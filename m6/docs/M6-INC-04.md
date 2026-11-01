# M6-INC-04 — Protection Proxy estructural

## Alcance y fuentes

Integrante 2 autorizó la estructura de protección, sin permisos institucionales,
integración, cálculo o persistencia. Rama confirmada por lectura de .git/HEAD:
feature/m6-evaluacion-calificaciones. e1f7f5f y 17 pruebas previas aprobadas son
referencias reportadas, no una validación nueva. No se ejecutaron Git, Maven,
Docker, compilaciones ni pruebas.

Se inspeccionaron los modelos/Strategy de M6, los puertos de aplicación y el
contrato público IdentityAccessPort de M1. Este expone identidad, roles y permisos
genéricos, pero no se localizaron permisos específicos ratificados para evaluación
M6. No se deduce acceso a partir de un rol ni se inventan códigos de permisos.

## Contratos e implementación

- application/port/EvaluacionCasoUso: evaluar(SolicitudEvaluacion) devuelve
  ResultadoEvaluacion. Es la interfaz común del proxy y el servicio real futuro.
- application/port/AutorizacionEvaluacionPort: verificarAcceso(solicitud) entrega
  AUTORIZADO, DENEGADO o NO_DISPONIBLE. Son decisiones de acceso, no estados
  académicos ni permisos institucionales. No hay implementación productiva.
- application/proxy/EvaluacionProtectionProxy implementa EvaluacionCasoUso y recibe
  el puerto y otro EvaluacionCasoUso como delegado.
- application/AccesoEvaluacionDenegadoException representa un rechazo local de
  aplicación, sin código HTTP ni código de permiso público.

Se reutilizan sin cambios SolicitudEvaluacion y ResultadoEvaluacion de Strategy.
Compartir tipos no significa que el proxy implemente Strategy: protege el caso de
uso; el futuro servicio decidirá su relación con una estrategia aprobada. No se
crea ese servicio ni una implementación ficticia en src/main.

## Denegación segura

La solicitud debe ser no nula. Si falta el proveedor (null), el proxy puede
construirse pero siempre deniega al invocarse. Si la decisión es DENEGADO,
NO_DISPONIBLE o null, lanza AccesoEvaluacionDenegadoException sin llamar al delegado.
Si verificarAcceso lanza RuntimeException, se deniega conservando la causa para
diagnóstico, con mensaje externo genérico. No se capturan errores fatales de JVM;
su propagación también impide alcanzar la delegación.

Solo AUTORIZADO permite delegar una vez con la misma instancia de solicitud;
se devuelve exactamente el resultado del delegado. Las excepciones del delegado
se propagan sin reclasificarse como autorización, porque se ejecuta fuera del
catch del proveedor. No hay reintentos, fallback académico, generación de notas,
registro de calificaciones, caché positiva ni consultas a repositorios.
El delegado es obligatorio; una solicitud o configuración inválida produce
NullPointerException sin iniciar una operación académica.

## Identidad y dependencias pendientes

El puerto pertenece a M6 y no importa M1 o Spring Security. Una implementación
futura autorizada deberá resolver la identidad actual desde un contexto confiable,
comprobar permiso institucional y alcance del recurso (intento/rúbrica). Los UUID
de la solicitud no autentican a su llamador. No se permite que el cliente declare
por sí mismo una identidad autorizada ni se proporcionan identidades simuladas.

Necesitan aprobación: códigos de permisos, alcance por recurso, actores admitidos,
contexto de ejecución sin petición HTTP y tratamiento institucional de errores.
También siguen pendientes DD-03 y los contratos de contexto académico M3/M5.

Las consultas de resultados y retroalimentaciones requerirán su propio análisis
de acceso cuando se autoricen; no se añaden operaciones ahora. No se habilitan
reevaluación, liberación, publicación ni un ciclo de evaluación real.

## Evidencia y límites de seguridad

El proxy y el delegado comparten interfaz: el consumidor puede utilizar el proxy
sin conocer el servicio concreto. Para protección efectiva, el ensamblado futuro
debe exponer exclusivamente la entrada protegida; un consumidor con acceso directo
al delegado podría omitir la comprobación. No hay beans ni ensamblado productivo
en este incremento y no se afirma autorización institucional efectiva.

La comprobación previa no resuelve por sí sola revocaciones concurrentes o cambios
de titularidad entre comprobación y ejecución. Debe acordarse la consistencia con
M1 y el futuro caso de uso. El proxy no valida existencia, estado Finalizado,
pertenencia de rúbrica, formato JSON, rango de calificación ni idempotencia: esas
responsabilidades no deben confundirse con acceso y siguen pendientes.

## Pruebas preparadas

EvaluacionProtectionProxyTest incorpora ocho métodos: autorización y orden de
delegación exacta; denegación/no disponibilidad; proveedor ausente; decisión null;
fallo del proveedor; propagación de excepción del delegado; reevaluación de acceso
en cada invocación; configuración/solicitud inválidas sin efectos. Se comprueban
identidad de solicitud y resultado y conservación literal de datos del resultado.
Todos los dobles Mockito existen solo en src/test. No realizan cálculos académicos.

Las pruebas previas y los modelos/Strategy no se modifican. La dependencia test
ya autorizada es suficiente según la inspección estática; no se modifica POM.
No se ejecutaron pruebas: resultados nuevos pendientes del Integrante 2.

Validación manual desde PRISALY-Backen:

```powershell
.\mvnw.cmd -B -pl m6 -am test
```

No se declara evaluación funcional terminada. Fin del alcance M6-INC-04.
