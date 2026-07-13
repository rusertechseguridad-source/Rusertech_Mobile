\### Archivo 2: `android\_ui\_compose.md`

\*\*Propósito:\*\* Define las reglas para la creación de interfaces de usuario utilizando Jetpack Compose y Material 3.



```markdown

\# Skill: Frontend Android con Jetpack Compose



\## Descripción

Esta skill guía la creación de interfaces de usuario nativas, declarativas y reactivas utilizando Jetpack Compose, asegurando un diseño consistente, accesible y de alto rendimiento.



\## Cuándo usar

\- Al crear cualquier componente de UI (Botones, Tarjetas, Listas).

\- Al diseñar pantallas completas.

\- Al manejar eventos de clic o entrada del usuario.

\- Al implementar temas o estilos.



\## Instrucciones y Reglas Estrictas

1\. \*\*Paradigma Declarativo\*\*: Construye la UI como una función de los estados. Nunca manipules la UI directamente (cero `findViewById` o manipulación imperativa de vistas).

2\. \*\*State Hoisting (Elevación de Estado)\*\*: Los componentes (Composables) sin estado (Stateless) deben recibir el estado y los callbacks como parámetros. Mantén el estado lo más alto en el árbol como sea lógico.

3\. \*\*Material Design 3\*\*: Usa los componentes de `androidx.compose.material3`. Aplica los temas usando `MaterialTheme`.

4\. \*\*Rendimiento\*\*:

&#x20;  - Usa `remember` para calcular valores derivados o mantener objetos que no deben ser reinstanciados en cada recomposición.

&#x20;  - Usa `key{}` en listas `LazyColumn`/`LazyRow` para ayudar a Compose a identificar elementos únicos y optimizar la recomposición.

&#x20;  - Evita pasar lambdas que capturan objetos grandes directamente a los composables; usa referencias estables.

5\. \*\*Navegación\*\*: Usa `NavHost` y `NavController` de `androidx.navigation.compose` para gestionar el grafo de navegación.



\## Ejemplo de Componente Declarativo (Kotlin)

```kotlin

@Composable

fun UserProfileScreen(

&#x20;   uiState: UserUiState,

&#x20;   onRetryClick: () -> Unit

) {

&#x20;   when (uiState) {

&#x20;       is UserUiState.Loading -> CircularProgressIndicator(modifier = Modifier.fillMaxSize())

&#x20;       is UserUiState.Success -> UserContent(user = uiState.data)

&#x20;       is UserUiState.Error -> ErrorScreen(message = uiState.message, onRetry = onRetryClick)

&#x20;   }

}



@Composable

private fun UserContent(user: User) {

&#x20;   Column(modifier = Modifier.padding(16.dp)) {

&#x20;       Text(text = user.name, style = MaterialTheme.typography.headlineMedium)

&#x20;       Text(text = user.email, style = MaterialTheme.typography.bodyLarge)

&#x20;   }

}

