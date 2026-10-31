# PRISALY — Respuesta del Integrante 3 al Integrante 4

Fecha: 31 de octubre de 2026, America/Bogota.
Estado: propuesta de contrato de consulta implementada localmente, pendiente de ratificación. No habilita inicio/reanudación. Sin commits, push, cambios en GitHub, modificaciones de M5 ni implementación de acceso estudiantil M1.

Se inspeccionó M5 en el commit solicitado 8942eee22cb3c3559abd25ac1eebc9f86da8b42d. Sus cifras de 97 pruebas unitarias y 12 PostgreSQL son reportadas por su responsable; no se reejecutó M5 en esta fase. Se actualizaron referencias remotas de lectura: develop 317f3950e17bace4459ab6712d19cbe66fbe2159. Sus V001–V009 son idénticas a las de la referencia a42c738 usada en el snapshot verificado de pruebas M4.

## 1. Puerto Java público de M4

Existe SesionAcademicaService.verificarAccesoSesion(UUID, UUID), pero solo comprueba sesión ABIERTA/EN_CURSO y existencia de convocatoria en el esquema propuesto. No comprueba tiempo, retirada, titularidad de participación, estado actual del caso ni repetición. Se marcó @Deprecated como aviso a consumidores, conservando su comportamiento y pruebas existentes. No constituye contrato definitivo para iniciar o reanudar intentos.

Se implementó el puerto público de consulta interna:

```java
package com.prisaly.m4.api;

public interface ProgramacionParticipacionPort {
    Optional<ContextoProgramacion> consultarSesion(
        UUID sesionId, UUID estudianteAutenticadoId);

    Optional<ContextoProgramacion> consultarConvocatoria(
        UUID convocatoriaId, UUID estudianteAutenticadoId);
}
```

Es un contrato de hechos persistidos, no un permiso. M5 consume esta interfaz y records; no necesita repositorios M4 ni HTTP. No se agregó dependencia M4→M5 ni una dependencia circular.

El adaptador usa un SELECT parametrizado sobre public."Convocatoria", public."Sesion" y public."Caso", con filtro de idEstudiante en ambas consultas. No carga entidades mutables, consulta tablas M5 ni utiliza sesiones_academicas/convocatorias de V010.

Activación explícita: `prisaly.m4.consulta-bd0052.enabled=true`, únicamente con V001–V009 disponibles. Por defecto se registra un proveedor que lanza ConsultaProgramacionNoDisponibleException; no intenta utilizar el esquema propuesto ni concede acceso por ausencia de datos. Este adaptador de lectura puede probarse sobre la base aprobada sin que eso concilie las escrituras actuales de M4.

## 2. Convocatoria correspondiente a una participación

La relación aprobada es:

`Intento.idParticipacion → Participacion.idParticipacion → Participacion.idConvocatoria → Convocatoria.idConvocatoria → Sesion.idSesion`.

M5 es responsable de resolver su Participacion.idConvocatoria a través de un contrato propio. Una vez obtenido, llama a ProgramacionParticipacionPort.consultarConvocatoria(idConvocatoria, idEstudianteAutenticado).

En 8942eee, IntentoEntity incluye idParticipacion de solo lectura, pero ResumenIntento y RepositorioIntentos no exponen el contexto completo de participación/convocatoria/fechas necesario para esta operación. No se amplió M5 unilateralmente. Ese contrato de resolución sigue pendiente del Integrante 4.

## 3. Titularidad de la participación

M1 debe proporcionar el idEstudiante autenticado, no simplemente idUsuario. La correspondencia Estudiante.idUsuario no es identidad de claves y debe resolverse en M1. M4 no autentica ni deduce esa correspondencia.

M5 obtiene la participación asociada al intento solicitado; de ella resuelve idConvocatoria y llama al puerto M4 con la identidad confiable. El puerto devuelve contexto solo si esa convocatoria tiene exactamente ese idEstudiante.

Para un identificador inexistente o de otro titular, ambas consultas devuelven Optional.empty(), sin distinguir cuál de los dos casos ocurrió ni revelar datos del propietario. No usar el booleano legado ni aceptar idEstudiante arbitrario del cuerpo o URL como prueba de autenticación.

Una consulta exitosa prueba la titularidad de la convocatoria encontrada; demostrar titularidad de la participación exige además que M5 haya obtenido la relación real desde sus datos, no que el cliente haya enviado una convocatoria cualquiera del mismo estudiante.

## 4. Datos para iniciar

ContextoProgramacion entrega:

| Dato | Tipo / semántica |
| --- | --- |
| convocatoriaId, estudianteId, sesionId, casoId | UUID oficiales |
| estadoSesion | Valor persistido BD-005.2, sin traducir al State propuesto |
| estadoAutorizacionConvocatoria | Valor persistido, sin inventar un catálogo definitivo |
| fechaRetiroConvocatoria | Optional<Instant> |
| estadoCaso, casoActivo | Hechos de "Caso"; casoPublicadoYActivo() es informativo |
| inicioPrevisto, finPrevisto | Instant derivados de timestamptz |
| fechaInicioReal, fechaFinReal | Optional<Instant> |
| tiempoMaximoParticipacion | Duration a partir de tiempoMaximoParticipacionSegundos |
| observadoEn | Instant del servidor PostgreSQL al realizar la consulta |

No se incluyen código/hash de autorización, datos personales, respuestas, calificaciones ni autorización de repetición.

InicioPrevisto y finPrevisto no se renombran a apertura/cierre: falta ratificar si son la ventana efectiva o solo planificación, y cómo intervienen las fechas reales y el estado. Tampoco se asume que tiempoMaximoParticipacion equivale al tiempo restante de un intento ya iniciado.

No se ha aprobado una fórmula como `min(inicioIntento + duración, finPrevisto)`: es una alternativa a decidir, no una regla implementada. No se transforma una consulta exitosa en elegibilidad académica definitiva.

## 5. Fuente de tiempo

No se encontró una decisión compartida que ratifique un reloj académico autorizado. El consumidor no debe confiar en el reloj, zona horaria ni temporizador del navegador.

El nuevo contexto obtiene observadoEn mediante clock_timestamp() PostgreSQL, y todas las marcas se expresan como Instant. Esta es una referencia técnica verificable, propuesta para armonizar las comprobaciones temporales; no una ratificación académica unilateral.

M5 en 8942eee usa Clock.systemUTC() para marcar fin al persistir una finalización, y su dominio no decide vencimiento. Esto no demuestra que exista un reloj compartido ni que se esté verificando limiteTemporal en cada respuesta.

Dirección Central debe ratificar reloj, precisión, límite inclusivo/exclusivo y tolerancia. La comprobación final de vigencia debe hacerse dentro de la unidad de trabajo que muta el intento, tras adquirir los bloqueos correspondientes; observadoEn es una instantánea informativa, no un permiso reutilizable.

## 6. Vencimiento mientras responde

M5 dispone de Finalizado y MotivoFinalizacion.TIEMPO_VENCIDO. Detectar el vencimiento y coordinar registro/finalización corresponde a su caso de uso transaccional; M4 no finaliza intentos.

Falta aprobar qué límite rige, cuándo se considera recibida una respuesta y qué ocurre en el instante exacto de vencimiento. También falta decidir si la finalización será por petición, tarea programada o ambos mecanismos.

Requisito técnico a coordinar: serializar registro de respuesta y finalización, impedir reapertura o extensión silenciosa del plazo y conservar lo confirmado. Recomendación pendiente de ratificación: al detectar vencimiento, finalizar con TIEMPO_VENCIDO y rechazar nuevas mutaciones fuera de plazo.

Advertencia transaccional: guardar Finalizado y luego lanzar una excepción RuntimeException en la misma transacción puede revertir la finalización. M5 debe acordar un resultado de dominio/estrategia transaccional que confirme la transición antes de comunicar vencimiento. Esta fase no modifica su implementación ni declara resuelto ese flujo.

## 7. Reanudación

M5 necesita idIntento, idParticipacion, estado persistido, inicio, limiteTemporal, eventual fin y respuestas confirmadas; debe resolver la convocatoria real y consultar nuevamente el contexto M4 para el estudiante autenticado.

Reanudar debe referirse al mismo intento persistido: no es una autorización para crear otro, borrar respuestas o reiniciar el reloj. El tratamiento de sesión cerrada, convocatoria retirada o caso archivado durante un intento ya iniciado necesita decisión explícita.

El contrato M4 aporta contexto actual de sesión/convocatoria. No aporta inicio/limiteTemporal ni saldo de tiempo del intento. En 8942eee esos datos todavía no están expuestos por ResumenIntento y la entidad mapea solo parte de las columnas oficiales. Su incorporación corresponde a M5.

## 8. Repetición

M4 no ofrece autorización de repetición y no la infiere de estar convocado, de tener confirmada=true ni de que la sesión esté abierta. M5 controla historial, número de intento y autorización, sujeto a DD-14 y al contrato que Dirección Central ratifique.

Las migraciones compartidas ya contienen controles: V005 serializa historial por estudiante/caso, V007 consume autorización vigente en la misma transacción y evita transiciones inválidas. Deben respetarse al implementar el inicio, evitando consumo separado o doble uso concurrente.

M4 entrega ids de caso, sesión, convocatoria y estudiante para enlazar esa verificación. No consulta "AutorizacionRepeticion" ni devuelve un booleano puedeRepetir. El módulo/proveedor que publique la consulta de autorización y el procedimiento final de inicio siguen pendientes de acuerdo.

## 9. V010

V010 sigue intacta y única en el módulo M4; no se ha aplicado a una base compartida por esta fase. Se desconoce si otro integrante ya la aplicó y falta confirmarlo con el Integrante 1.

El diagnóstico local ya demostró que V001–V009 crean 21 tablas y V010 agrega dos paralelas, sin activar los triggers oficiales en sesiones_academicas. Por eso no se autoriza incorporar esa propuesta como esquema definitivo.

El nuevo puerto lee las tablas oficiales existentes y no necesita una migración nueva. No se cambió V001–V009 ni se renombró o reescribió V010. El Integrante 1 debe confirmar historial/checksum y numeración, y acordar remapeo de escrituras M4 o migración correctiva posterior si corresponde. No crear tablas puente para ocultar la incompatibilidad.

## Manejo de errores y consistencia

| Situación | Contrato |
| --- | --- |
| Sesión/convocatoria inexistente o no perteneciente al estudiante | Optional.empty(); no revela propietario |
| Puerto apagado, esquema/conexión fallidos o cardinalidad inconsistente | ConsultaProgramacionNoDisponibleException, código M4-CONSULTA-PROGRAMACION-NO-DISPONIBLE |
| Datos temporales persistidos inconsistentes | Error de proveedor; no se crea contexto válido |
| UUID nulo | Error de programación en llamada interna; validar la entrada antes de invocar |
| Mapeo HTTP si se expone desde M4 | 503 con código público, sin SQL ni credenciales |
| Política académica no aprobada | No habilitar inicio/reanudación por una consulta exitosa |

M5 debe traducir el error público si lo presenta por su API. Este trabajo no agrega llamadas HTTP entre módulos.

El adaptador hace una lectura coherente con un SELECT, pero no mantiene locks ni una reserva después de devolver el DTO. El protocolo para serializar retirada/cierre con inicio/reanudación debe acordarse; todavía no se garantiza esa carrera entre módulos. El contexto debe consultarse de nuevo en cada operación protegida y no reutilizarse como permiso en caché.

## Pruebas y límites

Ver evidence/regression-summary.txt y reportes adjuntos. Se preservan las pruebas previas de M4 y se agregan cuatro pruebas de configuración y siete PostgreSQL para el nuevo puerto.

Resultado final de regresión: BUILD SUCCESS; 93 pruebas, cero fallos, errores u omitidas. Surefire: 63; Failsafe: 30. Incluye las 82 de la fase anterior y 11 nuevas; NO incluye las 109 pruebas reportadas por M5.

Empaquetado app: BUILD SUCCESS. Inspección del JAR final: M4 anidado idéntico al compilado, ninguna clase stub/TestM4Application, ambas autoconfiguraciones registradas y V010 única. Ver evidence/package-check.txt. El paquete sigue siendo del checkout M4 local, no una integración del develop actual.

Reproducción local desde la raíz:

```powershell
& ./m4/review/continuidad/prepare-baseline.ps1
$snapshotM4 = (Resolve-Path ./m4/target/bd0052-baseline).Path.Replace('\', '/')
./mvnw.cmd -pl m4 -am verify -Pdocker-it "-Dm4.baseline.migrations=$snapshotM4"
./mvnw.cmd -pl app -am package -DskipTests
& ./m4/review/continuidad/check-package.ps1 -EvidenceDirectory ./m4/review/contrato-m4-m5/evidence
```

Se prueban: activación controlada, proveedor ausente, titularidad, diferencia entre idEstudiante e idUsuario, ausencia/ajeno indistinguibles, fechas timestamptz/Instant y duración, datos retirados/cerrados sin otorgar permiso, reloj SQL y fallo de esquema. Una prueba crea Participacion/Intento reales por fixture SQL, resuelve la relación que debe aportar M5 y llama al puerto M4.

Esa última prueba es integración del contrato M4 con relaciones SQL aprobadas, NO ejecución de ParticipacionFacade/FachadaParticipacionTransaccional ni inicio/reanudación reales de M5. Esos flujos siguen sin estar disponibles en el commit inspeccionado. No se modificó ni se reejecutó M5.

## Pendientes para acuerdo entre integrantes

1. Ratificar paquete, firmas y semántica del puerto propuesto.
2. M1: idEstudiante derivado de identidad autenticada; fuera de este alcance.
3. M5: resolución pública y persistida de participación→convocatoria, y exposición de tiempos del intento.
4. Dirección Central: estados autorizantes, ventana efectiva, reloj, bordes temporales, vencimiento y reanudación tras cierre/retiro.
5. M4/M5: frontera transaccional, orden de locks y repetición DD-14.
6. Integrante 1: tratamiento de V010 y conciliación de las escrituras M4 con las 21 tablas.

No se declara contrato definitivo de elegibilidad ni integración completa M4–M5. Todos los cambios son locales y el PR remoto no se actualizó.
