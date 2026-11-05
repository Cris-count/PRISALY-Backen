# Postman — PRISALY Integrante 1 (espejo de Bruno)

Misma secuencia, IDs y contratos que `bruno/` (carpetas `00`–`05`).  
Los tokens e identificadores capturados viven en variables de colección (`accessToken`, `noPermAccessToken`, `sesionId`, `convocatoriaId`). Evita duplicarlos en el entorno o en Globals. No exportes la colección de Desktop después de ejecutar las pruebas con tokens.

Newman no es evidencia del entregable: ejecutar en **Postman Desktop**.

## Importar

1. Import → `PRISALY-Integrante-1.postman_collection.json`
2. Import → `PRISALY-Integrante-1.postman_environment.json`
3. Seleccionar entorno **PRISALY Integrante 1 local**
4. En **Current value** (no Initial) de `personnelPassword`, `personnelNoPermPassword` e `inactivePassword`, poner las claves locales. No exportar el entorno después de rellenarlo.

Verifica que `baseUrl` resuelva a `http://localhost:8080/api`. La versión actualizada se importa desde los JSON de arriba. Si ya existe una copia en Desktop, identifica la nueva por los scripts de **Pre-request** antes de ejecutar.

Si falta una variable requerida, el script omite la solicitud e indica su nombre en la consola de Postman. Registra el caso como bloqueado. Las contraseñas del JSON se entregan vacías.

## Orden (igual que Bruno)

1. `00-verificacion-sistema`
2. `01-m1-autenticacion` — solo `01-01` guarda `accessToken`
3. `02-m1-identidad-permisos`
4. `03-m1-acceso-estudiantil`
5. `04-m7-integracion-m4` — `04-02` guarda `sesionId`; `04-06` exige ese valor (si Postman muestra `{{sesionId}}` sin resolver, no aprobar el 401)
6. `05-m1-finalizacion` — logout **después** de M7

Collection Runner: una carpeta a la vez, **no paralelo**. Ejecuta manualmente `04-02` y `04-03` después de confirmar que el caso y el estudiante del entorno existen y son datos de prueba. Estas solicitudes crean una sesión y una convocatoria. `04-04` comprueba que la convocatoria recién creada aparece en la lista de M4.

## Regenerar el JSON

```powershell
node .\postman\build_collection.mjs
```

No escribe secretos.

## Seeds y backend

Los mismos de Bruno (`bruno/scripts/apply-m1-bruno-seed.ps1`, `apply-m7-bruno-seed.ps1`) si faltan datos. JAR Docker en `:8080`. M7 no tiene REST: convocatoria vía M4. HTTP 201 ≠ SMTP. DD-02 off → `03-01` = 501.
