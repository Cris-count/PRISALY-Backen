# Secretos locales (nunca se versionan)

`ACTUALIZAR-PRISALY.ps1` los genera en la carpeta de ejecución (`%LOCALAPPDATA%\prisaly-alineacion\secretos` por defecto), no aquí.

| Archivo | Contenido | Generado |
|---|---|---|
| `postgres-password` | Contraseña de PostgreSQL local | Aleatorio, 32 bytes |
| `codigo.key`, `sesion.key` | `kid:base64` (≥ 32 bytes) para K-COD y K-SES (DD-02 B) | Aleatorio |
| `outbox.key` | `kid:base64` (32 bytes) K-OUT, AES-GCM del outbox M7 | Aleatorio |
| `admin-bootstrap` | Contraseña inicial del administrador `administrador.dc106@prisaly.test` (solo primer arranque con base vacía) | Aleatorio |
| `qa-admin.txt` | Contraseña del administrador para «Acceso rápido QA» (copia de `admin-bootstrap`) | Copia |
| `qa-docente.txt` | Contraseña de `docente.demo@prisaly.test` para «Acceso rápido QA». Vacío ⇒ el botón responde 503 hasta que se cree la cuenta (alta + activación D-05) y se escriba aquí su contraseña | Vacío |
