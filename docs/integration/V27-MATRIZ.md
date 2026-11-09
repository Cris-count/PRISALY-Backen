# Matriz V27 de integracion — provisional

Los 27 textos se conservan desde el catalogo disponible; la matriz historica corresponde a bc8e378. ARQ-CU-01..07 se conservan separados. Las relaciones historicas RF-CU proceden de PRISALY_Ing de Sofyware II.pdf, pagina 6; el catalogo de 21 CU, de su pagina 8. El JSON adjunto conserva clases, endpoints, pruebas de referencia, puntuaciones historicas y SHA-256 de fuentes. Los textos de brechas historicas pueden haber sido superados: no son hallazgos nuevos.

| RF | Nombre | Modulo | Estado | Cambio de este incremento |
|---|---|---|---|---|
| RF-01 | Autenticar estudiantes | M1 (+M4/M5) | Parcial | A01/A02/A03: recursos de autenticacion y transporte seguro; DD-02 no habilitada |
| RF-02 | Controlar acceso por rol | M1 + APP | Parcial | A01/A02/A03: recursos de autenticacion y transporte seguro; DD-02 no habilitada |
| RF-03 | Administrar docentes, roles y permisos | M1 | Estructural | Sin cambio productivo en este incremento |
| RF-04 | Asociar espacio académico y estudiantes | M4 | Parcial | Sin cambio productivo en este incremento |
| RF-05 | Registrar estructura académica del caso | M3 | Parcial | Sin cambio productivo en este incremento |
| RF-06 | Registrar contextos y preguntas por escenario | M3 | Parcial | Sin cambio productivo en este incremento |
| RF-07 | Administrar preguntas y respuestas | M3 | Parcial | Sin cambio productivo en este incremento |
| RF-08 | Justificar respuestas correctas e incorrectas | M3 | Parcial | Sin cambio productivo en este incremento |
| RF-09 | Validar estructura y rúbrica del caso | M3 + M4 + BD | Parcial | Sin cambio productivo en este incremento |
| RF-10 | Responder y modificar respuestas | M5 | Implementado y bloqueado | Sin cambio productivo en este incremento |
| RF-11 | Registrar respuestas y mostrar progreso | M5 | Implementado y bloqueado | Sin cambio productivo en este incremento |
| RF-12 | Registrar inicio y fin de la participación | M5 + M6 | Parcial | Sin cambio productivo en este incremento |
| RF-13 | Calcular nota con rúbrica | M6 | Estructural | Sin cambio productivo en este incremento |
| RF-14 | Mostrar resultado y retroalimentación final | M6 | Estructural | Sin cambio productivo en este incremento |
| RF-15 | Consultar participaciones de estudiantes | M7 (V27) / M5-M6 | Estructural | Sin cambio productivo en este incremento |
| RF-16 | Descargar reporte de participación | M7 (V27) | No implementado | Sin cambio productivo en este incremento |
| RF-17 | Registrar y asociar la rúbrica | M3 (+M6) | Parcial | Sin cambio productivo en este incremento |
| RF-18 | Agendar fecha, horarios y condiciones | M4 | Parcial | Sin cambio productivo en este incremento |
| RF-19 | Administrar estados de sesiones | M4 | Parcial | Sin cambio productivo en este incremento |
| RF-20 | Asignar y validar código de autorización | M1 / M4 | Estructural | A01/A02/A03: recursos de autenticacion y transporte seguro; DD-02 no habilitada |
| RF-21 | Notificar convocatoria y cambios de estudiantes | M7 + M4 | Parcial | A04: recuperacion SMTP tras configuracion fallida; worker deshabilitado |
| RF-22 | Restringir ingreso tardío | M5 | Estructural | Sin cambio productivo en este incremento |
| RF-23 | Controlar tiempo máximo de participación | M5 | Implementado y bloqueado | Sin cambio productivo en este incremento |
| RF-24 | Evaluar intentos incompletos y ausencias | M6 | No implementado | Sin cambio productivo en este incremento |
| RF-25 | Autorizar repetición excepcional | M5 + BD | Implementado y bloqueado | Sin cambio productivo en este incremento |
| RF-26 | Registrar retroalimentación general docente | M7 (V27) / M6 | Estructural | Sin cambio productivo en este incremento |
| RF-27 | Notificar calificaciones y feedback | M7 | No implementado | Sin cambio productivo en este incremento |

## Medicion

La matriz de auditoria previa informa implementacion 47,2; integracion 40,7; pruebas 54,6; habilitacion 19,4; trazabilidad 62,0 e indice 43,7. Formula conservada: 0,35 I + 0,25 G + 0,20 T + 0,15 H + 0,05 R, promediando los 27 RF. Son mediciones historicas sobre bc8e378, no medidas del SHA inicial 63eceb4 ni del candidato corregido.

Cumplimiento estricto historico: 0/27 con I=100. Este incremento no cierra un RF completo adicional: no se declara superar el 70 %. No se recalifican los 27 RF para aumentar una cifra. Comparacion porcentual inicial/final del incremento: no verificable sin valoracion actual por CA y ratificacion de referencia. Las nuevas pruebas se cuentan separadas del indice ponderado; no son cobertura instrumentada de codigo.

Cobertura de pruebas planificadas para 21 CU historicos: se recuperaron los 21 nombres y relaciones RF-CU documentales; sus sintesis no sustituyen fichas exhaustivas ni ratifican politicas pendientes. No usar numero de suites como denominador ni confundir el plan con ejecucion.
