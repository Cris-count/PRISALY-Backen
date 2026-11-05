# Evidencias permanentes del avance M3

Consolidación de solo lectura de resultados, sin ejecutar pruebas, Maven, Docker,
servidores ni HTTP. Los originales y los documentos históricos no se sobrescriben.
DC-INT-02 y nuevas funcionalidades siguen pausados.

## Inventario de evidencias

| Directorio/archivo | Procedencia y alcance | Resultado |
| --- | --- | --- |
| surefire/TEST-*.xml | Diez suites M3; derivados de m3/target/surefire-reports | 132 tests, 0 fallos, errores u omitidas |
| failsafe/TEST-*.xml | Siete suites seleccionadas; derivados de m3/target/failsafe-reports | 71 tests, 0 fallos, errores u omitidas |
| failsafe/failsafe-summary.xml | Resumen existente de la selección reportada | completed=71; errors/failures/skipped=0 |
| newman/newman-resultados-seguros-20261104.json | Copia del reporte seguro de TEMP; Newman 6.2.1 | 13 solicitudes aprobadas y 39 aserciones aprobadas |
| bruno/bruno-resumen-historico-20261104.json | Derivado por lista permitida del informe histórico Bruno CLI 4.3.0 | 13 solicitudes, 14 tests y 27 aserciones aprobadas |
| metadatos/procedencia.json | Selección, fuentes y hashes originales, destino y depuración | No acredita versión ejecutada |
| metadatos/avisos-hikari.json | Dos mensajes reales de consola XML y testcase de origen | Pruebas de agotamiento aprobadas; no clasificación automática de defecto |
| metadatos/estado-local.txt | Rama, HEAD y estado observados mediante Git de lectura | Estado del paquete; no SHA de backend de las corridas |
| metadatos/controles-consolidacion.json | Conteos, controles de estructura y búsqueda de patrones sensibles | Revisión técnica acotada; no garantiza ausencia universal de secretos |

Las suites Failsafe incluidas son CasoCommandAddContentIT (22),
CasoCommandConcurrencyIT (7), CasoCommandUpdateIT (13), CasoJpaAggregateReadIT (1),
CasoJpaAggregateTransactionIT (2), CasoPersistenceAdapterIT (25) y RubricaJpaReadIT (1).
No se suman los XML previos CasoJpaReadIT, EvaluableContentJpaReadIT,
ContextoAcademicoProducerReadIT y PostgreSqlDockerIT. Sus nombres y metadatos
quedan registrados como excluidos en procedencia.json; los originales no se borran.

## Fecha y trazabilidad

La ejecución Maven fue confirmada por Integrante 2 para el 4 de noviembre de 2026.
Los archivos unitarios tienen LastWriteTime de 16:39 y las siete integraciones
de 16:40–16:42 UTC-05 de ese día. Esto corrobora la selección y es compatible con
lo reportado, pero LastWriteTime no es un timestamp certificado de ejecución:
los XML no incluyen timestamp de suite. El resumen no reemplaza el log de Maven.
BUILD SUCCESS procede de la confirmación del ejecutor; no se adjunta un log
completo identificado de esa misma ejecución ni se reutiliza uno antiguo.

Newman registra inicio 2026-11-04T21:12:22.966Z y fin 21:12:34.292Z. Su evidencia
se conserva como ejecución automatizada; no es una exportación de Postman Desktop.
Bruno es una corrida histórica distinta del mismo día. No se suman sus tests
ni se trasladan sus referencias de artefacto a la corrida Newman o Maven.

No se verificó la versión ejecutada del backend en Newman ni el SHA del código
de la corrida manual Maven. HEAD actual identifica el repositorio al consolidar,
con cambios sin confirmar. No corrige retroactivamente aquella falta de versión.

## Depuración y conservación

Los XML son DERIVADOS: solo conservan contadores, tiempos y testcase con nombre,
clase y tiempo. Se retiraron todas las properties y system-out/system-err para
evitar copiar entorno, URLs de conexión y secretos. Los hashes de originales
permiten cotejo privado autorizado; no deben esperarse hashes iguales entre un
original y su derivado. Los resultados individuales y totales no se alteraron.

Newman ya era un reporte seguro sin request headers ni entorno. Se verificaron
conteos y ausencia de campos de credenciales/cabeceras antes de copiarlo. Bruno
se reduce a resultados HTTP y aserciones sin copiar request/response headers o
cuerpos. No se adjuntan logs crudos ni entornos privados.

El inventario CSV en la carpeta superior contiene el hash de cada archivo del
paquete, incluidos estos derivados. Su propia fila omite hash/tamaño para evitar
autorreferencia. Revisar manualmente los archivos antes del empaquetado final.

## Evidencias y decisiones todavía pendientes

- Matriz oficial integral de requisitos y aceptación: no permite acreditar 40 %.
- Versión ejecutada y comandos exactos de las corridas no identificadas.
- Log final de BUILD SUCCESS y contexto de otros avisos PostgreSQL mencionados.
- Capturas Postman Desktop, solo si el docente las exige; no existen inventadas.
- Nuevas pruebas de ensamblaje app preparadas por DC-INT-02: no ejecutadas,
  fuera de los conteos consolidados y fuera del paquete fuente M3.
- Publicación persistente exitosa/DD-03, GET/PATCH y autorizaciones,
  copia persistida/archivado y corrección DC-INT pausada permanecen pendientes.

Este paquete está organizado para revisión académica del avance; no certifica
módulo completo ni arranque del estado combinado de develop. No se crea ZIP.
