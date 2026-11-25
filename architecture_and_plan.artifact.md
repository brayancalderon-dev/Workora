# Propuesta de Arquitectura, Diseño y Planificación — Workora

## 1. Revisión de Requerimientos y Limitaciones Técnicas

### Limitaciones Técnicas y Dependencias Externas
1. **Hiring Room:**
   - *Limitación:* Hiring Room generalmente no expone una API pública abierta para consumo de terceros sin un acuerdo comercial. Si se prohíbe el scraping no autorizado, la integración técnica directa de búsqueda automatizada será un desafío.
   - *Solución propuesta:* Permitir a los usuarios compartir URLs de vacantes de Hiring Room hacia Workora (vía *Intent Action Send*), donde Workora descargue el contenido (si es accesible públicamente sin scraping intensivo) o proveer un WebView interno que extraiga el HTML de forma pasiva cuando el usuario navega por la oferta, lo cual es más respetuoso con las políticas de scraping.
2. **Control de Bot de Telegram en Termux:**
   - *Limitación:* Por seguridad de Android, una app no puede iniciar o matar procesos de otra app directamente de forma arbitraria.
   - *Solución propuesta:* Utilizar el permiso `com.termux.permission.RUN_COMMAND` introducido en versiones recientes de Termux para enviar comandos como un *Intent* al servicio `RunCommandService` de Termux. Esto requiere que el usuario instale Termux, configure `allow-external-apps=true` en sus propiedades, y otorgue el permiso a Workora.
3. **Integración con IA:**
   - *Dependencia:* Se requerirá una API Key (ej. Google Gemini u OpenAI). Para que la app no dependa de un backend propio ni incurra en costos globales, Workora debería permitir al usuario ingresar su propia API Key ("Bring Your Own Key") o utilizar un modelo on-device más adelante, aunque para el MVP, la clave provista por el usuario es lo ideal.
4. **Integración con Gmail (OAuth 2.0):**
   - *Dependencia:* Se debe registrar la app en Google Cloud Console para obtener un Client ID de OAuth 2.0 y usar la API de Gmail para crear borradores. Esto es perfectamente viable desde el cliente Android.
5. **Respaldos (SAF):**
   - Usaremos *Storage Access Framework (SAF)* para permitir al usuario guardar un archivo `.zip` (o encriptado) en su Google Drive o almacenamiento interno, y restaurarlo desde ahí.

---

## 2. Arquitectura y Estructura de Carpetas

La aplicación seguirá los principios de **Clean Architecture** combinada con **MVVM** (Model-View-ViewModel), altamente recomendada para Jetpack Compose.

```
com.workora.app
│
├── core/                   # Clases comunes, utilidades, temas (UI), constantes
│   ├── network/            # Manejadores de red, excepciones
│   ├── database/           # Configuración de Room (AppDatabase)
│   ├── theme/              # Color, Type, Shape (Material 3)
│   └── navigation/         # Rutas, NavGraph
│
├── di/                     # Inyección de Dependencias (Hilt recomendado)
│
├── data/                   # Capa de Datos
│   ├── local/              # Room DAOs, DataStore, Entidades (Entities)
│   ├── remote/             # Retrofit APIs, DTOs (Gmail, AI)
│   └── repository/         # Implementación de los repositorios
│
├── domain/                 # Capa de Dominio (Reglas de negocio)
│   ├── model/              # Modelos de dominio puros (Profile, JobVacancy)
│   ├── repository/         # Interfaces de repositorios
│   └── usecase/            # Casos de uso (AnalyzeCvUseCase, MatchJobUseCase)
│
└── presentation/           # Capa de UI (Jetpack Compose + ViewModels)
    ├── components/         # Composables reutilizables (Botones, Tarjetas, TopBar)
    ├── dashboard/          # Pantalla principal
    ├── profile/            # Visualización y edición de perfil
    ├── jobs/               # Buscador y detalle de vacantes
    ├── applications/       # Gestor de postulaciones (Kanban/Lista)
    ├── integrations/       # Gmail, Termux, AI config
    └── setup/              # Asistente inicial (Wizard)
```

---

## 3. Modelos de Datos (Entidades Principales)

* **Profile:** `id`, `name`, `summary`, `skills` (List), `experience` (List), `education` (List), `languages` (List), `cvVersion`.
* **JobVacancy:** `id`, `title`, `company`, `location`, `workMode` (Remote, Hybrid, On-site), `description`, `url`, `source` (Hiring Room, Manual), `createdAt`.
* **MatchEvaluation:** `jobId`, `matchScore` (0-100), `confidenceLevel`, `strengths`, `weaknesses`, `recommendations`, `evaluationDate`.
* **Application:** `jobId`, `status` (New, Analyzed, Recommended, DraftCreated, Applied, InProcess, Interview, Rejected, Hired), `notes`, `appliedDate`, `followUpDate`.

---

## 4. Diseño de Pantallas (UI / UX)

* **Tema Visual:**
  * **Primario:** Azul Marino (`#0A192F` o similar).
  * **Secundario/Acento:** Morado (`#6C63FF` o similar).
  * **Fondo:** Tonos claros (`#F3F4F6`) para modo claro, gris oscuro (`#111827`) para modo oscuro.
* **Componentes Clave:**
  * **Bottom Navigation:** [Inicio (Dashboard)] | [Vacantes] | [Postulaciones] | [Perfil]
  * **Navigation Drawer (Menú Lateral):** Accesible desde el icono de hamburguesa en el TopBar. Contendrá opciones avanzadas (Integraciones, Bot Termux, Respaldos, Configuración).
  * **Tarjetas:** Bordes redondeados (`RoundedCornerShape(16.dp)`), sombras suaves.

---

## 5. Plan de Implementación Incremental (Fases del MVP)

Se proponen las siguientes fases ordenadas lógicamente para construir el proyecto sin abrumar la base de código, garantizando que cada paso funcione antes del siguiente.

*   **Fase 1: Configuración Base y Arquitectura UI**
    *   Configuración de Gradle (KTS), Hilt, Room, Retrofit, DataStore.
    *   Implementación del Tema Material 3 (Colores, Tipografías).
    *   Configuración de la Navegación (BottomBar, Drawer) y pantallas vacías (esqueletos).
*   **Fase 2: Perfil Profesional y Análisis Inicial**
    *   Crear base de datos local para el Perfil.
    *   Configurar proveedor de IA (ej. Gemini/OpenAI mediante API Key configurable por el usuario).
    *   Desarrollar el importador de PDF y extracción de texto.
    *   Implementar el *Prompt* de IA para estructurar el CV y guardarlo en Room.
    *   Pantalla de visualización y edición del perfil.
*   **Fase 3: Gestión de Vacantes y Buscador**
    *   Modelos de datos para `JobVacancy`.
    *   Desarrollar mecanismo de captura de vacantes (Compartir URL desde navegador -> Workora) o WebView interno.
    *   Pantalla de listado y detalle de vacantes.
*   **Fase 4: Evaluador de Compatibilidad (IA)**
    *   Implementar *UseCase* que envíe el Perfil y la Vacante a la IA para evaluación.
    *   Almacenar y mostrar resultados (Porcentajes, Brechas, Fortalezas) en la UI.
*   **Fase 5: Gestor de Postulaciones (Kanban/Estado)**
    *   Sistema de estados de aplicaciones.
    *   Pantalla para mover vacantes entre estados (Ej. "Recomendada" -> "En Proceso").
*   **Fase 6: Integración con Gmail (Borradores)**
    *   Autenticación OAuth 2.0 en Android.
    *   Uso de IA para generar carta de presentación/correo.
    *   Creación del borrador mediante la API de Gmail (sin enviar automáticamente).
*   **Fase 7: Integración con Termux (Bot Telegram)**
    *   Implementar los Intents de `com.termux.permission.RUN_COMMAND`.
    *   Pantalla de control (Iniciar, Detener, Logs básicos si son accesibles).
*   **Fase 8: Dashboard, Ajustes y Respaldos**
    *   Consolidar métricas para la pantalla de inicio (Dashboard).
    *   Exportar e importar base de datos usando SAF.
    *   Revisiones finales.