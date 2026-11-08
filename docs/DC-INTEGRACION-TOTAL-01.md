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

Frontend local identificado en repositorio separado, SHA `8f7e0c297e196b0787b19adcfa43c8525ca1c557`, Angular 22.2.0; sin cambios locales reportados. La inspección de SIM-01 y del despliegue continuará antes de modificarlo.

## Trazabilidad inicial

| Cambio | Módulo | Clase | Endpoint afectado | Prueba | RF/CU |
|---|---|---|---|---|---|
| A01 | M1 | ProvisionalAccessTokenStore / IdentityExceptionHandler | POST /v1/personal/autenticar | ProvisionalAccessTokenStoreTest | Fuente canónica pendiente; corrección técnica de recursos |
| A02/A03 | APP/M1 | HttpApiConfig | Rutas estudiantiles existentes | Pendiente de implementación | DD-02 sin habilitación productiva |
| A04 | M7 | ReclamoEnvioJpaAdapter / ProcesarEnviosPendientesService | Worker interno | M7EnvioWorkerPostgreSqlIT | D1–D11 M7; no confundir con D07 estudiantil |

A01: seis pruebas de ProvisionalAccessTokenStoreTest aprobadas, cero fallos/errores/omisiones. La capacidad predeterminada es 10.000 registros; al agotarse responde 503 AUTH_CAPACITY_EXCEEDED sin revocar tokens vigentes. Limpieza al emitir/consultar y mantenimiento explícito purgeExpired. Sin cambio académico.
