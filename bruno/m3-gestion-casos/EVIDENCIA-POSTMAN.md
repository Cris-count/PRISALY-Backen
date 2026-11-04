# Registro de ejecución Postman — M3

Estado: sin ejecutar. Este archivo es una plantilla, no evidencia de resultados.
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
