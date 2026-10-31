M4 incorpora programación de sesiones, estados y convocatorias con persistencia PostgreSQL. Se conserva una sola V010 en el módulo m4, consumida por app mediante su dependencia; se refuerzan las pruebas de rollback y concurrencia.

PR EN BORRADOR: conciliación BD-005.2 pendiente. M1 y M3 usan stubs; M5 no crea participaciones ni intentos en esta suite y su consulta de participaciones vinculadas devuelve false. No realizar merge.

Validación: 66 pruebas locales (49 Surefire, incluyendo smoke Docker, y 17 Failsafe); resultados y limitaciones en m4/review/CONCILIACION.md y evidencias adjuntas en m4/review. Ninguna modificación a V001–V009.

Revisión requerida del Integrante 1 y responsables M3/M5: aportar BD-005.2 y migraciones compartidas, acordar adaptadores y transacciones, comprobar F3-03 y participaciones reales, y ejecutar en Docker oficial. Usuarios GitHub pendientes de identificar.
