# DD-02 — Alternativas A y B (DC-M1-POSTCIERRE-01 / 02)

**Estado:** DC-M1-POSTCIERRE-02 selecciona la **alternativa A** como diseño técnico propuesto; la
ratificación productiva sigue pendiente. Acceso estudiantil productivo deshabilitado
(`prisaly.m1.student-access.enabled=false`, `DenyingStudentAccessService`).

## 1. Qué hay hoy en M1

| Pieza | Alternativa | Persistencia | Estado |
|---|---|---|---|
| `ActivacionCuentaEstudianteService` + `AutenticacionEstudianteService` | A | V011 (`ActivacionCuentaEstudiante`, `SesionEstudiante`) | Implementado tras el flag; `emitir()` sin llamador productivo |
| `SessionStudentAccessService` (`StudentAccessPort`) | A y B | `SesionEstudiante` | Implementado tras el flag |
| `StudentAccessDeliveryPort` → M7 `InMemoryStudentAccessDeliveryAdapter` | A y B | Memoria (sin Outbox cifrada) | Preparación; sin emisor conectado |
| `/v1/acceso-estudiante/validar` | — | Ninguna | Siempre 501 |

El reto en memoria de la preparación B (`StudentAccessValidationService`, `StudentAccessCodeIssuanceService`,
`StudentAccessIssuanceConfig`: código en claro, sin vigencia ni límite de intentos) se **eliminó** en
DC-M1-POSTCIERRE-02. Solo queda el reto persistente.

## 2. Diferencias con el diseño ratificado (plan v1.1)

| Aspecto | A — cuenta + activación | B — código por acceso (ratificado v1.1) |
|---|---|---|
| Credencial habitual | Contraseña propia (BCrypt en `Usuario.passwordHash`) | Ninguna; OTP de 8 dígitos por acceso |
| Código | Una vez, para activar (vigencia 15 min) | En cada acceso (vigencia **5 min**, reenvío **60 s**) |
| Cambia `Usuario` | Sí: `estado = 'Activo'` y `passwordHash` | No |
| Fuerza bruta | 5 intentos por código; **sin límite** en el login por contraseña | 5 intentos por código |
| Entrega | Una por cuenta | Una por acceso: Outbox cifrada AES-256-GCM en la misma TX (DD02-12a/12b) |
| Riesgo propio | `Usuario.estado` no distingue «pendiente» de «baja». Mitigado: solo se activa una cuenta sin `passwordHash` (una baja posterior a la activación no se reactiva). Sigue sin distinguirse una baja **anterior** a la activación | Volumen de correo; dependencia de SMTP para cada acceso |

## 3. Trabajo común ya completado (este PR)

- **Integridad del vínculo** (`db/dd02-propuesta/V012__dd02_integridad_vinculo.sql`, candidata):
  correspondencia `idUsuario`↔`idEstudiante` en activaciones y sesiones, sesión solo para cuenta activa,
  vínculo de `Estudiante` inmutable mientras existan filas DD-02, un único código abierto por usuario,
  consumo/revocación excluyentes y posteriores a la creación, columna `fechaUltimaActividad`.
- **Inactividad de 30 min** (parámetro ratificado, común): `PoliticaSesionEstudiante` y consulta
  atómica de sesión que valida y registra actividad (`CuentaEstudianteJdbcAdapter.sesionVigente`).
- Pruebas PostgreSQL 16.15 de lo anterior en `CuentaEstudiantePostgreSqlIT`.

## 4. Contrato técnico según la alternativa aprobada

Se conservan sin cambio de firma `StudentAccessPort`, `AuthenticatedStudent`, `StudentAccessDeliveryPort`,
`SesionEstudiante` y `SessionStudentAccessService`.

**Si se aprueba A:** ya aplicada la regla `passwordHash IS NULL` para activar (DC-M1-POSTCIERRE-02);
falta decidir si una baja anterior a la activación debe impedir activar (requiere estado o marca de
baja administrativa); añadir límite de intentos al login por contraseña (requiere esquema: contador o
tabla de intentos); definir quién solicita la emisión y conectar `emitir()` con
`StudentAccessDeliveryPort` en una sola transacción.

**Si se aprueba B:** reutilizar el reto persistente de A como `CredencialAccesoEstudiante`
(mismas columnas: HMAC, `challengeId`, vigencia, intentos, consumo, revocación); cambiar la vigencia a
5 min y añadir el reenvío mínimo de 60 s; el consumo abre sesión en lugar de fijar contraseña; retirar
`ActivacionCuentaEstudianteService`, `AutenticacionEstudianteService` y `/activar` + `/autenticar`.

En ambos casos: Outbox M7 cifrada en la misma TX (`Propagation.MANDATORY`), V011+V012 aprobadas con
número definitivo, y la guarda `M4SchemaAutoConfiguration` aceptando solo las tablas DD-02 autorizadas.

## 5. Pruebas exigibles a la alternativa aprobada

| Escenario | A | B |
|---|---|---|
| Código válido, vencido, reutilizado, agotado (5) | Ya cubierto | Repetir con vigencia 5 min |
| Reenvío antes de 60 s | — | Nuevo |
| Consumo concurrente: un ganador | Ya cubierto | Repetir sobre el reto de acceso |
| Login por contraseña: bloqueo tras N fallos | Nuevo | — |
| Baja administrativa no reactivable | Cubierto para bajas posteriores a la activación; pendiente de decisión para bajas previas | No aplica |
| Fallo al encolar Outbox → sin credencial usable (12a) | Nuevo | Nuevo |
| Fallo SMTP posterior → sin acceso sin código (12b) | Nuevo | Nuevo |
| Inactividad 30 min, vigencia 8 h, revocación | Ya cubierto (común) | Ya cubierto (común) |
| Vínculo cruzado, cuenta inactiva, emisión concurrente | Ya cubierto con V012 | Ya cubierto con V012 |
| Cookie estudiantil en rutas docentes / Bearer docente en M5 | Ya cubierto | Ya cubierto |
| Contexto APP completo con flag activado | Bloqueado por la guarda M4 | Bloqueado por la guarda M4 |
