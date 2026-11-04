# Bruno — PRISALY (Integrante 1: M1 + M7)

Colección: esta carpeta (`bruno.json`, nombre **PRISALY**). Bruno Desktop / CLI 4.3.

M7 **no tiene REST propio**. Las notificaciones se ejercitan por M4 (`/m4/sesiones` y convocatorias) → `NotificacionPort` → Aviso + `EnvioCorreo` **Pendiente**. Worker SMTP y DD-02 permanecen deshabilitados.

Inventario y matriz: [`INVENTARIO-M1-M7.md`](INVENTARIO-M1-M7.md), [`MATRIZ-RESULTADOS.md`](MATRIZ-RESULTADOS.md).

## 1. Iniciar el backend

PostgreSQL Compose saludable. JAR en Docker (receta del README raíz / `docs/M1-JAR-BOOT-EVIDENCE.md`). Comprobar `GET http://localhost:8080/api/health` → 200.

Maven/JUnit **no** son evidencia de este entregable; solo el arranque autorizado del JAR.

## 2. Abrir la colección

Bruno Desktop → Open Collection → `PRISALY-Backend/bruno`.

## 3. Entorno

Seleccionar **`local.secrets`** (gitignored).

| Entorno | Contraseñas | Efecto |
| --- | --- | --- |
| `local` | vacías (versionado) | `01-01` → 400 `VALIDATION_ERROR` |
| `local.secrets` | locales, no Git | autenticación real |

`accessToken`, `noPermAccessToken` y `sesionId` **no** están en los `.bru` de entorno: se asignan con `bru.setVar` en ejecución. Así `local` vacío no pisa un token de sesión.

## 4. Credenciales locales

```powershell
copy bruno\environments\local.secrets.bru.example bruno\environments\local.secrets.bru
```

No commitear `local.secrets.bru`. No pegar tokens ni contraseñas en capturas.

Seeds **solo si faltan datos** (comprobar docente activo / caso M7 antes):

```powershell
powershell -File bruno\scripts\apply-m1-bruno-seed.ps1
powershell -File bruno\scripts\apply-m7-bruno-seed.ps1
```

## 5. Qué ejecutar primero

Carpeta **`00-verificacion-sistema`**. Si health falla, no continuar.

## 6. Modo A — solicitud individual

Clic en la petición → Send. Lee **Docs** (precondiciones). Ejemplos:

- `02-01` necesita `01-01` (token).
- `04-03` necesita `04-02` (`sesionId`).
- `05-02` necesita `05-01`.
- Negativos de auth (`01-02`…`01-05`) no requieren token y **no** lo pisan.

## 7. Modo B — flujo secuencial

En Bruno Desktop: Run folder / Run collection **en orden de carpetas 00 → 05**. No paralelizar carpetas dependientes.

Orden:

1. `00-verificacion-sistema`
2. `01-m1-autenticacion` (01-01 guarda `accessToken`; el resto no lo toca)
3. `02-m1-identidad-permisos`
4. `03-m1-acceso-estudiantil` (no usa token de personal)
5. `04-m7-integracion-m4` (vuelve a autenticar al docente; `05` cierra sesión **después**)
6. `05-m1-finalizacion`

No ejecutar `05` antes de `02`/`04`.

CLI (no es evidencia docente; útil para humo local):

```powershell
cd PRISALY-Backend\bruno
bru run 00-verificacion-sistema --env local.secrets
```

## 8. Códigos HTTP esperados

| Código | Significado en esta colección |
| --- | --- |
| 200 | Health, `/me`, permiso `CREAR_CASO`, listado M4, login auxiliar |
| 201 | Sesión o convocatoria M4 (no implica SMTP) |
| 204 | Logout |
| 400 | Validación (`VALIDATION_ERROR`) |
| 401 | `AUTH_FAILED` o `UNAUTHENTICATED` |
| 403 | `FORBIDDEN` (M1) o `M4-SIN-PERMISO` |
| 501 | DD-02 off (`ESTUDIANTE_ACCESS_PENDING_CONTRACT`) |

## 9. Registrar resultados

Completar [`MATRIZ-RESULTADOS.md`](MATRIZ-RESULTADOS.md): observado, coincidencia, veredicto, fecha, SHA `git rev-parse HEAD`. Sin ejecución auténtica el veredicto es **No ejecutada**.

## 10. Evidencias sin secretos

Capturas: recortar password y token. Nombres sugeridos (el archivo solo existe si hay captura real):

- `M1-POST-01_AuthCorrecta_200.png`
- `M1-POST-02_ClaveIncorrecta_401.png`
- `M7-via-M4-01_Convocatoria_201.png`

## Carpetas

| Carpeta | Contenido |
| --- | --- |
| `00-verificacion-sistema` | Health |
| `01-m1-autenticacion` | Login + negativos |
| `02-m1-identidad-permisos` | `/me`, `CREAR_CASO`, token inválido |
| `03-m1-acceso-estudiantil` | `/validar` 501 y 400 |
| `04-m7-integracion-m4` | Sesión, convocar, listar, 403, 401 |
| `05-m1-finalizacion` | Logout + token revocado |

## Flujo M7 (detalle)

1. `04-01` Bearer con `GESTIONAR_SESION` (seed M7).
2. `04-02` `POST /m4/sesiones` → `sesionId`.
3. `04-03` convocar → M7 encola Pendiente.
4. `04-04` listar.
5. `04-05-prep` + `04-05` sin permiso → 403.
6. `04-06` sin token → 401.

SQL opcional (no REST M7):

```sql
SELECT "tipo","claveEvento" FROM "Aviso" ORDER BY "fechaCreacion" DESC LIMIT 5;
SELECT "estadoEnvio","correoDestino","claveIdempotencia" FROM "EnvioCorreo" ORDER BY "fechaCreacion" DESC LIMIT 5;
```

## UUIDs seed M7

| Variable | Valor |
| --- | --- |
| `casoIdBruno` | `33333333-3333-3333-3333-333333333301` |
| `estudianteIdBruno` | `22222222-2222-2222-2222-222222222201` |
| Docente | `docente.bruno@prisaly.local` (clave solo en `local.secrets.bru`) |

## Token provisional

`TD-M1-AUTH-PROVISIONAL`: Bearer opaco en memoria. Se pierde al reiniciar Spring. Ver `m1/docs/TD-M1-AUTH-PROVISIONAL.md`.
