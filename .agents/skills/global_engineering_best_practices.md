Skill: Principios Globales de Ingeniería de Software (Senior/Staff Level)

Descripción

Este documento actúa como la "constitución" para todo el código generado, revisado o refactorizado. Define las reglas universales de arquitectura, seguridad, calidad y colaboración que aplican sin importar el lenguaje (Kotlin, Python, JS), framework (Compose, React, Spring) o plataforma (Mobile, Web, Backend).



Cuándo usar

SIEMPRE. Estas reglas se aplican como capa base antes de aplicar cualquier skill específica de tecnología (ej. Android, React, Python).

Durante cualquier Code Review.

Al diseñar sistemas nuevos, APIs o bases de datos.

Al escribir o modificar cualquier línea de código de producción.

1\. Principios de Clean Code y Diseño

Legibilidad sobre Cleverness: El código se lee 10x más veces de lo que se escribe. Prioriza que cualquier desarrollador intermedio entienda la lógica a primera vista. No uses trucos de sintaxis oscuros.

Regla del Boy Scout: Deja el código un poco mejor de lo que lo encontraste. Si tocas una función, mejórale el nombre, elimina un if anidado innecesario o añade un tipo faltante.

SOLID y DRY:

Single Responsibility: Una clase/función debe tener solo una razón para cambiar.

Open/Closed: Abierto para extensión, cerrado para modificación (usa polimorfismo en lugar de encadenar if/else o switch largos basados en tipos).

DRY (Don't Repeat Yourself): Si copias y pegas más de 2 veces, es hora de abstraer.

Nombres Significativos:

PROHIBIDO: data, temp, info, manager, obj, var1.

USA: Sustantivos claros para variables/clases (userAccount, paymentProcessor), verbos para funciones (fetchUserData, calculateTotal).

Control de Complejidad:

Ninguna función debe tener más de 20-30 líneas. Si es más larga, abstrae partes en funciones privadas con nombres descriptivos.

Máximo 2 niveles de anidamiento (indentación). Usa early returns (guard clauses) para evitar el "triángulo de la muerte" de los if/else.

2\. Gestión de Errores y Resiliencia

Manejo Explícito de Errores: NUNCA uses un catch vacío o que solo imprima en consola (print(e)). Siempre propaga el error, lo envuelves en un error de dominio o lo manejas graciosamente mostrando un estado de UI adecuado (ej. "Error de conexión").

Fail Fast (Fallo Rápido): Valida las entradas (inputs) de funciones y APIs al inicio de la ejecución. Lanza excepciones o devuelve errores tan pronto como detectes que los datos son inválidos. No dejes que el error explote 10 líneas más abajo.

No uses Excepciones para Flujo de Control: Las excepciones son para casos excepcionales, no para validar lógica de negocio normal (ej. no usar try/catch para comprobar si una clave existe en un diccionario/mapa).

3\. Control de Versiones (Git) y Commits

Commits Atómicos: Cada commit debe representar UN solo cambio lógico conceptual. No mezcles "arreglar bug del login" con "refactorizar base de datos" en el mismo commit.

Conventional Commits: Usa el estándar para los mensajes de commit:

feat: (Nueva funcionalidad)

fix: (Corrección de bug)

refactor: (Cambio de código que no corrige bug ni añade funcionalidad)

chore: (Tareas de mantenimiento, deps)

docs: (Documentación)

Ejemplo: feat(auth): add biometric login flow

Seguridad en el Repo: NUNCA hagas commit de secretos, claves API, contraseñas o archivos .env. Usa siempre archivos .env.example con valores dummy.

4\. Seguridad por Defecto (Security First)

Zero Trust en Entradas: Toda entrada que venga de un usuario, de otra API o de un archivo externo es potencialmente maliciosa. Siempre sanitiza y valida antes de procesar.

Principio de Mínimo Privilegio: Solicita solo los permisos estrictamente necesarios (tanto en apps móviles como en servicios backend).

Manejo de Datos Sensibles:

Contraseñas: Siempre Hashear (usando bcrypt/argon2), NUNCA guardar en texto plano ni usar MD5/SHA (que son hashing, no encriptación, y son rápidos de romper).

Tokens/Keys en tránsito: SIEMPRE usar HTTPS/TLS.

5\. Filosofía de Testing (Calidad Asegurada)

El código no testeado es código roto: Si es lógica de negocio importante, debe tener una prueba automática (Unit Test).

Arrange-Act-Assert (AAA): Estructura todos los tests de esta manera para que sean legibles.

Primero la Claridad: Un test que falla con un mensaje incomprensible es casi inútil. Asegúrate de que los mensajes de aserción (assertEquals(expected, actual, "Should return X when Y")) expliquen el fallo.

Mocking con Cuidado: No mockees todo (no testees el framework). Mockea solo las fronteras (bases de datos, APIs externas, reloj del sistema). Si mockeas demasiado, tus tests no prueban nada real.

6\. El Paradigma "Agent-First" (Instrucciones para la IA)

Cuando actúes como agente generando código, debes seguir estas reglas adicionales:



No Confíes Ciegamente: Antes de escribir código que interactúe con APIs del sistema o librerías externas, consulta la documentación oficial si no estás 100% seguro de la firma del método o la versión.

Explica el "Por qué": Si decides usar un patrón de diseño complejo o ignoras una sugerencia del usuario, explica brevemente por qué esa decisión es mejor para la arquitectura a largo plazo.

Pensamiento en Cascada: Al generar una nueva característica, piensa en el impacto: ¿Necesito actualizar la base de datos? -> ¿Necesito migraciones? -> ¿Necesito actualizar el DTO de la API? -> ¿Necesito actualizar la UI? No generes solo la capa de UI y olvides el backend.

Autocrítica: Antes de mostrar el código final, revísalo mentalmente buscando: dependencias circulares, posibles fugas de memoria, problemas de concurrencia (race conditions) y violaciones de los principios SOLID mencionados arriba.

