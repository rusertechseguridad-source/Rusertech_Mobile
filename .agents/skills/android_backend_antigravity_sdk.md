\### Archivo 3: `android\_backend\_antigravity\_sdk.md`

\*\*Propósito:\*\* Establece cómo el frontend debe comunicarse con el backend tradicional y, crucialmente, cómo integrarse con el ecosistema de Google Antigravity mediante su SDK.



```markdown

\# Skill: Integración Backend y Antigravity SDK



\## Descripción

Define las mejores prácticas para la conectividad de red, almacenamiento local y la orquestación de tareas asíncronas utilizando el SDK de Google Antigravity para dotar a la app de capacidades agent-first.



\## Cuándo usar

\- Al crear un repositorio de datos (Repository).

\- Al implementar llamadas a APIs REST o GraphQL.

\- Al configurar la base de datos local con Room.

\- Al requerir delegar una tarea compleja a un Agente de Antigravity (ej. análisis de texto, generación de resúmenes).



\## Instrucciones y Reglas Estrictas

1\. \*\*Patrón Repository\*\*: Crea una interfaz en el dominio e impleméntala en la capa de datos. El Repository es el ÚNICO punto de acceso para que el ViewModel obtenga datos, decidiendo si vienen de la red (API) o del caché local (Room).

2\. \*\*Red (Retrofit)\*\*:

&#x20;  - Define las APIs usando interfaces de Retrofit.

&#x20;  - Usa interceptores de OkHttp para inyectar tokens de autenticación (OAuth2/JWT) de forma global.

&#x20;  - Maneja errores de red mapeándolos a clases de error de dominio (ej. `DomainError.NetworkError`).

3\. \*\*Base de Datos Local (Room)\*\*:

&#x20;  - Usa Room para cachear respuestas de API y permitir experiencia offline-first si es requerido.

&#x20;  - Define Entidades, DAOs (Data Access Objects) y la clase Database.

4\. \*\*Integración con Antigravity SDK (Crítico)\*\*:

&#x20;  - Para tareas que requieran IA (ej. "resumir notas del usuario"), NO llames a la API de Gemini directamente. Usa el \*\*Antigravity SDK\*\*.

&#x20;  - Envuelve las llamadas al SDK en funciones `suspend` dentro de un Repository dedicado (ej. `AgentRepository`).

&#x20;  - Configura timeouts adecuados, ya que las respuestas de los agentes pueden tardar más que una API REST normal.



\## Ejemplo Conceptual de Repository con SDK (Kotlin)

```kotlin

class NotesRepositoryImpl @Inject constructor(

&#x20;   private val notesApi: NotesApi,

&#x20;   private val notesDao: NotesDao,

&#x20;   private val antigravityClient: AntigravityClient // Inyección simulada del SDK

) : NotesRepository {



&#x20;   override suspend fun getNotesWithSummary(): List<Note> {

&#x20;       // 1. Obtener notas de la base de datos local o red

&#x20;       val notes = notesDao.getAllNotes().map { it.toDomain() }

&#x20;       

&#x20;       // 2. Delegar al Agente la tarea de generar resúmenes

&#x20;       return notes.map { note ->

&#x20;           try {

&#x20;               val prompt = "Genera un resumen de 1 línea para: ${note.content}"

&#x20;               // Llamada suspendida al agente a través del SDK

&#x20;               val agentResponse = antigravityClient.submitAndAwaitResponse(prompt)

&#x20;               note.copy(summary = agentResponse.text)

&#x20;           } catch (e: Exception) {

&#x20;               // Fallback graceful si el agente falla

&#x20;               note.copy(summary = "No se pudo generar el resumen.")

&#x20;           }

&#x20;       }

&#x20;   }

}

