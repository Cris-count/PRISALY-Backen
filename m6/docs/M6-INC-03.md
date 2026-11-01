# M6-INC-03 — Strategy estructural

## Alcance autorizado y estado

Integrante 2 autorizó exclusivamente la estructura Strategy, sin algoritmo real
ni ficticio. Rama confirmada por lectura de .git/HEAD:
feature/m6-evaluacion-calificaciones. Commit a9c0977 y once pruebas anteriores
aprobadas son referencias reportadas por el usuario, no una validación nueva.
No se ejecutaron Git, Maven, Docker, compilaciones ni pruebas.

## Contrato y modelos

Archivos nuevos en domain/strategy:

```java
interface EstrategiaEvaluacion {
    ResultadoEvaluacion evaluar(SolicitudEvaluacion solicitud);
}
record SolicitudEvaluacion(UUID idIntento, UUID idRubrica) {}
record ResultadoEvaluacion(BigDecimal valor, String detalleEvaluacion) {}
```

Los records comprueban componentes no nulos, conservan los valores y usan solo
tipos inmutables de Java 21. La interfaz establece por contrato solicitud/resultado
no nulos; no contiene código para imponer esa obligación a futuras implementaciones.
No hay defaults, selección automática, registro de estrategias ni implementación
productiva. No se crea orquestador en src/main. No hay Spring, JPA o dependencias
a M1/M3/M5. Calificacion y RetroalimentacionGeneral permanecen intactos.

Los UUID están respaldados por las FK de Calificacion en V001; son referencias
estructurales locales, no un nuevo contrato compartido aprobado con M3/M5.
No prueban existencia, finalización, pertenencia al mismo caso ni autorización.

## Límite deliberado de entrada

La inspección encontró consultas M3 de Caso y M5 de ResumenIntento. Este último
contiene estado, motivo y respuestas, pero sus comentarios mantienen pendiente
el contrato público; no garantiza para M6 un contexto académico completo y estable.
No se importan esas clases ni sus repositorios. La solicitud solo identifica
intento/rúbrica: NO contiene los datos suficientes para calcular una nota.

Antes de implementar un algoritmo habrá que ratificar un contexto inmutable con
respuestas finalizadas y rúbrica/preguntas, procedencia, consistencia y cobertura.
La firma mínima puede requerir evolución aprobada; no se propone que la estrategia
consulte bases de datos para suplir estos datos. No se agregan Map/Object arbitrarios,
preguntas, ponderaciones, respuestas omitidas ni estados nuevos.

## Salida y relación con Calificacion

valor se transporta como BigDecimal sin normalización, límites académicos ni
redondeo; detalleEvaluacion es documento opaco como String, sin parsear ni modificar.
Java null se rechaza; el documento JSON "null" se conserva literalmente. No se
certifica sintaxis JSONB. El JSON no se interpreta como fórmula ni estrategia.

ResultadoEvaluacion NO es una fila persistible ni una Calificacion: no genera UUID,
fecha o versión de política, ni habilita publicación. No duplica la validación de
NUMERIC(8,2) de Calificacion. Puede transportar un decimal exacto no almacenables
en esa columna: una futura frontera debe aplicar las validaciones existentes y
rechazarlo, o una política de redondeo expresamente aprobada; nunca redondear
silenciosamente. No se usa el nuevo record para eludir el modelo validado de 02.

V003 hace inmutable la calificación histórica y V001 permite una por intento;
esta salida no cambia esas restricciones ni define reevaluación o idempotencia.

## Evidencia del patrón

EstrategiaEvaluacion es el punto de variación explícito. El consumidor recibe una
implementación elegida por el llamador; no conoce un algoritmo concreto. La prueba
usa el mismo consumidor con dos dobles intercambiables, que devuelven resultados
previamente suministrados. Ambos dobles y el consumidor están solo en src/test.
Los números de fixture no se calculan a partir de respuestas ni representan
una fórmula aprobada. Otra prueba propaga un error sin fallback numérico.

No se configura ningún bean o implementación utilizable en producción; la evidencia
es del contrato y de la delegación, no de evaluación funcional completa.

## Pruebas preparadas

EstrategiaEvaluacionTest añade seis métodos: intercambiabilidad/delegación única,
ausencia de selección/ejecución al crear la solicitud, propagación de error sin
nota por defecto, UUID obligatorios, conservación exacta de decimal/documento y
estructura inmutable. Las once pruebas anteriores no se modifican.

La dependencia test autorizada ya existe. Ninguna prueba nueva se ejecutó; no
se declara BUILD SUCCESS ni aprobación. Validación manual desde PRISALY-Backen:

```powershell
.\mvnw.cmd -B -pl m6 -am test
```

## Pendientes y riesgos

V002/V003 señalan DD-03 pendiente: fórmula, escala, ponderaciones, cobertura y
redondeo. También faltan contexto intermodular aprobado, tratamiento de omitidas,
versión de política, autorización, persistencia, idempotencia y liberación.
La finalización del intento debe verificarse fuera de estos UUID mínimos.
El principal riesgo es confundir este contrato preliminar con una entrada
académica suficiente o su salida con una calificación persistida.

No se implementan Protection Proxy, integración, endpoints, cálculo ni permisos.
No se avanza a M6-INC-04. Evaluación funcional pendiente.
