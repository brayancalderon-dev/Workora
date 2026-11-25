package com.workora.app.core.utils

import android.content.Context
import android.content.Intent

object TermuxHelper {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val BOT_COMMAND = "python bot_monitor.py"

    fun startTelegramBot(context: Context) {
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = RUN_COMMAND_ACTION
            // Usamos directamente el ejecutable de Python de Termux
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/python")
            // Pasamos el script como argumento
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("bot_monitor.py"))
            // Directorio donde reside el script
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            // CRÍTICO: background = false para que abra la ventana de la terminal y veamos el output
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        
        try {
            android.util.Log.d("TermuxHelper", "Intentando iniciar el servicio de Termux...")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.util.Log.d("TermuxHelper", "Usando startForegroundService (Android O+)")
                val component = context.startForegroundService(intent)
                if (component == null) {
                   android.util.Log.e("TermuxHelper", "startForegroundService devolvió null. El servicio no existe o no se puede acceder.")
                   android.widget.Toast.makeText(context, "Error: No se pudo acceder al servicio de Termux.", android.widget.Toast.LENGTH_LONG).show()
                   return
                }
            } else {
                android.util.Log.d("TermuxHelper", "Usando startService (pre-Android O)")
                val component = context.startService(intent)
                if (component == null) {
                   android.util.Log.e("TermuxHelper", "startService devolvió null. El servicio no existe o no se puede acceder.")
                   android.widget.Toast.makeText(context, "Error: No se pudo acceder al servicio de Termux.", android.widget.Toast.LENGTH_LONG).show()
                   return
                }
            }
            android.util.Log.d("TermuxHelper", "Comando enviado correctamente a Termux.")
            android.widget.Toast.makeText(context, "Comando enviado a Termux", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            android.util.Log.e("TermuxHelper", "SecurityException: Permiso denegado.", e)
            android.widget.Toast.makeText(context, "Error de Permisos. ¿Otorgaste 'com.termux.permission.RUN_COMMAND' y configuraste allow-external-apps?", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: IllegalStateException) {
            android.util.Log.e("TermuxHelper", "IllegalStateException: Fallo al iniciar el servicio (posibles restricciones de fondo).", e)
            android.widget.Toast.makeText(context, "Error: Termux no permite inicio en segundo plano en este momento.", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("TermuxHelper", "Excepción inesperada.", e)
            android.widget.Toast.makeText(context, "Error inesperado: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}