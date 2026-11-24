# DC-CIERRE-BACKEND-02 — M4: lector concurrente con reserva D-06

8 de noviembre de 2026. Responsable: Nicolás / M4.

## Candidato y bloque funcional

Rama `feature/m4-alineado-20261104`. SHA de partida y HEAD al terminar:
`05a3bce4c24ccd069d23be3f44b4fd449d86269c`.
Las modificaciones descritas están sin commit; HEAD no identifica por sí solo este parche.
No se creó PR, push ni merge. Los archivos no rastreados de campaña CI ya existían y no se modificaron.

La evidencia previa de 114 pruebas PostgreSQL sobre `d8eb62d` permanece independiente.
Este trabajo aborda únicamente la brecha documentada en
`DC-M4-POSTGRES-02-RESULTADO.md`: lectura M5→M6 concurrente con la reserva D-06.
No se reprodujo un defecto productivo ni se cambió código de producción.

## Archivos y cambios

- `app/src/test/java/com/prisaly/config/HistorialM4M5IT.java`: dos casos nuevos.
- Este informe y `docs/evidencias/DC-CIERRE-BACKEND-02/resumen.json`.

Los casos `lectorM6NoEsperaReservaD06NiVeInicioSinCommit` y
`lectorM6NoExponeInicioRevertidoPorD06` mantienen abierta la transacción real
que reserva Sesion/Convocatoria y crea Participacion/Intento. Una segunda tarea
invoca el lector protegido real antes de liberar al escritor: debe terminar en
cinco segundos sin exponer el intento no confirmado. Después de commit debe
obtener el intento y su Caso; después de rollback debe continuar ausente.
Ambos casos comprueban cardinalidad de Participacion e Intento y finalizan los
hilos en finally. Identidad, elegibilidad y autorización institucional son fixtures
explícitos; repositorios, transacciones, reserva y consulta son los existentes.

No certifican consistencia de una lectura ya iniciada frente a cambios posteriores,
ni un flujo HTTP productivo. No agregan proveedores ni mecanismos transaccionales.

## Validación nueva

| Comprobación | Resultado |
|---|---|
| Maven `-pl app -am test-compile -DskipTests` | BUILD SUCCESS; compila los casos nuevos y todo el reactor necesario |
| M4CoordinacionInicioTest | 4 aprobadas, 0 fallos/errores/omisiones |
| ConsultaIntentoEvaluacionDesdeM5Test | 11 aprobadas, 0 fallos/errores/omisiones |
| HistorialM4M5IT, selección de los dos casos nuevos | Bloqueada antes de ejecutar métodos: Testcontainers no encuentra Docker |
| git diff --check | Aprobado |

Total unitario nuevo: **15/15**. No son evidencia PostgreSQL.
El XML nuevo de HistorialM4M5IT registra **1 error de inicialización de clase**,
no dos pruebas ejecutadas. `integration-test` mostró BUILD SUCCESS porque
Failsafe difiere su veredicto hasta `verify`; ese texto no constituye aprobación.
Una primera invocación de `verify` se detuvo en M3 por un resumen Failsafe previo
(1 error); no se atribuye como resultado nuevo M3 ni como ejecución de APP.

Logs locales ignorados: `dc-cierre-backend-02-compile.log`,
`dc-cierre-backend-02-unit.log`, `dc-cierre-backend-02-tests.log` e
`dc-cierre-backend-02-it.log`. El resumen persistente incluye hash del archivo
Java modificado para identificar el parche sin confundirlo con HEAD.

Reejecución en un checkout revisado con Docker operativo, PostgreSQL efímero
y autorización de fixtures de DC-MACRO-04; `clean` limpia salidas Maven y evita
reutilizar resúmenes Failsafe anteriores:

```powershell
.\mvnw.cmd -B -pl app -am clean verify -Pdocker-it `
  '-Dtest=M4CoordinacionInicioTest,ConsultaIntentoEvaluacionDesdeM5Test' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dit.test=HistorialM4M5IT#lectorM6NoEsperaReservaD06NiVeInicioSinCommit+lectorM6NoExponeInicioRevertidoPorD06' `
  '-Dfailsafe.failIfNoSpecifiedTests=false'
```

Exigir dos testcase nuevos aprobados, cero errores/omisiones y `verify` exitoso.
La campaña temporal existente fija otro SHA y conteos anteriores: no ejecuta
estos cambios locales ni debe presentarse como su validación.

## Trazabilidad y decisiones

Relación técnica con la protección del historial de programación y la frontera
M4→M5→M6, BD-005.2 y protocolo D-06. El catálogo V27 no se localizó como archivo
identificable en este workspace; no se inventa un código V27 para esta brecha.
La asignación exacta queda pendiente de su fuente oficial.

No se habilitan DD-02, DD-03, DD-04 ni D07; D-06 tampoco se ratifica mediante
fixtures. No se modifican BD-005.2, migraciones ni contratos compartidos.
Estado: parche de pruebas compilado y revisable, **validación PostgreSQL pendiente
por Docker no disponible**. No se declara cerrado este bloque ni habilitada
la transición a frontend/simulador.
