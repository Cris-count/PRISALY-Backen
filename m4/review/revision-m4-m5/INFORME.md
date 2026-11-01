# Revisión conjunta M1–M7 y trabajo local M4–M5
Fecha: 31/10/2026. Base develop: feec0fb. M5 incorporado desde origin/feature/m5-participacion-intentos, commit3fc823e, solo al árbol de trabajo. Sin commit, push ni cambio de PR en esta fase.

## Resultado y alcance
La primera ejecución completa con M5 incorporado pasó431 pruebas. Tras la corrección503 se aprobaron436 pruebas. La reorganización de M4 añade9 casos: la última verificación pasó **445 pruebas, cero fallos, errores u omisiones**: M1=14, M3=127, M4=149, M5=145 y app=10. Se verificó por separado la coincidencia entre XML nuevos y resúmenes Maven. Ver [resultado definitivo](evidence/RESULTADO.md). Los resultados demuestran las operaciones cubiertas, no un recorrido académico completo.

| Módulo | Implementación disponible | Resultado de revisión |
|---|---|---|
| M1 | Identidad, permisos y autenticación de personal | Ocho pruebas Surefire y seis PostgreSQL. Acceso estudiantil DD-02 sigue denegado. |
| M2 | Solo package-info y pom | No hay funcionalidad ni pruebas para certificar. |
| M3 | Dominio, lectura/escritura del agregado, huella y comandos parciales | 89 pruebas Surefire y38 PostgreSQL. Se revisó sin modificarlo. No expone recorrido REST de casos completo. |
| M4 | Sesiones, convocatorias, State y consulta de programación | 107 pruebas Surefire y42 Failsafe. Persistencia oficial y reglas RF09/F303 cubiertas. Una prueba Surefire usa Docker. |
| M5 | Dominio, fachada y persistencia de intentos/respuestas | Línea base124 unitarias y16 PostgreSQL. Incorporada localmente; se corrige503 y se añaden pruebas conjuntas. |
| M6 | Solo package-info y pom | Sin evaluación implementada en esta rama. |
| M7 | Solo package-info y pom | Sin proveedor real de notificaciones en esta rama. |
| app | Ensamblado y seguridad | Una prueba Surefire y nueve PostgreSQL. El ensamblado no demuestra integración funcional de los módulos vacíos. |

No se probaron Angular ni interfaces de usuario. La rama remota M3 tiene avances posteriores sin incorporar a develop; este informe evalúa feec0fb, sin fusionar ni modificar trabajo ajeno.

## Defectos encontrados y cambios locales
1. **M5 no estaba en develop.** Se trajo únicamente m5 desde3fc823e con git restore --worktree, sin cambiar el índice ni generar un merge/commit. Sus140 pruebas originales pasaron antes de editarlo.
2. **Error M4 sin traducir en M5.** ProgramacionM4ErroresHttpTest reprodujo una ServletException sin respuesta503. ManejadorErroresM5 ahora captura ConsultaProgramacionNoDisponibleException y TraductorErroresM5 devuelve503 con código público M4 y mensaje genérico. Se añade dependencia unidireccional M5→M4 para consumir su API pública; M4 no depende de M5 ni se crean llamadas HTTP entre módulos.
3. **Conteo histórico contaminado.** El anterior total207/157 incluía un XML Surefire antiguo con17 casos de SesionAcademicaPostgreSqlIT del30/10, además de su ejecución Failsafe vigente. El log original de esa fase suma190 pruebas,140 de M4. Se conserva la explicación de la corrección; los XML y conteos obsoletos se retiraron con autorización para evitar reutilizarlos. El colector exige ahora RunStartedAt y excluye los archivos anteriores a esa ejecución.
4. **Bloqueos del entorno.** Docker estaba apagado y el primer intento falló en M1. Se inició Docker Desktop. Maven no resolvió Maven Central para una nueva dependencia de M3; se descargaron desde Maven Central el JAR/POM jackson-core2.21.7 y sus POM base/BOM, comprobando SHA1 oficial antes de colocarlos en la caché local. No se cambiaron versiones ni pom de M3.

Cambios adicionales de prueba M5:
- El caso de traducción503 se añade al traductor parametrizado.
- Un controlador exclusivamente de prueba verifica el estado503 y ausencia de detalles SQL en la respuesta HTTP; no crea endpoints de producción.
- PostgreSQL: resolver Intento→Participacion→Convocatoria y consultar el contexto público M4 para su estudiante; UUID de otro estudiante devuelve vacío.
- PostgreSQL: fechaRetiro persistida aparece en el contexto, sin convertirla en una decisión de acceso.
- PostgreSQL: indisponibilidad SQL real provoca la excepción pública M4 y su traducción503; el cambio de nombre de tabla se revierte dentro de la transacción de un contenedor efímero y la consulta vuelve a funcionar.

Los datos de sesión/convocatoria/intento en estas pruebas son fixtures oficiales. No se consideran autorización para iniciar/reanudar ni evidencia de identidad estudiantil productiva. El renombrado se hace solo en PostgreSQL efímero y no se ejecuta en bases compartidas.

## Reorganización local de M4

La presentación separa SesionController de ConvocatoriaController, manteniendo las mismas rutas, DTO y manejo de errores. La fachada SesionAcademicaService conserva sus firmas y centraliza en cargarSesionParaModificar los permisos, el bloqueo de fila y la propiedad para actualizar, reasignar, cambiar estado y convocar.

La creación valida el agregado antes de consultar o bloquear proveedores externos. Una identidad docente nula se rechaza como no autenticada; SesionRepositoryAdapter exige una transacción de escritura para proteger la programación. Se simplificaron imports y configuración sin cambiar la selección de proveedores ni permitir stubs productivos.

Se añadieron nueve ejecuciones de prueba: identidad nula y mutación sin docente(2), duración/rango inválidos sin acceso a proveedores(3), validación y error503 de convocatorias por HTTP(2), y persistencia/propiedad de las rutas de convocatorias más rechazo de transacción de solo lectura en PostgreSQL(2). Las nueve ejecuciones nuevas pasaron. La prueba transaccional comprueba la excepción traducida por Spring y su causa; no espera que la excepción Java atraviese el proxy de repositorio sin traducción.

La reorganización no modifica los puertos m4/api, las migraciones ni las implementaciones de otros módulos. Los contratos y el esquema BD-005.2 permanecen compatibles. La documentación se actualiza en estos mismos archivos.

## Lo que todavía impide afirmar que todo funciona
- M2, M6 y M7 no tienen implementación funcional en develop.
- M1/DD-02 no ofrece acceso estudiantil ratificado. No se implementó ni se eludió.
- M5 no expone endpoints de estudiante; iniciar/reanudar y la política de repetición siguen sin implementar/ratificar. Su control de identidad/titularidad permanece provisional, sin conectar a una entrada pública.
- Faltan las reglas de reloj, vencimiento, cierre durante respuestas, reanudación y repetición, y el orden global de locks entre módulos. Las pruebas actuales de concurrencia no cubren todas esas carreras.
- M4 mantiene el proveedor de historial M5 ausente con503: todavía falta publicar y ensamblar un contrato productivo de historial. Las pruebas con datos reales no sustituyen ese adaptador.
- Convocar desde M4 requiere proveedor M7 real; al faltar, la operación falla y revierte. Las pruebas de persistencia M4 importan notificación de prueba explícita.
- La consulta pública M4 está apagada por defecto (prisaly.m4.consulta-bd0052.enabled); activarla no autoriza académicamente.
- V010 compartida sigue sin confirmar. El archivo fuente no se altera, no se ejecuta automáticamente ni se aplica ninguna migración correctiva destructiva. Ver conciliacion-bd0052.

## Verificación reproducible
Desde la raíz Maven, con Docker disponible y los cambios locales de M5 presentes:
```powershell
$inicioRevision = Get-Date
$baselineRevision = (Resolve-Path app/src/main/resources/db/migration).Path.Replace('\','/')
.\mvnw.cmd verify -Pdocker-it "-Dm4.baseline.migrations=$baselineRevision"
if ($LASTEXITCODE -ne 0) { throw 'La regresión no pasó' }
.\m4\review\conciliacion-bd0052\collect-evidence.ps1 -RunStartedAt $inicioRevision -EvidenceDirectory m4/review/revision-m4-m5/evidence
.\m4\review\conciliacion-bd0052\check-package.ps1 -EvidenceDirectory m4/review/revision-m4-m5/evidence
```

Los informes nuevos contienen nombres/resultados de las suites sin propiedades del entorno, SQL de logs ni credenciales. Los logs crudos temporales y los informes de fases sustituidas se retiraron con autorización expresa. Se conserva la evidencia estructurada actual y se consolidaron contratos, decisiones y pendientes vigentes. No se realizaron cambios en implementaciones de app, M1, M2, M3, M6, M7 ni V001–V010.
