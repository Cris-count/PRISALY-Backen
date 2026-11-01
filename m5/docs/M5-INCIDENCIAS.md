# M5 | Registro de incidencias

Estado al 2026-11-01 (cierre técnico, rama `feature/m5-cierre-tecnico`). Responsable: Integrante 4. Para incorporar al registro maestro de QA.

## Evidencia y estado

**Tipos de evidencia:**

- **Ejecutada:** prueba corrida y observada.
- **Reportada:** resultado comunicado por quien la ejecutó, sin log en el repositorio.
- **Descrita:** solo hay una descripción.

**Estados:**

- **Corregida y verificada:** prueba de regresión ejecutada con Maven.
- **Corregida, pendiente de Maven:** prueba ejecutada fuera de Maven.
- **Abierta.**
- **Remitida:** corresponde a otro responsable.

## 1. Defectos de M5

| ID | Fecha | Severidad | Descripción | Reproducción | Causa raíz | Corrección | Regresión | Commit | Estado | Evidencia |
|---|---|---|---|---|---|---|---|---|---|---|
| M5-INC-01 | 2026-10-31 | Alta | Finalizar un intento recién creado violaba `ck_intento_tiempos` (`fin` unos 4 ms antes de `inicio`) | `mvnw -pl m5 -am -Pdocker-it verify` en Windows con Docker Desktop: falla `RepositorioIntentosJpaIT.rechazaRespuestaEnIntentoFinalizado` | **Confirmada:** `fin` usaba el reloj de la JVM e `inicio` el de PostgreSQL (`now()`) | `fin` y `fechaActualizacion` con `clock_timestamp()` de PostgreSQL | `marcasTemporalesUsanElRelojDeLaBase` | `747a2a4` | Corregida y verificada | Reportada: fallo y BUILD SUCCESS posterior en Windows (20 IT). Ejecutada: reproducción SQL del rechazo en PostgreSQL 16.15. Detalle en `M5-RELOJ-PERSISTENCIA.md` |
| M5-INC-02 | 2026-11-01 | Alta (latente) | Tres beans implementaban `OperacionesParticipacion`: inyectar la interfaz o la fachada podía saltarse identidad y titularidad | En `1da1c8a`, `ConfiguracionParticipacion` registra `ParticipacionFacade` y `FachadaParticipacionTransaccional`, y `ConfiguracionAccesoEstudiante` registra el proxy: tres beans del mismo tipo | **Confirmada** por lectura de código: la fachada y su decorador se registraban como beans | Un único bean, el proxy; los repositorios Spring Data pasan a ser de paquete | `ConfiguracionParticipacionTest` (8), `ArquitecturaAccesoProtegidoTest` (5), comprobada con clases sonda | `d4a6b11` | Corregida, pendiente de Maven | Ejecutada fuera de Maven: la corrección (un solo bean) y las sondas que violan cada regla. No había controladores, así que no era explotable aún |
| M5-INC-03 | 2026-11-01 | Media | `AuthenticatedStudent(null, studentId)` de M1 se aceptaba como identidad | `new IdentidadEstudianteDesdeM1(() -> new AuthenticatedStudent(null, id)).actual()` devolvía identidad | **Confirmada:** solo se validaba `studentId` | También se exige `userId`, como M4 | 6c, 6d y 6e en `IdentidadEstudianteDesdeM1Test` | `2bfdc09` | Corregida, pendiente de Maven | Ejecutada: 6c falla con el código anterior y pasa con la corrección |
| M5-INC-04 | 2026-11-01 | Baja (latente) | Cuerpo JSON mal formado o tipo de parámetro incorrecto respondía con el formato por defecto de Spring Boot, no con `{code, message}` | `ManejadorErroresM5` sin manejadores para esas excepciones | **Confirmada** por lectura de código | 400 `VALIDATION_ERROR` con mensaje genérico | `cuerpoMalFormado`, `tipoIncorrecto` | `218114b` | Corregida, pendiente de Maven | Ejecutada fuera de Maven |
| M5-INC-05 | 2026-10-31 | Alta (integración) | El PR de M5 tenía conflictos add/add con develop | Merge de `f4c981c` con develop `2c96c91`: 9 archivos en conflicto | **Confirmada:** `413c8bf` (M4) copió M5 `3fc823e` dentro de develop, sin historia común | Merge en tres vías con base `3fc823e` | Pruebas existentes de M5 | `3f5844d` | Corregida y verificada | Reportada: BUILD SUCCESS en Windows sobre `747a2a4` |
| M5-INC-06 | 2026-10-31 | Media | `ConsultaProgramacionNoDisponibleException` de M4 no se traducía (500 genérico en vez de 503) | `M4M5ConsoleProbe` del Integrante 3 | **Confirmada** | Hecha por el Integrante 3 en develop (`413c8bf`) y conservada en la conciliación | `ProgramacionM4ErroresHttpTest`, `TraductorErroresM5Test` | `413c8bf` / `3f5844d` | Corregida y verificada | Reportada por M4; incluida en el BUILD SUCCESS de Windows |
| M5-INC-07 | 2026-10-31 | Baja (documental) | El mensaje del commit `902582f` dice 16 pruebas de validación; son 13 | Contar los `@Test` y `@ParameterizedTest` de `SolicitudRespuestaProvisionalValidacionTest` | Error al redactar el mensaje | No se reescribe la historia publicada; queda registrado aquí | — | `902582f` | Abierta (solo registro) | Descrita |
| M5-INC-08 | 2026-11-01 | Media (diseño) | `HistorialParticipacionPort` se publicó en `m5` para que lo consumiera M4, pero M4 no puede depender de `m5` sin ciclo Maven; mientras tanto, M4 respondía 503 al editar sesiones | Editar una sesión con el respaldo de M4 (`M4ProviderAutoConfiguration`) | **Confirmada** por revisión de dependencias | M5 implementa el puerto de M4 en `ParticipacionesM4DesdeM5` (`m5 → m4`, como M7 con `NotificacionPort`) | `ParticipacionesM4DesdeM5Test` (6), `ParticipacionesM4DesdeM5IT` (7) | rama `feature/m5-cierre-tecnico` | Corregida, pendiente de Maven | Ejecutada fuera de Maven (unitarias) |
| M5-INC-09 | 2026-11-01 | Media | El orden de las respuestas difería entre el agregado recién modificado (orden de registro) y el recargado de la base (`ORDER BY "idPregunta"`) | Registrar las preguntas en orden inverso al de su uuid y comparar el resultado de registrar con el de consultar | **Confirmada:** `LinkedHashMap` en el dominio frente a la consulta ordenada del adaptador; BD-005.2 no guarda el orden de registro | `ResumenIntento` ordena siempre por pregunta, con la comparación canónica de uuid de PostgreSQL | `ResumenIntentoTest` (+3), `RepositorioIntentosJpaIT.ordenDeRespuestasConsistente` | rama `feature/m5-cierre-tecnico` | Corregida, pendiente de Maven | Ejecutada fuera de Maven (unitarias); orden de uuid comprobado en PostgreSQL 16.15 |
| M5-INC-10 | 2026-11-01 | Media (proceso) | El PR #9 se fusionó en develop (`8b6dc33`) con `50fe1f1`, la historia anterior a la normalización, mientras la rama publicada está en `a12f69d` (historia normalizada). develop conserva los 56 trailers `Claude-Session` y el parche con la línea de sesión; la rama y develop divergen | `git log --grep=session_01 origin/develop` devuelve 56; `a12f69d` no es antecesor de develop | **Confirmada** | No se modifica develop (fuera de alcance). El cierre parte de develop en la rama nueva `feature/m5-cierre-tecnico`. Limpiar develop exige reescribirlo con autorización del equipo | — | — | Abierta: decisión del equipo | Ejecutada (`git log`, `git merge-base`) |

## 2. Observaciones externas (remitidas)

| ID | Descripción | Responsable | Evidencia |
|---|---|---|---|
| EXT-M4-01 | Las 17 pruebas de contratos de M4 se omitían; según el equipo, pasan al indicar la ubicación de las migraciones | Integrante 3 (M4) | Reportada; no la reprodujo M5. Es un ajuste de configuración de la ejecución, no un fallo corregido por M5 |
| EXT-M4-02 | Sin `prisaly.m4.consulta-bd0052.enabled=true`, la titularidad de M5 responde 503 (respaldo de M4) | Integrante 3 / Integración | Lectura de `M4ProgramacionConsultaAutoConfiguration` |
| EXT-M4-03 | Resuelta por M5-INC-08: `ParticipacionesPort` ya tiene proveedor de M5. Falta la revisión del Integrante 3 | Integrante 3 (con el contrato de M5 de la sección 2 de `M5-DOCUMENTO-TECNICO.md`) | Lectura de `M4ProviderAutoConfiguration` |
| EXT-M1-01 | `IdentityDomainException` (M1) y `ConsultaProgramacionNoDisponibleException` (M4) no declaran `serialVersionUID` y generan avisos `-Xlint:serial` | Integrantes 1 y 3 | Ejecutada: `javac -Xlint:all` |

## 3. Advertencias del entorno

- **Cowork no ejecuta Maven ni Testcontainers:** Maven Central responde 403 y no hay Docker. Las pruebas de M5 aquí corren con `javac` y JUnit 5.10.2 contra jars sueltos. No sustituyen la ejecución en Windows.
- **Finales de línea:** la copia de trabajo de Windows usa CRLF por `autocrlf`. Comparar con `git diff --ignore-cr-at-eol`.
- **PostgreSQL aislado de Cowork:** se detuvo dos veces al borrar bases de prueba, por permisos del espacio de trabajo. No afecta a PRISALY.

## 4. Limitaciones conocidas (no son defectos)

- **Sin endpoints de estudiante:** faltan ARQ-008.3 y DD-02.
- **Iniciar, reanudar y repetir intentos:** sin implementar (M4 y DD-14).
- **Titularidad no es elegibilidad:** una convocatoria retirada sigue siendo PROPIO.
- **Mensaje del 404:** `IntentoNoEncontradoException` incluye la referencia enviada por el cliente.
- **UUID no canónicos:** `UUID.fromString` acepta formas como `1-1-1-1-1`. El DTO provisional exige la forma canónica y la titularidad se verifica igual.

## 5. Decisiones DD y de Arquitectura que condicionan M5

DD-02, DD-14, ARQ-008.3, decisión 4 (ajeno: 403 o 404), decisión 7 (identidad
no disponible: 401, 403 o 503), catálogo de `motivoFinalizacion`, cambio de
respuesta antes de finalizar, y efecto del retiro de una convocatoria. Detalle
en `M5-DOCUMENTO-TECNICO.md`, sección 9.

## 6. Pruebas omitidas o no ejecutables en Cowork

| Prueba | Motivo | Dónde se valida |
|---|---|---|
| Todas las `*IT` de M5 (30) | Testcontainers y Docker | Windows, `-Pdocker-it` |
| `ProgramacionM4ErroresHttpTest` | Falta hamcrest en Cowork | Windows |
| `SolicitudRespuestaProvisionalValidacionTest` | Falta un proveedor de Bean Validation en Cowork | Windows |
