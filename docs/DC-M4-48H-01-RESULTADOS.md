# DC-M4-48H-01 — resultados y bloqueos

Fecha: 5 de noviembre de 2026. Estado: cierre condicionado; comprobaciones pendientes formalmente registradas. Este seguimiento reemplaza las cifras Git anteriores para el corte actual, sin reescribir evidencia histórica.

## Versión candidata

`git fetch origin` completado (el primer intento en sandbox falló por red; repetición autorizada fuera del sandbox satisfactoria). Se confirmó que PR #37 ya integró M4. La rama **feature/m4-alineado-20261104** avanzó mediante `git merge --ff-only origin/develop`, sin conflictos, de c2d5dcf a **a7d37ec6ed5207736d0fd691cad814ef04f03ed4**. No se cambió develop local.

- Frente a origin/develop actualizado: **0 por recibir / 0 por integrar**.
- Frente al upstream origin/feature/m4-alineado-20261104: **0 por recibir / 5 commits por subir**, todos ya contenidos en origin/develop; no son cinco cambios M4 nuevos.
- Los dos informes locales previos sin seguimiento se conservaron. La corrección documental y este seguimiento quedan sin commit; no hubo push.
- No hay diferencias de fuentes productivas M3/M4/M5/app entre el SHA aceptado c2d5dcf y el integrado a7d37ec. No se rediseñó M4.

## Cambio autorizado

Se modificó exclusivamente `description` de PM4-21 en `postman/PRISALY-M4-PT-DCP-01.postman_collection.json`: historial legítimo M5, participación necesaria, intento opcional, convocatoria sola insuficiente y estado bloqueado explícito. Las aserciones 409 / M4-SESION-CON-PARTICIPACIONES se conservan.

Verificación: JSON válido; comparación estructural de la colección contra HEAD, normalizando únicamente esa descripción, idéntica. `git diff --check` aprobado. No cambió ninguna otra solicitud/aserción, migración, regla o Java.

## Verificación actual sobre a7d37ec

| Comprobación nueva | Resultado |
|---|---|
| Reactor completo `mvnw -B -DskipTests package` | **FALLA de compilación en app**. M1–M7 empaquetan; pruebas omitidas expresamente. |
| Error concreto | CaseAuthorizationM1Config.java:17: el proveedor anónimo no implementa canCreateCase(String,String), abstracto en CaseAuthorizationPort. |
| Arranque Spring y proveedor efectivo de autorización | Bloqueado por compilación. Además permanecen dos definiciones CaseAuthorizationPort sin selección explícita; no hay cardinalidad runtime acreditada. |
| Dominio/State M4 y empaquetado V010 | **21 aprobadas, 0 fallos, 0 errores, 0 omitidas**, ejecución nueva sobre a7d37ec. |
| Concurrencia PostgreSQL CasoCommandConcurrencyIT | **1 error de infraestructura**, 0 fallos de aserción: Testcontainers no encuentra un entorno Docker válido. |
| IT M4/M5 seleccionadas | No ejecutadas: el reactor se detuvo en M3; M4/M5 SKIPPED. |
| HistorialM4M5IT / cardinalidad en app | Bloqueadas por compilación integrada y motor Docker ausente; no se atribuyen resultados históricos al candidato. |
| HTTP del mismo SHA/JAR | Bloqueado: no se produjo JAR app nuevo válido. No se reutilizó un JAR anterior. PM4-21 continúa bloqueada también por falta de historial legítimo M5. |

Comandos:

```powershell
.\mvnw.cmd -B -DskipTests package
.\mvnw.cmd -B -pl m4 -am test '-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest' '-Dsurefire.failIfNoSpecifiedTests=false'
.\mvnw.cmd -B -pl m4,m5 -am verify -Pdocker-it '-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SesionAcademicaPostgreSqlIT,M4M1ContratosPostgreSqlIT,ParticipacionesM4DesdeM5IT,InicioIntentoIT,CasoCommandConcurrencyIT' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Dm4.baseline.migrations=C:/Users/NICOLAS/OneDrive/Desktop/Prisaly/PRISALY-Backen/app/src/main/resources/db/migration'
```

Evidencia local de esta ejecución: `dc-m4-build.log`, `dc-m4-selective.log`, `dc-m4-integration.log`; XML en m4/target/surefire-reports y m3/target/failsafe-reports. Los logs/target están ignorados por Git. Los resultados 841 históricos no se suman ni se presentan como ejecutados ahora.

Docker: no existe pipe dockerDesktopLinuxEngine; se intentó abrir Docker Desktop y se repitió docker info fuera del sandbox, sin motor disponible. No se ejecutó compose ni se alteró volumen/base. Las suites seleccionadas no contienen llamadas clean/repair; se excluyeron las suites que hacen clean incluso en contenedores efímeros. No se ejecutó clean, repair, DDL compartido ni modificación de migraciones.

## Coordinación efectuada y dependencias

- Se enviaron solicitudes autorizadas al chat **CRISTIAN NUEVO 1** para validar con M3 proveedor único y arranque, y posteriormente el error concreto de compilación. No hay corrección ni evidencia nueva recibida que lo cierre.
- Se comunicó a **Direccion central 4.0** la dependencia M3, al no identificar chat propio de Mariana. Es coordinación mediante Dirección, no confirmación directa de M3. La respuesta recibida asigna la revisión a Mariana/M3 con Cristian/M1; no se toma como autorización para modificar sus componentes desde M4.
- Se coordinó con **Ingrid 4**: su respuesta mantiene D-06 implementado con evidencia histórica y ratificación final pendiente; el parche local GET/PUT M5 todavía requiere inspección de su diff. No se incorporó ese parche externo.
- Inspección del árbol integrado: un proveedor real ParticipacionesPort (`ParticipacionesM4DesdeM5`), un HistorialParticipacionPort (`HistorialParticipacionJdbc`) y una implementación JDBC ProgramacionInicioCoordinadoPort. Los fallbacks/beans M4 usan ConditionalOnMissingBean; no se encontró el puente duplicado de app. Esto respalda unicidad estática, **no demuestra cardinalidad efectiva con Spring arrancado**.
- El orden D-06 conserva Sesion → Convocatoria → Participacion → Intento; consulta informativa y reserva son contratos diferentes. DD-02 y políticas académicas no ratificadas permanecen bloqueadas.

## V010 compartida

**Auditoría no ejecutada por falta de acceso identificado.** El checkout tiene únicamente .env.example; no hay .env ni variables de conexión identificadas para bases compartidas, ni motor Docker local accesible. No se adivinaron credenciales/hosts ni se usaron defaults para conectarse a un entorno compartido.

**Confirmación posterior de Dirección Central:** por el momento no existe una conexión de solo lectura identificada y autorizada para las bases compartidas. Se registra como **bloqueo de infraestructura pendiente de validación**. Su cierre corresponde al responsable de infraestructura mediante confirmación del entorno autorizado o entrega de resultados SQL; ya no queda pendiente una respuesta del usuario sobre disponibilidad actual.

Por cada entorno faltan: flyway_schema_history (versión, script, checksum, éxito), presencia de tablas oficiales/paralelas y, si existen paralelas, inventario de datos a preservar. Consultas preparadas existentes: `m4/review/conciliacion-bd0052/CONSULTAS-V010.sql`, solo SELECT. Hasta obtener esos resultados, V010 compartida queda **desconocida**, no ausente ni aprobada. Cualquier despliegue sobre una base compartida cuyo historial no haya sido revisado permanece condicionado a esa validación.

Este bloqueo no impide verificaciones locales en una base aislada con V001–V009. Se volvió a consultar docker info después de recibir la resolución: la pipe dockerDesktopLinuxEngine continúa ausente. Por ello las IT PostgreSQL locales mantienen un bloqueo separado de disponibilidad de Docker; no se repitieron suites cuyo requisito sigue ausente. Se conservan las 21 pruebas selectivas actuales aprobadas y los resultados fallidos/bloqueados ya registrados. No se ejecutaron modificaciones de esquema, repair, clean, eliminación de tablas ni cambios de migraciones históricas.

## PR preparado

Título: **docs(m4): corregir PM4-21 y registrar verificación integrada DC-M4-48H-01**.

Base: develop. Head: feature/m4-alineado-20261104, actualizado a a7d37ec. PR nuevo de seguimiento, no reapertura ni duplicación de PR #37.

Descripción lista para usar:

> PM4-21 tenía una descripción copiada de edición sin historial. Se corrige únicamente ese texto para exigir historial legítimo M5 y conservar el bloqueo, las solicitudes y las aserciones existentes. Se registra la verificación sobre a7d37ec después de actualizar la rama con origin/develop.
>
> Validación: comparación estructural de colección y diff check aprobados; 21 pruebas selectivas M4 aprobadas. Compilación app bloqueada por CaseAuthorizationM1Config sin canCreateCase; concurrencia PostgreSQL bloqueada por Docker ausente. No hay evidencia HTTP nueva ni auditoría de V010 compartida por falta de conexión autorizada.
>
> Dependencias de cierre técnico: M1/M3 deben resolver compilación y proveedor efectivo único, y probar arranque; M5/Arquitectura deben ratificar D-06 y revisar parche local; QA debe repetir IT/concurrencia y HTTP del mismo SHA/JAR; responsables BD deben aportar auditoría V010 por entorno. DD-02 sigue deshabilitado. Sin cambios de reglas académicas, capas, módulos, BD-005.2, contratos ni patrones GoF.

Archivos del PR de seguimiento: colección y este informe. Los dos informes previos permanecen locales para decidir su incorporación; no incluirlos automáticamente ni usar un add global. PR remoto no creado: todavía no hay commit nuevo que publicar. No se hizo push a ninguna rama.

El criterio de compilación integrada no se cumple. Se entrega registro formal de los bloqueos admitido por DC-M4-48H-01, sin certificar cierre técnico definitivo.

## Cierre de fase — DC-M4-48H-03

Dirección Central acepta el informe y el registro de bloqueos. **Estado final de esta fase: M4 en cierre condicionado, implementación preservada y dependencias formalmente identificadas.** No se requieren modificaciones funcionales M4 mientras permanezcan abiertos los bloqueos externos. El PR documental permanece preparado sin correcciones funcionales ajenas; no se crea commit ni se publica desde este seguimiento.

Las 21 pruebas selectivas aprobadas, la compilación integrada fallida y el intento de concurrencia con error de infraestructura se conservan como evidencias independientes. No se sustituyen por resultados históricos ni se presentan las IT omitidas como aprobadas. PM4-21 permanece bloqueada hasta disponer de historial legítimo M5. V010 compartida queda a cargo del responsable de infraestructura.

Preparación de la próxima fase, sin ejecutar ahora las comprobaciones bloqueadas:

1. Recibir versión integrada compilable de M1/M3/app, confirmar proveedor efectivo único de autorización y registrar el SHA candidato y su diff; conservar el alcance documental M4.
2. Confirmar entorno PostgreSQL aislado autorizado con línea V001–V009 y motor Docker disponible. Usar los comandos selectivos anteriores como punto de partida; no ejecutar suites con clean/repair bajo esta resolución. HistorialM4M5IT y pruebas de cardinalidad app requieren además compilación integrada satisfactoria.
3. Repetir IT de persistencia y concurrencia aplicables y registrar cada suite, fallos, errores y omisiones de esa ejecución. Coordinar D-06 con M5/Arquitectura y verificar un único proveedor de historial y mecanismo de reserva efectivos; no incorporar implementaciones alternativas simultáneas.
4. Construir el JAR app del SHA candidato, registrar SHA-256, perfil y base autorizada y ejecutar HTTP contra ese mismo artefacto. PM4-21 solo se desbloquea con trazabilidad del historial legítimo M5; los demás casos se ejecutan conforme a sus precondiciones.
5. Adjuntar evidencia nueva al PR documental y mantener explícitos los gates que sigan abiertos. La aprobación de despliegue compartido requiere el inventario V010 del responsable de infraestructura.

Se mantienen intactos BD-005.2, migraciones históricas, reglas académicas, DD-02, las cuatro capas, M1–M7 y los patrones GoF. Sin push directo a develop ni nueva ejecución de pruebas en este cierre de fase.

## Coordinación conjunta D-06 — evidencia solicitada por M5

M5 comunica A-01 corregida y aceptada por Dirección, HistorialM4M5IT 23/23 y 80/80 pruebas focalizadas. Se registra como **evidencia comunicada por M5**, sin atribuirla al checkout M4 ni sumarla a sus 21 pruebas. Falta identificar rama/SHA exactos de esa corrección y sus reportes para seleccionar una base conjunta; no se presume que A-01 resuelva el error de compilación de CaseAuthorizationM1Config observado aquí.

Se reconfirmaron rama feature/m4-alineado-20261104 y SHA a7d37ec6ed5207736d0fd691cad814ef04f03ed4, conservando descripción PM4-21 e informes sin commit. No hubo fetch ni corrida nueva de suites en esta comprobación. Docker sigue sin motor disponible.

La comprobación runtime conjunta queda preparada para una base compilable e identificada que contenga la corrección M5: verificar exactamente un ParticipacionesPort efectivo, participacionesM4DesdeM5, ausencia del puente historialM4M5 y un único ProgramacionInicioCoordinadoPort; ejecutar HistorialM4M5IT y las pruebas pertinentes de coordinación, transacción de escritura READ COMMITTED, orden Sesion → Convocatoria → Participacion → Intento, bloqueo concurrente y rollback. Registrar SHA, entorno aislado V001–V009 y reportes nuevos. La inspección estática previa no sustituye esos resultados.

PM4-21 permanece bloqueada hasta un flujo M5 autorizado con historial legítimo. No se habilita DD-02 ni se ratifican políticas académicas mediante las pruebas.
