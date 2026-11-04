# PRISALY M3 — Colección de creación

Estado: trece solicitudes ejecutadas técnicamente con Bruno CLI 4.3.0 en la base
efímera de validación usando la fixture local existente de M1; trece aprobadas.
No es evidencia Postman ni éxito productivo. La fuente de contratos, seguridad, bloqueos y
cobertura es [DC-M3-REST-03](../../m3/docs/DC-M3-REST-03.md).

## Preparación

1. Abrir esta carpeta como colección Bruno independiente; no reemplaza la de M1.
2. Seleccionar `local.example`. `baseUrl` ya incluye `/api`, siguiendo la convención
   existente: `http://localhost:8080/api`. No añadir otro `/api` en las solicitudes.
3. Obtener credenciales y token mediante el procedimiento institucional de M1.
   No se incorporan usuarios ni permisos ficticios a esta colección. La cuenta
   positiva debe estar activa y tener CREAR_CASO realmente asignado.
4. Configurar `accessToken` solo en sesión o almacenamiento local privado. Para
   el escenario 03, `noPermAccessToken` debe ser un token real de una cuenta activa
   sin ese permiso; un token inválido produce 401, no demuestra denegación 403.
5. Proporcionar `nuevoCasoUuid` explícitamente con un UUID completo no usado.
   No hay scripts que generen UUID, fechas, capacidades o contenido académico.
6. Ejecutar 01; únicamente ante 201 se conserva `caseId` en una variable de sesión.
   El escenario 09 necesita ese mismo caseId para probar unicidad y no sobrescritura.
7. Ejecutar las otras solicitudes con sus precondiciones. Repetir 01 con el mismo
   UUID producirá 409; para una nueva alta debe suministrarse otro UUID explícito.

Los scripts anteriores a la solicitud comprueban variables mínimas para evitar
enviar accidentalmente una prueba positiva sin token o UUID. Los tests validan
HTTP, código, respuesta mínima y ausencia de detalles internos. No capturan ni
exportan el token. No guardar entornos con valores reales dentro del repositorio.
La resolución usa primero `bru.getVar` (sesión) y después `bru.getEnvVar` (entorno).
Los entornos .bru deben guardarse en UTF-8 sin BOM: Bruno CLI 4.3.0 rechaza el BOM.

## Solicitudes ejecutables

| Archivo | HTTP esperado | Precondición |
| --- | --- | --- |
| 01-crear-borrador | 201 | Cuenta autorizada y nuevoCasoUuid no usado. |
| 02-sin-autenticacion | 401 | No se envía Authorization. |
| 03-sin-permiso | 403 | Token válido sin CREAR_CASO. |
| 04-uuid-invalido | 400 | Token autorizado. |
| 05-sin-titulo | 400 | Token autorizado. |
| 06-tiempo-cero | 400 | Token autorizado. |
| 07-campo-desconocido | 400 | Token autorizado. |
| 08-propietario-en-json | 400 | Token autorizado; docenteId no es admitido. |
| 09-uuid-duplicado | 409 | caseId de 01 ya persistido. |
| 10-tiempo-decimal | 400 | Token autorizado. |
| 11-titulo-excesivo | 400 | Token autorizado. |
| 12-texto-nul | 400 | Token autorizado. |
| 13-tiempo-negativo | 400 | Token autorizado. |

Los valores textuales y 300 segundos son datos de prueba explícitos, no defaults.
Las respuestas no incluyen propietario, fecha, contenido evaluable ni huella.
`expectedFingerprint` queda reservada para una futura activación autorizada de
PATCH; no debe inventarse ni interpretarse como credencial.

## Consulta y actualización

No contienen .bru ejecutables: GET carece de política de lectura/visibilidad y
PATCH de modificación institucional aprobada. Están descritos cuatro escenarios
de consulta y ocho de actualización en sus archivos PENDIENTE.md y en la matriz
técnica. No enviarlos todavía ni presentar un 404 del router como prueba de
autorización o inexistencia de un caso.

## Postman

Importar `../exports/PRISALY_M3_Postman_Collection.json` y
`../exports/PRISALY_M3_Postman_Environment.example.json` desde el explorador de
archivos (rutas relativas a esta carpeta). Seleccionar el entorno importado y
configurar variables localmente sin guardar
tokens en la exportación. Ejecutar con las mismas precondiciones. Los tests
Postman validan los mismos trece escenarios; las carpetas bloqueadas están vacías.
Tras 201, caseId se actualiza en la colección y en el entorno seleccionado para
que una variable de entorno vacía no oculte el UUID del caso creado.

Para entrega académica usar evidencia auténtica Postman y completar
[EVIDENCIA-POSTMAN.md](EVIDENCIA-POSTMAN.md). Un test técnico o una colección
preparada no acredita una solicitud HTTP ejecutada.

## Validación técnica registrada

La ejecución final obtuvo trece solicitudes aprobadas, catorce tests y veintisiete
aserciones. El alta válida devolvió 201; sin autenticación 401; sin permiso 403;
UUID duplicado 409; los otros nueve escenarios devolvieron 400. Una consulta
independiente en PostgreSQL confirmó el commit y el propietario M1. Ver el
registro técnico en DC-M3-REST-03 y los logs depurados de la sesión.

Se usaron exclusivamente la base efímera propia y la fixture ya existente
`bruno/scripts/m1-bruno-seed-local.sql`, con sus credenciales de prueba leídas
localmente. No se crearon permisos nuevos ni se modificó la autenticación M1.
Los tokens solo existieron en un entorno temporal privado y fueron eliminados;
las cabeceras del informe compartible están redactadas.

El backend de validación queda vinculado a `127.0.0.1:18080`; para usarlo manualmente
ajustar baseUrl a `http://127.0.0.1:18080/api`. El entorno de ejemplo mantiene el
puerto convencional 8080. Elegir un UUID nuevo para repetir el alta; la base de
esta sesión ya contiene el caso técnico creado. Postman sigue pendiente de
interacción manual e importación efectiva, sin respuestas o capturas inventadas.

El informe nativo de Bruno puede contener Authorization: debe depurarse antes
de compartirlo. En esta ejecución se reemplazaron los tokens por [REDACTED] en
una copia y se eliminaron el entorno y el informe temporal privados.
