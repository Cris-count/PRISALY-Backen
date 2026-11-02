# DD-02 — Auditoría V010 / disponibilidad de migración

**Fecha:** 2026-11-02  
**Responsable:** Integrante 1 (M1 / M7)  
**Alcance:** Solo lectura — sin DDL, sin V011, sin código productivo  
**Gate local:** **APROBADO**  
**Bases compartidas:** **Pendientes de auditoría** (bloquean autorización de V011)

---

## 1. Rama y HEAD inspeccionados

| Campo | Valor |
| --- | --- |
| Rama | `feature/m1-m7-estabilizacion` |
| **HEAD efectivo** | `d85bf851e6e78e9b1a81cb664c7ffc924de927a7` |
| Tip mensaje | `Merge branch 'develop' into feature/m1-m7-estabilizacion` |
| Ancestro citado en contextos antiguos | `52ce48c` (smoke IT estabilización) — **no** es el tip |
| Plan DD-02 en árbol | [`DD02-PLAN-IMPLEMENTACION-M1-M7.md`](DD02-PLAN-IMPLEMENTACION-M1-M7.md) — **Versión 1.1** |

### Matriz Paso 1.5 (auditoría compartida — abierta)

| Entorno | Host/BD | Versión 10 | Versión 11 | Resultado |
| --- | --- | --- | --- | --- |
| Local | prisaly-postgres | No | No | **PASS** |
| Integración | Pendiente | Pendiente | Pendiente | Abierto |
| M3–M6 compartido | Pendiente | Pendiente | Pendiente | Abierto |
| Demos | Pendiente | Pendiente | Pendiente | Abierto |

Sin contraseñas ni secretos en este informe. V011 permanece bloqueada hasta cerrar las filas abiertas.

---

## 2. Ubicación de V001–V009 (canónico del monolito)

Directorio Flyway de **app** (fuente):

`app/src/main/resources/db/migration/`

| Script |
| --- |
| `V001__bd0052_estructura.sql` |
| `V002__bd0052_restricciones.sql` |
| `V003__bd0052_integridad.sql` |
| `V004__bd0052_integridad_intentos.sql` |
| `V005__bd0052_cierre_integridad.sql` |
| `V006__bd0052_proteccion_relaciones.sql` |
| `V007__fase3_politicas_aprobadas.sql` |
| `V008__bloqueo_caso_al_reasignar_sesion.sql` |
| `V009__rf09_solo_casos_publicados.sql` |

No existe `V010` ni `V011` en este directorio.

---

## 3. Ubicación y exclusión de V010 de M4

| Aspecto | Evidencia |
| --- | --- |
| Fuente en árbol | `m4/src/main/resources/db/migration/V010__m4_sesiones_convocatorias.sql` |
| Contenido | Propuesta histórica de tablas paralelas (`sesiones_academicas`, `convocatorias`); **no** es DDL DD-02 |
| Empaque `m4/pom.xml` | `exclude` de `db/migration/V010__…` en resources normales; segunda resource la copia a `db/m4-propuesta/` |
| Clases compiladas | `m4/target/classes/db/migration` **ausente**; sí existe `m4/target/classes/db/m4-propuesta/V010__…` |
| JAR `m4-0.0.1-SNAPSHOT.jar` | Solo `db/m4-propuesta/V010__m4_sesiones_convocatorias.sql` — **no** hay entrada `db/migration/V010…` |

**Precisión:** la exclusión de V010 del descubrimiento Flyway del monolito se logra por el **empaquetado de M4**, no porque app declare una ubicación Flyway exclusiva.

---

## 4. Configuración efectiva de Flyway en app

Archivo: `app/src/main/resources/application.yml`

```yaml
spring:
  flyway:
    enabled: true
```

- **No** hay `spring.flyway.locations` en app.
- Comportamiento esperado: default Spring Boot (`classpath:db/migration` sobre el classpath de la aplicación).
- Las referencias a `spring.flyway.locations` / `Flyway.configure` en el repo aparecen en **pruebas** (m1/m4/m5/m7), típicamente apuntando al baseline de `app/.../db/migration`.

Con el empaque actual de M4, V010 **no** vive bajo `db/migration` en el JAR dependiente; el default de Boot no debería aplicarla.  
**No** se afirma que app tenga location exclusiva configurada.

---

## 5. Consulta real de `flyway_schema_history` (local)

Entorno: contenedor Docker `prisaly-postgres` (rearrancado para la auditoría; estaba stopped).

```sql
SELECT installed_rank, version, description, script, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

| installed_rank | version | script | success |
| --- | --- | --- | --- |
| 1 | 001 | V001__bd0052_estructura.sql | t |
| 2 | 002 | V002__bd0052_restricciones.sql | t |
| 3 | 003 | V003__bd0052_integridad.sql | t |
| 4 | 004 | V004__bd0052_integridad_intentos.sql | t |
| 5 | 005 | V005__bd0052_cierre_integridad.sql | t |
| 6 | 006 | V006__bd0052_proteccion_relaciones.sql | t |
| 7 | 007 | V007__fase3_politicas_aprobadas.sql | t |
| 8 | 008 | V008__bloqueo_caso_al_reasignar_sesion.sql | t |
| 9 | 009 | V009__rf09_solo_casos_publicados.sql | t |

**9 filas.** No aparece versión `10`, ni script `V010%`, ni `V011%`.

---

## 6. Resultado de la auditoría local (Gate)

| Verificación | Dictamen |
| --- | --- |
| Rama / HEAD `d85bf85` | Conforme |
| V001–V009 en app | Presentes |
| V001–V009 en historial local | Aplicadas (`success = t`) |
| V010 M4 fuera de `db/migration` en JAR | Conforme (empaque) |
| Flyway app | Default Boot; **sin** location exclusiva |
| Historial local V010 | Ausente |
| V011 DD-02 | **No creada** (correcto) |
| Gate local | **APROBADO** |

---

## 7. Limitaciones de la auditoría

1. Solo se auditó el contenedor local `prisaly-postgres`.
2. **No** se consultaron bases compartidas de compañeros, CI persistente ni entornos de despliegue.
3. Un historial limpio en una máquina **no** garantiza que el número de migración esté libre en todos los entornos.
4. Si alguna base compartida aplicó alguna vez el V010 propuesto de M4 (u otra versión 10), hace falta conciliación de historial **antes** de publicar cualquier `V011` en app.
5. Esta auditoría **no** autoriza DDL ni implementación OTP/sesiones.

---

## 8. Decisión propuesta (actualizada tras resolución Dirección)

**Precisión:** que V010 M4 no se ejecute automáticamente **no** implica que el número 10 esté libre. La numeración definitiva (V010 reutilizada, V011 u otra) queda **condicionada** a `docs/DD02-INVENTARIO-FLYWAY.md` y a las tres aprobaciones (diseño / SQL+versión / ejecución).

| Tema | Estado |
| --- | --- |
| Candidato histórico V011 | Hipótesis de trabajo **no definitiva** |
| Reutilizar V010 | Solo si la conciliación demuestra que está libre y no reservada a M4 |
| V010 M4 en fuente/empaque | Intacta; fuera de `db/migration` en JAR |
| V001–V009 | Intactas |
| Archivo Flyway definitivo en app | **No autorizado** aún |
| Ejecución de migraciones | **No autorizada** |
| Activación estudiantil / denying | Sin cambio |

Preparación autorizada en paralelo: diccionario de datos, diseño, propuesta SQL y plan de pruebas PostgreSQL — sin ejecutar DDL.

### Secuencia acordada tras este documento

1. ~~Cerrar auditoría documental local~~ (este archivo).  
2. **Auditoría compartida (bloqueo actual):** inventariar y consultar solo-lectura:
   - PostgreSQL de integración.
   - PostgreSQL utilizado por M3/M4/M5/M6 (si aplica).
   - Bases compartidas de demos / pruebas conjuntas.  
   Consulta única por entorno:
   ```sql
   SELECT version, description, script, success
   FROM public.flyway_schema_history
   ORDER BY installed_rank;
   ```
   Sin migraciones ni modificación de datos.
3. Autorizar explícitamente V011 si no hay colisión.  
4. Persistencia DD-02 → OTP M1 → Outbox M7 → sesiones APP → pruebas DD02-01…14.  
5. `DenyingStudentAccessService` y `/validar` 501 hasta pruebas + acta de activación.

---

**Estado:** Gate local cerrado. Auditoría compartida abierta (bloquea V011). V011 no creada. Producción / auth estudiantil no habilitadas.
