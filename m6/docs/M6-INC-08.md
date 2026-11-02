# M6-INC-08 — Preparación estructural del contexto académico autorizado

Estado: implementación local preparada; pendiente de ejecución manual e integración
institucional. Base c4a844f y productor M3 afb1a50 son referencias reportadas, no
verificadas mediante Git. Rama comprobada por lectura de HEAD: feature/m6-evaluacion-calificaciones.

## Resolución y archivos

Dirección Técnica autoriza preparación estructural de un intento FINALIZADO, sin
evaluación automática, escritura de notas, DD-04 ni liberación DD-12.

Nuevos, bajo m6:

- `src/main/java/com/prisaly/m6/application/port/SolicitudCapacidadContextoAcademicoPort.java`
- `src/main/java/com/prisaly/m6/application/integration/ContextoEvaluacionPreparado.java`
- `src/main/java/com/prisaly/m6/application/PreparacionContextoException.java`
- `src/main/java/com/prisaly/m6/application/service/PrepararContextoEvaluacionService.java`
- `src/test/java/com/prisaly/m6/application/service/PrepararContextoEvaluacionServiceTest.java`
- `docs/M6-INC-08.md`

Modificados: ConsultaContextoAcademicoPort y ContratosConsumidoresTest para incorporar
la capacidad. No se modifica POM, Strategy, Protection Proxy, DTO INC-07 ni persistencia.

## API y flujo

```java
Optional<ContextoAcademico> findByCasoId(UUID idCaso, String capacidad);
String solicitarCapacidad(UUID idIntento, UUID idCaso);
ContextoEvaluacionPreparado preparar(UUID idIntento);
```

El resultado es un record con IntentoEvaluacion intento y ContextoAcademico
contextoAcademico, ambos obligatorios. No contiene capacidad, calificación ni decisión
de publicación. Conserva los DTO inmutables sin transformar decimales, JSON o fechas.

El servicio no es bean ni endpoint: app debe construirlo con proveedores confiables.
Solo recibe externamente idIntento. Consulta el intento; comprueba identidad exacta
y FINALIZADO; toma participación y caso del DTO recuperado; solicita capacidad para
ese intento/caso; rechaza null/vacío/blanco; consulta contexto con el caso y la cadena
exacta; comprueba caso y referencias; devuelve los datos. No llama Strategy ni posee
dependencias hacia escritores. No usa AutorizacionEvaluacionPort como capacidad.

Se comprueban padres escenario/caso, pregunta/escenario y opción/pregunta, unicidad
de UUID de esos elementos y de preguntas respondidas, existencia de cada pregunta
respondida y pertenencia de su opción seleccionada. No se exige respuesta a todas
las preguntas, opción correcta seleccionada, rúbrica completa, estado Publicado,
actividad, visibilidad o estructuras no vacías como condiciones académicas nuevas.
La rúbrica se transporta; su integridad productora sigue siendo responsabilidad M3.

## Seguridad y responsabilidades

SolicitudCapacidadContextoAcademicoPort no recibe identidad, rol ni permiso libres.
Su adaptador en app debe invocar M1 bajo identidad confiable y obtener la capacidad
de M6_EVALUACION / CONSULTAR_CONTEXTO_EVALUABLE para el caso concreto. M6 no emite
ni valida criptografía, entropía, expiración o revocación. M3 verificará la cadena
antes de leer mediante su mecanismo institucional conectado desde app.

El servicio no acepta un booleano como autorización ni registra/cachea/devuelve la
capacidad. String es opaco y no demuestra autenticidad; no puede borrarse de memoria
de forma garantizada. No añadir trazas de argumentos ni mensajes de proveedor que
puedan revelar secretos. App/M1 deben aplicar auditoría segura sin tokens originales.

La consulta inicial de intento necesita autorización de su productor/frontera de
invocación. Este servicio interno no crea esa autorización. No se expone un nuevo
consumidor productivo mientras no exista ensamblaje y control de acceso aprobados.

## Errores y prioridad

UUID null falla antes de consultar. Consulta de intento fallida produce
FALLO_CONSULTA_INTENTO; ausencia INTENTO_INEXISTENTE; UUID distinto
UUID_INTENTO_INCONSISTENTE; estado no finalizado INTENTO_NO_FINALIZADO.
Estos rechazos no solicitan capacidad ni contexto.

Emisor null produce EMISOR_AUSENTE; excepción técnica EMISOR_NO_DISPONIBLE; respuesta
null/blanca CAPACIDAD_INVALIDA. Ninguno consulta el contexto académico.

Las denegaciones de los adaptadores deben traducirse a AccesoEvaluacionDenegadoException.
El servicio devuelve una instancia limpia de esa excepción sin causa de proveedor.
Otros fallos de consulta académica producen FALLO_CONSULTA_CONTEXTO, un Optional null
también se considera fallo técnico. Optional.empty después de autorización corresponde
a CONTEXTO_INEXISTENTE. Caso distinto produce CASO_INCONSISTENTE; referencias inválidas
REFERENCIAS_INCONSISTENTES. No se devuelve resultado parcial ni ausencia por fallos.

Los errores técnicos se clasifican mediante código fijo y no incorporan causas o
mensajes recibidos: podrían contener la capacidad. La observabilidad técnica detallada
debe resolverse en proveedores confiables con sanitización, no registrando secretos.
No hay mapping HTTP ni nuevos permisos.

## Pruebas y límites

Nueve métodos nuevos: recorrido válido con InOrder y conservación de los mismos DTO;
rechazos de intento; argumento null/fallo de intento; emisor ausente; emisor fallido
o denegado; capacidades vacías; ausencia/denegación/fallo del contexto separados;
caso/pregunta/opción inconsistentes; vacíos y rúbrica opcional sin políticas nuevas.
Se adapta la prueba de contratos INC-07 a la firma con capacidad.

La conservación por identidad de DTO cubre participación, decimales, fechas, órdenes,
JSON y nulos sin reconstrucción. Los tests verifican el paso exacto de capacidad/caso
y ausencia de capacidad/causas sensibles en resultados y excepciones. No hay ejecución
de Strategy ni escritura productiva en el grafo de dependencias del servicio.

Los dobles son solo fixtures locales; no demuestran identidad, HMAC, revocación ni
seguridad de un adaptador real. FINALIZADO no certifica titularidad, autorización
histórica o estabilidad de respuestas. No hay transacción conjunta entre módulos ni
versionado histórico: eso requiere garantías del productor M5, M3 y Arquitectura.

Pendientes: productor M5 con intento/participación/caso/respuestas coherentes; M1
emisor real; M3 productor protegido; app adaptadores, traducción de errores/DTO y
ensamblaje confiable. Ninguna dependencia directa M6→M1/M3/M5 se agrega. DD-03,
selección DD-04 y DD-12 permanecen bloqueados y fuera de este alcance.

## Validación manual pendiente

No se ejecutaron Git, Maven, Docker ni pruebas; no se declara compilación ni aprobación.
Desde PRISALY-Backen:

```powershell
mvn -pl m6 -am test
mvn -pl m6 -am -Pdocker-it verify
```

El segundo comando es regresión de los lectores PostgreSQL anteriores; no demuestra
la integración nueva. Registrar resultados reales. M6 no dispone todavía de evaluación
automática ni integración productiva completa.
