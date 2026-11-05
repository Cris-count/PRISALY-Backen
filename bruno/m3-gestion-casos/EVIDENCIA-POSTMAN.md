# Registro de ejecución Postman — M3

Estado actual: colección ejecutada mediante Newman 6.2.1: 13 solicitudes
aprobadas y 39 aserciones aprobadas. Ver la corrida automatizada al final.
La ejecución y las capturas de Postman Desktop siguen pendientes. Los apartados
anteriores conservan su estado histórico; no son resultados de esta nueva corrida.
No insertar tokens, contraseñas, cabeceras Authorization ni datos productivos.

## Identificación de la versión efectivamente probada

- SHA HEAD probado: pendiente.
- Cambios locales adicionales, si existen: pendiente de inventario al ejecutar.
- Fecha/hora real y zona horaria: pendiente.
- Responsable/probador: pendiente.
- Entorno, versión PostgreSQL y versión del backend: pendiente.
- Resultado de compilación/arranque y referencia a logs depurados: pendiente.

HEAD inspeccionado durante la preparación:
`ac32db0c38a99d8eaeb9ba9403718ddfce7d8a85`, con cambios sin commit.
No es un SHA de una versión ejecutada ni representa por sí solo el código preparado.

## Evidencias que debe registrar el probador

Para cada escenario: nombre de solicitud, método/URL, cuerpo enviado, estado HTTP,
cuerpo recibido, resultado observado de las aserciones, fecha/hora real, probador,
versión exacta y referencia a captura auténtica o exportación depurada. Ocultar
Authorization, cookies, contraseñas y cualquier dato institucional sensible antes
de compartir capturas o archivos. Conservar los originales en el espacio privado
autorizado, no inventar ni reconstruir respuestas o capturas.

| Solicitud | Estado esperado | Observado | Fecha/probador | Evidencia depurada |
| --- | --- | --- | --- | --- |
| Crear borrador válido | 201 | Pendiente | Pendiente | Pendiente |
| Sin autenticación | 401 | Pendiente | Pendiente | Pendiente |
| Sin permiso | 403 | Pendiente | Pendiente | Pendiente |
| UUID inválido | 400 | Pendiente | Pendiente | Pendiente |
| Sin título | 400 | Pendiente | Pendiente | Pendiente |
| Tiempo cero | 400 | Pendiente | Pendiente | Pendiente |
| Campo desconocido | 400 | Pendiente | Pendiente | Pendiente |
| Propietario en JSON | 400 | Pendiente | Pendiente | Pendiente |
| UUID duplicado | 409 | Pendiente | Pendiente | Pendiente |
| Tiempo decimal | 400 | Pendiente | Pendiente | Pendiente |
| Título excesivo | 400 | Pendiente | Pendiente | Pendiente |
| NUL en narrativa | 400 | Pendiente | Pendiente | Pendiente |
| Tiempo negativo | 400 | Pendiente | Pendiente | Pendiente |

La tabla anterior queda reservada para la ejecución y capturas manuales en
Postman Desktop. La evidencia automatizada Newman se registra separadamente.

Verificar además la fila persistida, su propietario institucional, el estado
Borrador y la conservación del primer caso ante UUID repetido usando un mecanismo
autorizado de inspección de BD. No existe GET M3 habilitado para esa comprobación.
La colección no genera escenarios artificiales de fallo de commit o bloqueo.

GET/PATCH: bloqueados por política institucional; sin solicitudes ejecutadas ni
resultados esperados que puedan acreditarse en esta entrega. No declarar M3 completo.

## Estado después de la validación técnica del 4 de noviembre de 2026

Postman se detectó instalado y se abrió su ventana, pero la pantalla inicial no
expuso controles accesibles y no se pudo confirmar el foco para abrir la
importación de forma segura. No se completó la importación, no se enviaron
solicitudes desde Postman y no hay capturas HTTP válidas de esa aplicación.
Las capturas temporales de preparación no son evidencia de solicitudes.

La colección Postman v2.1 y su entorno están preparados con trece escenarios;
los tokens del ejemplo permanecen vacíos. El responsable debe completar la
pantalla inicial de Postman, importar ambos archivos, seleccionar el entorno,
configurar localmente la autenticación M1 y ejecutar las solicitudes.

Hay un backend técnico aislado en `http://127.0.0.1:18080/api`, con PostgreSQL
16.15 y la fixture local M1 ya existente en el repositorio. No es un entorno
productivo ni una acreditación de usuarios productivos. Para evidencia académica
se debe identificar expresamente el entorno y los datos de prueba utilizados.
La credencial no debe pegarse en esta plantilla ni exportarse con la colección.

Resultados separados, que NO rellenan la columna Observado de esta plantilla:

- Compilación completa: correcta.
- Unidad: 131 M3 y 11 app aprobadas.
- PostgreSQL: 77 M3 y 16 app aprobadas por clase, en varias corridas documentadas.
- Bruno CLI 4.3.0: 13 solicitudes aprobadas, 14 tests y 27 aserciones.
- PostgreSQL independiente confirmó el caso creado y su propietario autenticado.

Referencia de código: HEAD `ac32db0c38a99d8eaeb9ba9403718ddfce7d8a85` más cambios
locales sin commit. JAR efectivamente utilizado, SHA-256:
`51189B9EA5EA8295876111B17E3F73D433054C895AB721BCC197AE831EE756CA`.
El manifiesto de archivos de la sesión complementa esa referencia. Ninguno de
estos datos acredita una ejecución Postman. La tabla anterior sigue pendiente.

## Comprobación previa al cierre manual Postman — 2026-11-04

- Se consultó nuevamente `http://127.0.0.1:18080/api/health`: respuesta correcta
  con `status: ok` y `service: prisaly-backend`.
- Ambos archivos de exportación se analizaron correctamente como JSON. La
  colección declara el formato Postman v2.1 y contiene trece solicitudes POST a
  `{{baseUrl}}/v1/casos`; el entorno declara alcance `environment` y seis variables.
  Esta comprobación estructural no acredita una importación efectiva en la GUI.
- Para este backend hay que sustituir el baseUrl de ejemplo por
  `http://127.0.0.1:18080/api`. Los tokens del archivo siguen vacíos.
- No se repitieron pruebas técnicas ni solicitudes de creación. Los resultados
  Bruno anteriores se conservan. No hay nuevos resultados ni capturas Postman;
  todas las filas de Observado siguen pendientes.

## Revisión estática limitada de los archivos Postman

Se leyeron íntegramente la colección, el entorno de ejemplo y este registro
mediante Get-Content, sin consultar otras fuentes ni ejecutar solicitudes.

- La colección declara Postman Collection v2.1. Sus trece solicitudes utilizan
  POST, `{{baseUrl}}/v1/casos` y Content-Type application/json. No duplica /api.
- La solicitud 02 utiliza noauth; la 03 usa noPermAccessToken; las otras once
  usan accessToken mediante Bearer. No hay tokens ni contraseñas reales en los
  tres archivos. Las respuestas guardadas de la colección están vacías.
- Los cuerpos representan los trece escenarios de la tabla. El NUL está
  escapado como carácter JSON, no como los seis caracteres literales de texto;
  el tiempo decimal es numérico y los opcionales del alta válida son null.
- Los scripts comprueban los códigos HTTP y de error esperados, ausencia de
  detalles internos y, para el alta, únicamente idCaso y estado BORRADOR.
- El entorno contiene baseUrl, accessToken, noPermAccessToken, caseId,
  nuevoCasoUuid y expectedFingerprint. Esta última no se usa: PATCH sigue
  bloqueado. El puerto 8080 es un ejemplo; ajustar al backend realmente iniciado.

Corrección de dependencia entre solicitudes: 01 ahora limpia caseId del entorno
y la colección antes del envío y solo lo guarda después de comprobar 201 y un
cuerpo válido con el UUID solicitado. Antes podía conservar un identificador
anterior o guardar uno de una respuesta 201 que no superara las aserciones.
09 conserva el cuerpo con `{{caseId}}` y ahora exige que coincida con
nuevoCasoUuid. Se aclaró además que el SHA de preparación es histórico, no la
versión de una futura ejecución Postman.

Para ejecutar: importar ambos JSON, seleccionar el entorno, completar baseUrl,
los dos tokens locales sin prefijo Bearer y un nuevoCasoUuid explícito no usado.
No definir caseId mediante variables locales o datos del Runner que oculten el
valor guardado en el entorno. Ejecutar 01 y comprobar 201, estado BORRADOR,
UUID y tests correctos antes de continuar 02–13. No cambiar nuevoCasoUuid durante
la secuencia; para repetirla, usar uno nuevo y comenzar otra vez en 01.

Esta revisión es estática: no acredita importación GUI, disponibilidad actual
del backend ni resultados HTTP. Los trece escenarios permanecen PENDIENTES.
Los resultados anteriores de Bruno y su referencia de versión no se alteraron.

## Precomprobación de ejecución automatizada — 2026-11-04 16:05:15 -05:00

Comprobación real, sin ejecutar la colección:

- GET `http://127.0.0.1:18080/api/health`: HTTP observado 200, cuerpo
  `{"status":"ok","service":"prisaly-backend"}`. No es una de las trece pruebas.
- Newman y Postman CLI no se encontraron como comandos disponibles en PATH.
  Esto no demuestra ausencia de instalaciones fuera de PATH. No se instalaron
  herramientas ni se iniciaron servidores.
- El entorno de ejemplo conserva accessToken, noPermAccessToken y nuevoCasoUuid
  vacíos; baseUrl apunta al ejemplo 8080, no al backend comprobado en 18080.
- La respuesta health no identifica una versión de compilación. En esta
  precomprobación no se verificó el SHA ni el hash del JAR actualmente ejecutado;
  no se le atribuyen las referencias históricas de las corridas anteriores.

Resultado de esta intervención: 0 solicitudes de la colección ejecutadas,
0 aprobadas, 0 fallidas y 13 bloqueadas para esta corrida. HTTP observado y
aserciones de cada una: NO EJECUTADO; los HTTP esperados siguen en la tabla.
No se ejecutó 01 ni 09. No hay reporte Newman/Postman ni capturas nuevas.

Para desbloquear: proporcionar la ubicación de una CLI compatible ya instalada
o autorizar su instalación; configurar de forma privada un token válido M1 con
CREAR_CASO, otro de cuenta activa sin ese permiso y un UUID nuevo explícito.
Usar baseUrl `http://127.0.0.1:18080/api` en el entorno privado de ejecución.
No incorporar los tokens al ejemplo versionado ni a esta documentación.
Se detuvo la ejecución al detectar esos prerrequisitos ausentes, sin intentar
crear credenciales, recuperar secretos de fixtures ni enviar altas parciales.
Los resultados históricos Bruno permanecen intactos y las trece pruebas
Postman continúan pendientes de ejecución real.

## Ejecución real de la colección mediante Newman — 2026-11-04

Ejecutor: Codex, bajo autorización del Integrante 2. Inicio 16:12:22.966 y fin
16:12:34.292, UTC-05:00 (21:12:22.966Z–21:12:34.292Z). Entorno técnico local,
no productivo. Base URL `http://127.0.0.1:18080/api`.

### Prerrequisitos efectivamente resueltos

- Node.js v22.14.0 y npm 10.9.2 disponibles.
- Newman 6.2.1 instalado únicamente en
  `%TEMP%/prisaly-m3-newman-20261104`, sin instalación global ni cambios en
  manifiestos o dependencias del repositorio. npm informó avisos de dependencias
  transitivas obsoletas; no impidieron la instalación ni la ejecución.
- Health respondió 200 antes de ejecutar la colección.
- Se utilizaron las cuentas de prueba ya documentadas en `bruno/README.md` y
  `bruno/environments/local.secrets.bru.example`. No se ejecutaron seeds, cambios
  de usuarios, contraseñas, permisos ni modificaciones en M1. Dos autenticaciones
  reales mediante `/v1/personal/autenticar` devolvieron 200: se comprobó presencia
  de CREAR_CASO en la cuenta positiva y ausencia en la cuenta negativa.
- Tokens únicamente en memoria durante el proceso Node. No se escribieron
  entornos con secretos ni informes nativos que incluyeran cabeceras. El informe
  seguro conserva solo metadatos, HTTP, idCaso/estado/código y aserciones.
- UUID nuevo generado exclusivamente como dato de prueba:
  `2aff7417-80d2-4f88-a253-c86da57b9507`.

### Procedimiento y resultados observados

Se usaron los JSON existentes y el API programático de Newman, sin modificar
la colección ni el entorno exportados. La solicitud 01 se ejecutó primero;
se comprobaron 201, cuerpo válido y caseId guardado. Su entorno en memoria se
transfirió a la ejecución de 02–13, en orden. La lógica de control excluía 09
si 01 no obtenía un 201 validado. No hubo exclusiones en esta corrida.

| N.º | Solicitud | HTTP esperado | HTTP observado | Aserciones | Resultado |
| --- | --- | --- | --- | --- | --- |
| 01 | Crear borrador válido | 201 | 201 | 3/3 | Aprobada |
| 02 | Sin autenticación | 401 | 401 | 3/3 | Aprobada |
| 03 | Sin permiso | 403 | 403 | 3/3 | Aprobada |
| 04 | UUID inválido | 400 | 400 | 3/3 | Aprobada |
| 05 | Sin título | 400 | 400 | 3/3 | Aprobada |
| 06 | Tiempo cero | 400 | 400 | 3/3 | Aprobada |
| 07 | Campo desconocido | 400 | 400 | 3/3 | Aprobada |
| 08 | Propietario en JSON | 400 | 400 | 3/3 | Aprobada |
| 09 | UUID duplicado | 409 | 409 | 3/3 | Aprobada |
| 10 | Tiempo decimal | 400 | 400 | 3/3 | Aprobada |
| 11 | Título de 151 caracteres | 400 | 400 | 3/3 | Aprobada |
| 12 | NUL en narrativa | 400 | 400 | 3/3 | Aprobada |
| 13 | Tiempo negativo | 400 | 400 | 3/3 | Aprobada |

Totales: 13 ejecutadas, 13 aprobadas, 0 fallidas, 0 bloqueadas; 39 aserciones,
0 fallidas; sin errores de runtime reportados por Newman.
01 devolvió idCaso igual al UUID solicitado y estado BORRADOR; 09 devolvió
CASE_CONFLICT. 02 devolvió UNAUTHENTICATED, 03 FORBIDDEN y los nueve escenarios
400 devolvieron INVALID_CASE_REQUEST. Las comprobaciones de ausencia de detalles
internos aprobaron en las trece respuestas.

### Evidencia, versión y límites

Informe estructurado seguro fuera del repositorio:
`%TEMP%/prisaly-m3-postman-safe-results-20261104.json`.
El reporte incluye tiempos UTC y resultados individuales con sus aserciones;
no contiene tokens, contraseñas ni cabeceras de autenticación. Es un informe
derivado del resultado real del API de Newman, no una exportación de la GUI.

La versión exacta del backend actualmente ejecutado no pudo verificarse en
esta corrida: health no expone identificación de build. No se atribuye el SHA
del checkout, un commit publicado ni el hash histórico del JAR a estos resultados.
Las referencias históricas anteriores se conservan exclusivamente para sus
respectivas ejecuciones. No se efectuó inspección adicional de PostgreSQL en
esta corrida; 201 y el 409 posterior son observaciones HTTP reales.

No se inició ni detuvo ningún servicio, no se modificó código productivo y no
se realizaron operaciones Git. Los resultados históricos de Bruno permanecen
intactos. La ejecución automatizada de las trece pruebas queda demostrada;
capturas y ejecución en Postman Desktop, si son exigidas por el docente, siguen
pendientes y no se sustituyen por esta evidencia Newman.
