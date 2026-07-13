\### Archivo 4: `android\_testing\_best\_practices.md`

\*\*Propósito:\*\* Define los estándares de calidad, testing, seguridad y rendimiento que debe cumplir el código entregado.



```markdown

\# Skill: Testing, Seguridad y Rendimiento Android



\## Descripción

Esta skill asegura que el código no solo funcione, sino que sea robusto, seguro, eficiente y esté respaldado por pruebas automatizadas adecuadas.



\## Cuándo usar

\- Al finalizar la implementación de una Feature.

\- Durante el Code Review.

\- Al configurar pipelines de CI/CD.

\- Al detectar fugas de memoria o lentitud en la app.



\## Instrucciones y Reglas Estrictas

1\. \*\*Estrategia de Testing\*\*:

&#x20;  - \*\*Unit Tests (JUnit 5 + MockK)\*\*: Obligatorios para ViewModels y Casos de Uso. Deben mockear los Repositorios.

&#x20;  - \*\*UI Tests (Compose Testing)\*\*: Escribe tests básicos de UI para verificar que los estados principales (Loading, Error, Success) se renderizan correctamente.

2\. \*\*Seguridad\*\*:

&#x20;  - NUNCA hardcodees API Keys, secrets o contraseñas. Usar `BuildConfig` para variables de entorno o archivos `.properties` ignorados en Git.

&#x20;  - Para almacenar tokens sensibles localmente, usar `EncryptedSharedPreferences` de Jetpack Security.

&#x20;  - Aplicar ofuscación con R8/ProGuard en las builds de release.

3\. \*\*Rendimiento\*\*:

&#x20;  - Evitar asignaciones de objetos pesados dentro de los bloques de Compose (ej. no instanciar un `Paint` nuevo en cada recomposición).

&#x20;  - Usar `Modifier` encadenados adecuadamente (ej. no usar `Modifier.fillMaxSize()` y luego `Modifier.width(100.dp)` que causa conflictos de restricciones).

4\. \*\*Manejo de Ciclo de Vida\*\*:

&#x20;  - Suscripciones a Flows en ViewModels deben usar `viewModelScope.launch`.

&#x20;  - Evitar referencias contextuales a `Context` o `View` dentro de ViewModels o Casos de Uso para prevenir fugas de memoria.



\## Ejemplo de Unit Test para ViewModel (Kotlin)

```kotlin

@OptIn(ExperimentalCoroutinesApi::class)

class UserViewModelTest {



&#x20;   @get:Rule

&#x20;   val mainDispatcherRule = MainDispatcherRule()



&#x20;   private val getUserUseCase = mockk<GetUserUseCase>()

&#x20;   private lateinit var viewModel: UserViewModel



&#x20;   @Before

&#x20;   fun setup() {

&#x20;       viewModel = UserViewModel(getUserUseCase)

&#x20;   }



&#x20;   @Test

&#x20;   fun `loadUser success updates state to Success`() = runTest {

&#x20;       // Given

&#x20;       val fakeUser = User(id = "1", name = "Test")

&#x20;       coEvery { getUserUseCase("1") } returns flow { emit(fakeUser) }



&#x20;       // When

&#x20;       viewModel.loadUser("1")



&#x20;       // Then

&#x20;       assertEquals(UserUiState.Success(fakeUser), viewModel.uiState.value)

&#x20;   }

}

