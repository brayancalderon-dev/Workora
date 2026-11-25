package com.workora.app.core.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileHelper {
    fun copyUriToInternalStorage(context: Context, uri: Uri, destinationFolderName: String = "cv_documents"): String? {
        val fileName = File(getFileName(context, uri) ?: "document_${System.currentTimeMillis()}.pdf")
            .name.ifBlank { "document_${System.currentTimeMillis()}.pdf" }
        
        val folder = File(context.filesDir, destinationFolderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val destinationFile = File(folder, fileName)
        
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
            }
            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** OCRs a locally stored PDF CV using the ML Kit dependency already in the app. */
    fun extractPdfText(context: Context, fileName: String, folderName: String = "cv_documents"): String {
        val file = File(File(context.filesDir, folderName), fileName)
        if (!file.isFile) return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    (0 until minOf(renderer.pageCount, 20)).joinToString("\n") { index ->
                        renderer.openPage(index).use { page ->
                            val scale = minOf(2f, 1800f / maxOf(page.width, page.height))
                            val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
                            } finally { bitmap.recycle() }
                        }
                    }
                }
            }
        } catch (_: Exception) { "" }
        finally { recognizer.close() }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result
    }
}
