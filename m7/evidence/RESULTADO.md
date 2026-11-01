# Verificación M7-INC-01

2026-11-01, 16:28:41 America/Bogota. **BUILD SUCCESS**, duración 3:35. Rama feature/m7-comunicaciones, base 2c96c912923c8e90ab1c74e3fcfd8aeb29265249, cambios locales sin commit.

Comando desde raíz del backend:

```powershell
.\mvnw.cmd -B verify -Pdocker-it '-Dm4.baseline.migrations=C:/Users/NICOLAS/OneDrive/Desktop/Prisaly/PRISALY-Backen/app/src/main/resources/db/migration'
```

| Módulo | Surefire | Failsafe | Total |
| --- | ---: | ---: | ---: |
| M1 | 8 | 6 | 14 |
| M3 | 89 | 38 | 127 |
| M4 | 107 | 42 | 149 |
| M5 | 126 | 19 | 145 |
| M7 | 10 | 12 | 22 |
| App | 1 | 9 | 10 |
| **Total** | **341** | **126** | **467** |

Cero fallos, errores y omitidas. M2 y M6 sin pruebas en este corte. Los 56 informes de suite incluidos en [suites.json](suites.json) se seleccionaron por LastWriteTime posterior o igual a la creación de m7-regression.log (16:25:04), excluyendo informes antiguos. No se incluyen propiedades de entorno ni credenciales en la evidencia versionable.

La suma de resúmenes Maven por módulo/runner coincide con los XML (467). Se conservaron las 445 pruebas de la línea base y se añadieron 22 de M7. Log completo local ignorado por Git: m7-regression.log. Informes de origen: `<modulo>/target/surefire-reports/TEST-*.xml` y `<modulo>/target/failsafe-reports/TEST-*.xml`.

M7 usa PostgreSQL 16.15 real, aplica V001–V009 y valida el mapeo Hibernate sin crear esquema. Sus IT cubren destinatario Usuario resuelto desde Estudiante, flush de Convocatoria guardada por M4, unicidad, FK, errores SQL reales, rollback local/exterior, idempotencia y dos variantes de concurrencia. App carga el conjunto de módulos y supera sus pruebas de seguridad HTTP existentes. La identidad permisiva del IT M7 es exclusivamente una fixture src/test; no sustituye M1 productivo.

Verificación Git final: `git diff --check` sin errores; `git diff --quiet HEAD -- app/src/main/resources/db/migration` y la comparación de M1/M3/M4/M5/M6/App/POM raíz sin diferencias. Sin migraciones nuevas ni cambios ajenos. No se realizaron commits, push o merge.

Pendientes de alcance: documentos originales V27/CU y ARQ-008.3, autenticación estudiantil DD-02, resultados DD-12, REST, correo y reportes. La semántica de registro interno de este incremento fue ratificada por el responsable en el chat.
