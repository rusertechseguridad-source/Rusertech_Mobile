Propósito: Define las reglas de arquitectura, patrones de diseño y uso de Kotlin para el proyecto.



Skill: Arquitectura Android Moderna (Kotlin)

Descripción

Esta skill asegura que todo el código generado o refactorizado siga los estándares más altos de arquitectura Android moderna, enfocándose en la escalabilidad, testeabilidad y mantenimiento a largo plazo.



Cuándo usar

Al crear nuevas pantallas (Features).

Al refactorizar código legacy.

Al diseñar la estructura de capas para una nueva funcionalidad.

Al integrar nuevas fuentes de datos.

Instrucciones y Reglas Estrictas

Patrón de Arquitectura: Usa estrictamente MVVM (Model-View-ViewModel) o MVI (Model-View-Intent) para la capa de presentación.

Clean Architecture: Separa el código en tres capas claras:

Presentation (UI, ViewModels, Estados)

Domain (Casos de Uso/Interactors, Entidades de Dominio, Repositorios Interfaces)

Data (Implementaciones de Repositorios, APIs, Fuentes de Datos Locales, Modelos de Datos)

Gestión de Estado:

El estado de la UI debe ser inmutable (usa data class).

Usa StateFlow o SharedFlow en los ViewModels para exponer el estado. NUNCA uses LiveData para proyectos nuevos.

Asincronía:

Usa exclusivamente Coroutines (suspend functions) y Flows para operaciones asíncronas. No uses RxJava a menos que sea estrictamente necesario por código legacy.

Inyección de Dependencias:

Usa Hilt para la inyección de dependencias. Anota ViewModels con @HiltViewModel y usa @Inject para las dependencias.

Ejemplo de Estructura de ViewModel (Kotlin)

@HiltViewModelclass UserViewModel @Inject constructor(    private val getUserUseCase: GetUserUseCase) : ViewModel() {    private val \_uiState = MutableStateFlow<UserUiState>(UserUiState.Loading)    val uiState: StateFlow<UserUiState> = \_uiState.asStateFlow()    fun loadUser(userId: String) {        viewModelScope.launch {            \_uiState.value = UserUiState.Loading            getUserUseCase(userId)                .catch { e -> \_uiState.value = UserUiState.Error(e.message ?: "Error desconocido") }                .collect { user -> \_uiState.value = UserUiState.Success(user) }        }    }}

