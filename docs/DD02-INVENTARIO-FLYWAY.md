# DD-02 — Inventario Flyway y conciliación de numeración

**Fecha:** 2026-11-02  
**Rama de trabajo:** `feature/m1-m7-estabilizacion`  
**HEAD:** `d85bf85`  
**Alcance:** Solo evidencia documental. **Sin** archivo Flyway definitivo en app. **Sin** `migrate`.

Referencia gate local: [`DD02-V010-AUDITORIA.md`](DD02-V010-AUDITORIA.md).

---

## 1. Inventario en `origin/develop`

Migraciones canónicas del monolito (`app/src/main/resources/db/migration/`):

| Script | Rol |
| --- | --- |
| V001__bd0052_estructura.sql | Esquema BD-005.2 |
| V002__bd0052_restricciones.sql | Índices / checks |
| V003__bd0052_integridad.sql | Integridad |
| V004__bd0052_integridad_intentos.sql | Intentos |
| V005__bd0052_cierre_integridad.sql | Cierre integridad |
| V006__bd0052_proteccion_relaciones.sql | Protección relaciones |
| V007__fase3_politicas_aprobadas.sql | Políticas F3 |
| V008__bloqueo_caso_al_reasignar_sesion.sql | Bloqueo caso |
| V009__rf09_solo_casos_publicados.sql | RF09 |

También en develop (módulo M4, **no** canónico de app):

| Recurso | Ruta |
| --- | --- |
| V010 propuesta M4 | `m4/src/main/resources/db/migration/V010__m4_sesiones_convocatorias.sql` |

Empaque en develop (`m4/pom.xml`): exclude de `db/migration/V010…` + copia a `db/m4-propuesta/`.

---

## 2. Inventario en `feature/m1-m7-estabilizacion` (HEAD)

Respecto a `origin/develop`, **no hay diff** en:

- `app/src/main/resources/db/migration/**`
- `m4/src/main/resources/db/**`

Conclusión: la rama DD-02 **no** añade migraciones. Pendiente de commit solo documentación DD-02 en working tree.

---

## 3. Situación contractual de V010 (M4)

| Aspecto | Hecho |
| --- | --- |
| Contenido | Tablas paralelas `sesiones_academicas` / `convocatorias` (propuesta previa a BD-005.2 en app) |
| Comentario en script | “PROPUESTO”; no modifica V001–V009 |
| Ejecución automática monolito | **No** (empaque) |
| ¿Reservada semánticamente a M4? | **Sí como artefacto histórico/propuesta** — el número **10** está ocupado en el árbol Git de M4 aunque no aplique Flyway de app |
| ¿Libre para DD-02 en app? | **No se puede afirmar** solo porque no esté en el historial local |

**Precisión Dirección:** no ejecutarse automáticamente ≠ número libre para reutilizar.

---

## 4. JAR M4 — ubicación efectiva

Inspección `m4/target/m4-0.0.1-SNAPSHOT.jar`:

```text
db/
db/m4-propuesta/
db/m4-propuesta/V010__m4_sesiones_convocatorias.sql
```

No existe `db/migration/V010…` dentro del JAR. Flyway default `classpath:db/migration` del monolito **no** debe descubrir este recurso.

---

## 5. Historiales Flyway accesibles

### 5.1 Local — `prisaly-postgres`

```sql
SELECT version, description, script, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

Resultado: versiones **001–009** todas `success = t`. **Sin** versión 10 ni 11.

### 5.2 Integración / M3–M6 compartido / demos

| Entorno | Host/BD | v10 | v11 | Resultado |
| --- | --- | --- | --- | --- |
| Local | prisaly-postgres | No | No | PASS |
| Integración | *pendiente equipo* | ? | ? | Abierto |
| M3–M6 compartido | *pendiente equipo* | ? | ? | Abierto |
| Demos | *pendiente equipo* | ? | ? | Abierto |

Sin contraseñas en este informe. Completar cuando Dirección/equipo indiquen host y nombre de base.

---

## 6. Versiones disponibles y reservadas (repo)

| Versión | Estado en repo | Uso Flyway app |
| --- | --- | --- |
| 001–009 | Canónicas en app; en develop y rama DD-02 | Aplicadas (local) |
| 010 | Archivo en M4; empaquetado `db/m4-propuesta/` | No descubierta; **número ocupado en Git M4** |
| 011+ | No existen archivos en app ni M4 | Candidatas **hipotéticas** para DD-02 |

---

## 7. Justificación de numeración (propuesta, no definitiva)

| Opción | Pros | Contras | Recomendación provisional |
| --- | --- | --- | --- |
| Reutilizar **V010** en app para DD-02 | Siguiente entero tras 009 en historial local | Colisión de nombre/número con artefacto M4; riesgo si alguna base aplicó o espera el V010 M4 | **Desaconsejada** hasta acta que libere el número 10 |
| **V011** (u otra >10) en app | Evita colisión de script con M4; alineada a historial canónico V001–V009 | Requiere confirmar que ninguna base tenga ya v11 | **Candidata preferente** tras conciliación compartida |
| Renombrar/archivar solo docs | — | No elimina el archivo M4 existente | Fuera de alcance M1 sin OK M4/Dirección |

**Estado:** numeración **condicionada**. Este inventario **no** autoriza crear `V010` ni `V011` en `app/.../db/migration`.

---

## 8. Tres aprobaciones (recordatorio)

1. Diseño de persistencia (diccionario + ER).  
2. Versión + SQL definitivo (este inventario + propuesta).  
3. Ejecución Flyway.

---

**Cierre línea A (repo + local):** inventario completo. **Pendiente:** filas de bases compartidas y decisión formal de número.
