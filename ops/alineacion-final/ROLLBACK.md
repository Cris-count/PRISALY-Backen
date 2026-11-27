# Rollback

## Entorno de Nicolás (proyecto `prisaly-alineacion`, creado por ACTUALIZAR-PRISALY.ps1)

Detener sin perder datos:

```powershell
powershell -ExecutionPolicy Bypass -File ACTUALIZAR-PRISALY.ps1 -Backend <ruta> -Front <ruta> -Detener
```

Este proyecto usa volúmenes propios (`prisaly-alineacion_pg_data`, `prisaly-alineacion_audit_data`). Su entorno anterior y sus ramas no se modifican, así que basta con volver a arrancar su entorno habitual. Los worktrees (`%LOCALAPPDATA%\prisaly-alineacion\backend|frontend`) se retiran con `git worktree remove <ruta>` cuando ya no se necesiten.

## Entorno QA de Ingrid (`prisaly-dc105-local`)

Volver al estado anterior a DC-ALINEACION-INTEGRAL-01: listado M3 apagado y el Frontend tal como estaba, con sus archivos copiados a mano. Los comandos exactos están en `PRISALY-ENTREGA\DC-ALINEACION-INTEGRAL-01\ROLLBACK.txt`.

Imágenes de respaldo:
- `prisaly-cierre-backend:respaldo-pre-alineacion`
- `prisaly-cierre-frontend:respaldo-pre-alineacion`
- `prisaly-cierre-frontend:respaldo-contenedor-pre-alineacion`

En ambos casos: nunca `down -v`, `flyway repair` ni `flyway clean`.
