# M4 — Adaptación a contratos tras incorporar develop

Fecha: 31 de octubre de 2026. Base local: b7b7c0e (merge de develop 03b0899, conservando M4 5e05888).
Los cambios de esta fase están exclusivamente en M4. No se editan implementaciones de M1/M2/M3/M5/M6/M7 ni app. Sin nuevos commits, push o cambios en el PR.

## Identidad y autorización M1

IdentidadM1Adapter consume IdentityAccessPort, no repositorios ni entidades M1. Obtiene currentUser(), comprueba que el UUID declarado coincida con el autenticado y consulta hasPermission() para ese mismo UUID. No consulta permisos de un docente elegido por el cliente ni autoriza por rol como sustituto del permiso.

La autoconfiguración selecciona ese adaptador si existe el proveedor público M1. Si no existe, sigue denegando permisos. UNAUTHENTICATED se traduce a M4-NO-AUTENTICADO/401; identidad inexistente o inactiva a 403; errores de contrato desconocidos a proveedor no disponible, sin exponer mensajes internos.

El permiso usado sigue siendo el código existente GESTIONAR_SESION. Debe estar asignado explícitamente por M1; no se inventa una correspondencia con otros permisos ni se concede por defecto. Sigue pendiente su ratificación académica/arquitectónica.

### API HTTP

- X-Docente-Id es opcional y solo una comprobación de compatibilidad. Si se envía otro UUID, se responde 403. Si no se envía, la identidad proviene de M1. Los clientes deben usar su Authorization y dejar de depender de esa cabecera; no se modificó CORS de app.
- Lectura de sesión y convocatorias verifica permiso y propiedad del docente autenticado.
- Listado devuelve únicamente sesiones del docente autenticado. Un filtro con otro docente se rechaza. No se inventa una excepción de administrador sin política aprobada.
- Las operaciones internas de consulta usadas por el dominio se conservan; los controladores usan las variantes autorizadas.
- La ruta HTTP /m4/sesiones/{id}/acceso/{estudianteId} devuelve 410 M4-ACCESO-LEGADO-RETIRADO y deja de invocar el booleano legado. No se cambió la política interna antigua para convertirla en una autorización académica nueva.

Se conserva el manejo HTTP anterior de entradas 400, conflictos 409, proveedores 503 y errores internos genéricos.

## Consulta autenticada de estudiante para M5

Se añadió com.prisaly.m4.api.ProgramacionAutenticadaPort:

```java
Optional<ContextoProgramacion> consultarSesion(UUID sesionId);
Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoriaId);
```

ProgramacionAutenticadaM1Adapter obtiene el estudiante exclusivamente mediante StudentAccessPort.requireCurrentStudent() y usa studentId, no userId, al invocar la consulta factual M4 existente. No acepta idEstudiante del cliente, no usa tokens de personal como identidad estudiantil y no implementa login estudiantil.

El proveedor productivo actual de M1 deniega por DD-02 y M4 respeta esa denegación, sin consultar sesiones. Sin proveedor también se rechaza. La API factual anterior con UUID explícito se conserva para consumidores internos que ya posean identidad validada; la fachada autenticada es la opción recomendada para peticiones estudiantiles.

La lectura oficial sigue apagada por defecto y requiere prisaly.m4.consulta-bd0052.enabled=true con la línea base disponible. Ninguna consulta autoriza por sí sola inicio, reanudación o repetición. No se modificó ni se integró la rama de M5.

## Contrato M3: adaptación parcial y bloqueo explícito

CasoAcademicoM3Adapter usa exclusivamente CasoRepositoryPort.findById(String), traduce el UUID a cadena y rechaza inexistente, Borrador o agregado de otra identidad. No utiliza CasoJpaRepository ni CasoJpaEntity de M3.

La autoconfiguración puede conectar el adaptador cuando el monolito publique un bean de CasoRepositoryPort. En el develop incorporado existe la interfaz, pero no su implementación productiva como bean. El fallback seguro permanece activo en ese caso.

El agregado Caso de este contrato no expone activo, ni una operación que confirme programación + F3-03 de manera atómica. Por ello esta adaptación NO demuestra elegibilidad completa RF-09 ni integra F3-03 real.

solicitarBloqueoAcademico rechaza con M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO/503. No escribe una marca manualmente ni acepta contenidoEvaluableBloqueado=true como prueba de que la programación actual quedó protegida en la BD oficial. Las migraciones V007–V009 ya incluyen el mecanismo SQL; falta conciliar escrituras de M4 y el contrato transaccional. No se permite convertir la existencia de la interfaz en un éxito simulado.

### Corrección de reasignación en el servicio M4

Al cambiar el caso, M4 protege primero participaciones y también convocatorias, incluso si todavía no hay intentos, de acuerdo con V005/V006. Responde 409 M4-SESION-CON-CONVOCATORIAS en ese segundo caso.

Si es la primera sesión del caso destino, el servicio solicita F3-03 para ese destino dentro de la transacción. Si el proveedor no lo confirma, la operación falla y debe revertirse. La prueba de rollback usa PostgreSQL real con el fallo M3 simulado expresamente; no se presenta como bloqueo real de M3.

## Coherencia de persistencia

SesionRepositoryAdapter usa saveAndFlush antes de construir el resultado de dominio. Así el resultado ya contiene la versión incrementada por Hibernate y los conflictos SQL se detectan antes de continuar con la solicitud F3-03. La prueba existente de actualización comprueba ahora tanto la versión devuelta como la versión persistida.

No se renombraron tablas, campos o estados ni se alteró V010 o V001–V009. No se crea un nuevo esquema ni se introduce un mapeo muchos-a-uno de estados sin aprobación.

## Validación y alcance

Ver evidence/regression-summary.txt y reportes XML de esta fase. Los XML exportados excluyen propiedades de proceso.

Resultado final: BUILD SUCCESS en todo el reactor, 201 pruebas aprobadas, cero fallos/errores/omitidas. M4 aporta 151 (113 Surefire y 38 Failsafe); M1, M3 y app aportan las otras 50. M2/M5/M6/M7 siguen siendo estructuras sin implementación en este checkout y su compilación no demuestra integración funcional.

Se inspeccionó también el paquete app: JAR M4 anidado idéntico al compilado, ninguna clase stub/TestM4Application y una única V010. No se publica este paquete como esquema conciliado; la migración propuesta sigue presente y el diagnóstico 21→23 tablas continúa siendo un bloqueo.

Reproducción desde la raíz:

```powershell
$baselineM4 = (Resolve-Path ./app/src/main/resources/db/migration).Path.Replace('\', '/')
./mvnw.cmd verify -Pdocker-it "-Dm4.baseline.migrations=$baselineM4"
& ./m4/review/continuidad/check-package.ps1 -EvidenceDirectory ./m4/review/adaptacion-contratos/evidence
```

- Adaptadores M1/M3 con dobles de sus interfaces públicas: traducción de identidad/errores, selección de proveedor, UUID, denegación de F3-03 y ausencia de escritura manual.
- Servicio/controlador M4: identidad declarada frente a principal, listado propio, propiedad, historial y solicitud F3-03 del destino.
- Consulta estudiantil: uso de studentId y respeto al DenyingStudentAccessService real de M1.
- M4M1ContratosPostgreSqlIT: IdentityAccessService y repositorios M1 reales sobre PostgreSQL 16.15, junto con adaptador, servicio y controlador M4. Comprueba lectura propia, rechazo ajeno, suplantación por cabecera, creación sin cabecera, permiso revocado y ausencia de identidad.
- En ese test la autenticación se coloca en SecurityContext como fixture confiable. No es prueba de login/token ni de DD-02. La persistencia M4 y M3/M7 son dobles; la creación HTTP de ese escenario no demuestra programación en BD-005.2.
- PostgreSQL M4: versión posterior al flush y rollback de reasignación tras fallo de F3-03. Persistencia M4 propuesta, con M3 simulado.
- Pruebas de app: verificación adicional del ensamblaje completo, sin editar app.

## Qué impide completar el objetivo

1. Ratificar el mapeo de los cinco estados propuestos a los tres estados de "Sesion". También duración obligatoria, fechas reales, timestamptz y autorización de convocatoria.
2. Confirmar con Integrante 1 si V010 fue aplicada, reservar numeración y acordar su tratamiento. Las escrituras siguen usando las tablas propuestas y las consultas oficiales usan "Sesion"/"Convocatoria": no se declara reconciliado ese esquema.
3. Proveedor M3 público persistente y contrato atómico de programación/F3-03, incluyendo caso activo y concurrencia.
4. Proveedor público M5 de historial, identidad estudiantil DD-02, reglas de ventana/reanudación/repetición y M7 real.

La pregunta sobre estados y permiso se dejó al usuario durante esta fase; la falta de respuesta no se interpreta como aprobación. Se completaron las adaptaciones independientes de esa decisión y se mantuvieron las denegaciones seguras. M4 no se declara listo para despliegue ni para cerrar integración.
