# DC-INTEGRACION-TOTAL-01 — registro único

Línea base: `63eceb448e6c37596ce8115080f1cdda174538a9`, `origin/develop` actualizado el 8 de noviembre de 2026. Rama `integration/cierre-backend-v27`, worktree limpio separado de la auditoría. Java 21.0.10, Maven 3.9.11. Docker CLI 29.8.2 disponible; Docker Engine local no responde. PostgreSQL se verificará en runner efímero 16.15.

## Ejecución priorizada

1. A01: limpieza de tokens abandonados y capacidad acotada sin expulsar sesiones vigentes. Pruebas de expiración, revocación y concurrencia.
2. A02/A03: orden de filtros, acceso estudiantil mediante el puerto existente y configuración explícita del despliegue/cookies. DD-02 permanece deshabilitada por defecto.
3. A04: preservar distinción de errores de configuración y recuperar entrega SMTP con PostgreSQL/GreenMail, conservando leases y conteo de reclamos.
4. Completar recorridos con contratos verificables; preservar denegación cuando falten decisiones.
5. Regresión, HTTP/Postman, trazabilidad y PR sin fusión.

No se atribuyen las 1.195 pruebas históricas a los commits de este incremento. V001–V009, D-06 y las propuestas Flyway no se alteran preventivamente.

## Fuentes y dependencias externas

El catálogo canónico V27, los 21 CU completos, ARQ-008.2/008.3 y el acta final DD-02 todavía deben localizarse. No se reconstruirán identificadores desde el código ni se declarará un porcentaje nuevo sin metodología y fuente verificables.

Actualización de fuentes: se recuperaron los 21 CU históricos y sus relaciones RF–CU de `PRISALY_Ing de Sofyware II.pdf`, páginas 6 y 8, con síntesis 12–14. La matriz y el plan adjuntos ya usan esos identificadores; no se equiparan con ARQ-CU por suposición. Las fichas exhaustivas y las decisiones vigentes siguen condicionando los recorridos completos.

Actualización: localizados en Descargas `B_MATRIZ_V27_PRISALY.xlsx` y `PRISALY_Catalogo_V27_Actualizado_ARQ2` (archivo XLSX sin extensión). La matriz corresponde a la auditoría de `bc8e378` y usa un extracto derivado V27; el catálogo explicita la aprobación provisional DD-10 y criterios propuestos. Son fuentes de trazabilidad provisional, no ratificación automática de políticas. Dirección Central confirmó en este incremento que DD-02 sigue pendiente entre contraseña con activación y OTP por acceso. CU históricos: CU-01..CU-22 con CU-04 integrado en CU-11; no equivalerlos sin fuente a ARQ-CU-01..07.

A02/A03: se registra primero Bearer y luego el filtro estudiantil; se reutiliza StudentAccessPort para las rutas estudiantiles sin crear principal institucional. El origen confiable se exige para operaciones inseguras con cookies; CORS con credenciales se limita a rutas estudiantiles y orígenes HTTP explícitos, únicamente detrás del flag. Frontend `origin/feature/simulador-2d` documenta desarrollo en 4200 hacia 8080 y producción `/api` mediante proxy. SameSite Strict / HttpOnly se conservan; HTTPS debe llegar a APP o a un proxy con forwarding confiable para Secure. Tres pruebas MVC/configuración aprobadas; no son persistencia real. Nueva suite StudentSessionControlledHttpIT prepara HTTP real contra PostgreSQL candidato con flag habilitado solo en el fixture.

A04: marca persistida CONFIGURACION en ultimoError distingue la causa. Los reclamos siguen contándose y los leases no se sustituyen. Un error de configuración confirmado conserva Pendiente incluso tras el presupuesto ordinario; el siguiente reclamo puede probar el proveedor reparado. Al reclamar se limpia la marca anterior para que un proceso abandonado no conserve esa excepción indefinidamente. Fallo permanente/transitorio agotado y lease abandonado mantienen cierre; resultados con lease viejo siguen rechazados. Backoff saturado evita desbordamiento en reintentos de configuración prolongados. No se habilita el worker ni se modifica el esquema. La clasificación no reconstruye causas de errores históricos sin marca.

Frontend local identificado en repositorio separado, SHA `8f7e0c297e196b0787b19adcfa43c8525ca1c557`, Angular 22.2.0; sin cambios locales reportados. La inspección de SIM-01 y del despliegue continuará antes de modificarlo.

SIM-01 recuperado sin cambios productivos desde `origin/feature/simulador-2d`, SHA `0331392372cee28be226ffa131e66c89f8716099`, rama separada `integration/sim01-existente`. Build con Node 24.15.0, 70 pruebas ChromeHeadless y comprobación de URLs aprobados. Escena y avance de preguntas verificados en navegador; aviso de datos simulados visible. No es persistencia M5 ni calificación real.

## Trazabilidad inicial

| Cambio | Módulo | Clase | Endpoint afectado | Prueba | RF/CU |
|---|---|---|---|---|---|
| A01 | M1 | ProvisionalAccessTokenStore / IdentityExceptionHandler | POST /v1/personal/autenticar | ProvisionalAccessTokenStoreTest | Fuente canónica pendiente; corrección técnica de recursos |
| A02/A03 | APP/M1 | HttpApiConfig | Rutas estudiantiles existentes | Pendiente de implementación | DD-02 sin habilitación productiva |
| A04 | M7 | ReclamoEnvioJpaAdapter / ProcesarEnviosPendientesService | Worker interno | M7EnvioWorkerPostgreSqlIT | D1–D11 M7; no confundir con D07 estudiantil |

A01: seis pruebas de ProvisionalAccessTokenStoreTest aprobadas, cero fallos/errores/omisiones. La capacidad predeterminada es 10.000 registros; al agotarse responde 503 AUTH_CAPACITY_EXCEEDED sin revocar tokens vigentes. Limpieza al emitir/consultar y mantenimiento explícito purgeExpired. Sin cambio académico.
