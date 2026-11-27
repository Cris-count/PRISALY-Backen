# PRISALY en Railway — Staging (DC-DEPLOY-01)

Procedimiento reproducible para desplegar Frontend, Backend y PostgreSQL en un proyecto Railway.
**Sin secretos:** aquí solo aparecen nombres de variables. Los valores se escriben en Railway como variables
(selladas cuando son secretos) y nunca en Git, Dockerfiles, `railway.json`, logs ni capturas.

## Arquitectura

```
Navegador ──HTTPS──▶ frontend (nginx, dominio *.up.railway.app)
                        │  /api/*  (misma URL de origen; red privada cifrada de Railway)
                        ▼
                     backend (Spring Boot, sin dominio público) ──▶ Postgres (sin TCP proxy)
MUNDO-24 (Unreal + PS2) ── fuera de Railway: equipo o servidor con GPU (Railway no ofrece GPU ni UDP público).
```

- El navegador solo conoce el dominio del Frontend. El Backend y PostgreSQL no se exponen.
- Ramas: `deploy/railway-staging` en PRISALY-Front y PRISALY-Backen. Railway despliega solo esa rama.
- Configuración como código: `Dockerfile.railway` en cada repositorio y `deploy/railway/configurar-servicios.mjs`, que guarda en cada servicio de Railway (API pública) el Dockerfile, el healthcheck, la política de reinicio y el sueño. Sustituye a `railway.json`, que Railway deja de leer el 01/12/2026.

## Servicios y variables

### Postgres (plantilla oficial de Railway)
Sin TCP proxy. Activar copias de seguridad programadas del volumen (pestaña *Backups*).

### backend (repo PRISALY-Backen, rama `deploy/railway-staging`)
Sin dominio público. Healthcheck `GET /api/health` (aplicado por `configurar-servicios.mjs`).

| Variable | Valor en Railway | Nota |
|---|---|---|
| `PORT` | `8080` | Fijo para que el Frontend pueda referenciarlo |
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | Spring espera JDBC, no el `DATABASE_URL` de Railway |
| `DATABASE_USER` | Fase 1: `${{Postgres.PGUSER}}` · Fase 2: `prisaly_app` | |
| `DATABASE_PASSWORD` | Fase 1: `${{Postgres.PGPASSWORD}}` · Fase 2: variable sellada | |
| `SPRING_FLYWAY_ENABLED` | Fase 1: `true` · Fase 2: `false` | Solo `db/migration` oficial |
| `CORS_ORIGINS` | `https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}` | Origen único, sin comodines |
| `PRISALY_M7_ZONA_HORARIA` | `America/Bogota` | Opcional (OBS-83-M7-02) |
| `PRISALY_INICIALIZACION_ADMIN_CORREO` / `_NOMBRE` | correo y nombre del administrador | Solo durante la inicialización |
| `PRISALY_ADMIN_CREDENCIAL_INICIAL` | **sellada, la escribe el operador** | Solo para el primer arranque; después se elimina |

Los flags académicos se quedan como en `application.yml` (apagados). Encender cualquiera (`student-access`,
activación docente, alta de estudiantes, M6…) y sus migraciones candidatas requiere autorización expresa para
Internet: la autorización de QA local (DC-OPS-85) no se extiende a este entorno.

### frontend (repo PRISALY-Front, rama `deploy/railway-staging`)
Dominio público generado por Railway. Healthcheck `GET /salud`.

| Variable | Valor en Railway |
|---|---|
| `BACKEND_INTERNO` | `http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:${{backend.PORT}}` |

## Orden de despliegue

1. **Postgres:** crear desde la plantilla. Activar backups.
2. **backend, fase 1 (migración):** variables de fase 1. Desplegar y esperar al healthcheck verde. Flyway migra con el
   usuario propietario.
3. **PD-01 y rol de aplicación:** con `railway connect Postgres` (o `railway ssh` al servicio Postgres):
   - `\i docs/provision/pd01-inicializacion.sql` y `\i docs/provision/pd01-p78-rol-estudiante.sql` (oficiales, idempotentes);
   - `psql … -v clave_app=… -f deploy/railway/rol-aplicacion.sql` (la contraseña la genera el operador).
4. **backend, fase 2:** `DATABASE_USER=prisaly_app`, `DATABASE_PASSWORD` sellada, `SPRING_FLYWAY_ENABLED=false`.
5. **Primer administrador:** el operador define `PRISALY_ADMIN_CREDENCIAL_INICIAL` (sellada), `…_CORREO` y `…_NOMBRE`,
   redepliega, comprueba el acceso y **elimina las tres variables** y redepliega. `arranque-backend.sh` vuelca la
   credencial a un archivo 0600 temporal que se borra tras el arranque; nunca se imprime.
6. **frontend:** variable `BACKEND_INTERNO`, desplegar y generar dominio (*Settings → Networking → Generate Domain*).
7. Volver al backend y comprobar que `CORS_ORIGINS` resuelve al dominio generado (redeploy si se creó después).

## Migraciones posteriores (DC-DEPLOY-04)

Sin tocar `DATABASE_USER`/`DATABASE_PASSWORD` (la app sigue con `prisaly_app`):

1. Backup: `railway ssh --service Postgres -- sh -c 'pg_dump -U "$PGUSER" -d "$PGDATABASE" --no-owner' > respaldo.sql`.
2. Variables: `SPRING_FLYWAY_ENABLED=true`, `SPRING_FLYWAY_USER=${{Postgres.PGUSER}}`,
   `SPRING_FLYWAY_PASSWORD=${{Postgres.PGPASSWORD}}`, `SPRING_FLYWAY_LOCATIONS=<ubicaciones del contrato>` y
   `PRISALY_M4_SCHEMACONTRACT=<contrato>`. Redesplegar: Flyway migra con el propietario.
3. El primer arranque falla con `M4-ESQUEMA-PENDIENTE … faltantes=[…]` aunque las tablas existan:
   `information_schema.tables` solo muestra a `prisaly_app` las tablas sobre las que tiene privilegios. Aplicar por SSH
   los dos `GRANT … ON ALL TABLES/SEQUENCES IN SCHEMA public TO prisaly_app` de `rol-aplicacion.sql`.
4. `SPRING_FLYWAY_ENABLED=false`, borrar `SPRING_FLYWAY_USER`/`_PASSWORD` y `railway redeploy` (borrar variables no
   redespliega).

Staging, 27/11/2026: contrato `bd0052-v013-integral-dc92` (`db/migration`, `dd02b-propuesta`, `m6-propuesta`,
`d05-propuesta`, `dc92-propuesta`; historial 001–009, 013, 014–016, 019–023). V017/V018 no aplicadas: ningún contrato
las combina con V020.

## Rollback y recuperación

- **Aplicación:** *Deployments → … → Rollback* a la imagen anterior (retención: 72 h en Hobby, 120 h en Pro); pasado
  ese plazo, *Redeploy* del SHA anterior de `deploy/railway-staging`.
- **Datos:** revertir la aplicación **no** revierte migraciones ni datos. Antes de cualquier migración nueva: backup
  manual del volumen de Postgres y comprobar que la versión anterior de la aplicación es compatible con el esquema.
  Restaurar: pestaña *Backups* del servicio Postgres → *Restore* → revisar el cambio preparado → *Deploy*.
- **Registro:** anotar en el Plan Maestro el SHA desplegado de cada servicio.

## Límites conocidos (Staging)

- **SMTP:** Railway solo lo permite en el plan Pro. M7 usa SMTP (`spring.mail`); en Hobby no hay correo saliente.
- **DM1-06:** el límite de intentos de acceso por origen usa la IP TCP. Detrás del proxy de Railway y de nginx todos los
  usuarios comparten esa IP. Pendiente de decisión antes de abrir el entorno a más usuarios.
- **Auditoría PD-01:** `logs/auditoria` vive en el sistema de archivos efímero del contenedor; sin volumen se pierde al
  redesplegar.
- **MUNDO-24:** fuera de Railway. Producción del Front no publica una sesión 3D fija (`simuladorUnrealLocal: false`).
