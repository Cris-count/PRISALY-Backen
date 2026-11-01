# M7 — Cómo ejecutar y documentar pruebas

Rama de trabajo: `feature/m7-notificaciones`.

## Comandos (Git Bash)

```bash
./mvnw -B -pl m7 test
./mvnw -B -pl m7 -Pdocker-it verify
```

## Comandos (PowerShell)

```powershell
.\mvnw.cmd -B -pl m7 test
.\mvnw.cmd -B -pl m7 -Pdocker-it verify
```

En Git Bash **no** uses `.\mvnw.cmd` (falla con `command not found`). Usa `./mvnw` o `./mvnw.cmd`.

## Alcance del reactor

`-pl app -am -Pdocker-it verify` construye/prueba el reactor completo (m1…m7, app). Si falla **otro** módulo (p. ej. M4 por pull de Docker Hub), M7 queda `SKIPPED` sin haberse ejecutado.

Para evidencia de **M7**, preferir `-pl m7` (y `-am` solo si hace falta compilar dependencias directas).

## Fallos de entorno vs dominio

Si Testcontainers/Docker no puede bajar `postgres:16-alpine` (`lookup …docker.com: no such host`), es bloqueo de **red/DNS/Docker Desktop**, no un fallo de lógica M7. Documentarlo como entorno.

## Evidencia automática (consola)

Git Bash:

```bash
mkdir -p docs/evidence
./mvnw -B -pl m7 test 2>&1 | tee "docs/evidence/m7-test-$(date +%Y%m%d-%H%M%S).log"
```

PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path docs\evidence | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
.\mvnw.cmd -B -pl m7 test 2>&1 | Tee-Object -FilePath "docs\evidence\m7-test-$stamp.log"
```

Informes Surefire (si hay fallos): `m7/target/surefire-reports/`.

**No** commits de logs con secretos. Añade `docs/evidence/*.log` a `.gitignore` si se generan en local de forma habitual.

## Plantilla de nota breve

```md
## Evidencia M7 — AAAA-MM-DD
- Rama: feature/m7-notificaciones
- Comando: ./mvnw -B -pl m7 test
- Resultado: BUILD SUCCESS | FAILURE
- Causa (si falla): …
- Notas: unitarias / docker-it / entorno Docker
```

## Relacionado

- Reconocimiento: [`M7-RECONOCIMIENTO.md`](M7-RECONOCIMIENTO.md)
- Fase 1 (persistencia/idempotencia): [`M7-FASE1.md`](M7-FASE1.md)
- Contrato M4 → M7: `NotificacionPort.notificarConvocatoria` (Fase 2 productiva; sin simular entrega SMTP)
