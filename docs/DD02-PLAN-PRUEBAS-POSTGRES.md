# DD-02 — Plan de pruebas PostgreSQL

**Estado:** Preparación — ejecutar solo cuando exista script versionado aprobado y autorización de entorno de prueba  
**Herramientas previstas:** JUnit, Testcontainers (`postgres:16.15`), GreenMail (capa M7; fuera del SQL puro)  
**No aplica aún:** producción, flag estudiantil true, sustitución del denying

Alineado a DD02-01…14 del plan de implementación y a la propuesta SQL.

---

## 1. Integridad referencial y constraints

| ID | Caso | Esperado |
| --- | --- | --- |
| PG-01 | INSERT credencial con `idUsuario` inexistente | Rechazo FK |
| PG-02 | INSERT credencial `fechaExpiracion <= fechaCreacion` | Rechazo CHECK |
| PG-03 | INSERT credencial `intentosFallidos = 6` | Rechazo CHECK |
| PG-04 | Dos filas mismo `challengeId` | Rechazo UNIQUE |
| PG-05 | Dos Outbox misma `claveIdempotencia` | Rechazo UNIQUE |
| PG-06 | Sesión sin Estudiante válido | Rechazo FK |
| PG-07 | Intento sin credencial | Rechazo FK |

---

## 2. Caducidad y políticas temporales

| ID | Caso | Esperado |
| --- | --- | --- |
| PG-10 | Credencial con `fechaExpiracion` en pasado | Validación de app/SQL de consumo no actualiza fila |
| PG-11 | Reenvío antes de `fechaProximoReenvioPermitido` | Negocio rechaza; BD sin nueva credencial activa |
| PG-12 | Sesión con `fechaExpiracion` vencida | `StudentAccessPort` deniega (cuando exista adaptador) |
| PG-13 | Inactividad &gt; 30 min | Negocio trata sesión como inválida |

---

## 3. Concurrencia y atomicidad

| ID | Caso | Esperado |
| --- | --- | --- |
| PG-20 | Dos TX validan mismo OTP simultáneamente | Una sola `fechaConsumo` no nula (DD02-13) |
| PG-21 | TX: credencial OK + fallo INSERT Outbox | Rollback completo; 0 filas (DD02-12a) |
| PG-22 | Commit credencial+Outbox; SMTP falla después | Filas permanecen; estado Outbox reintentable (DD02-12b) |
| PG-23 | Dos encolados misma `claveIdempotencia` | Segundo falla UNIQUE |

Usar `PlatformTransactionManager` único del monolito / Testcontainers.

---

## 4. Seguridad de datos en BD

| ID | Caso | Esperado |
| --- | --- | --- |
| PG-30 | Tras solicitar OTP | No existe columna/texto con OTP en claro en Credencial ni Intento |
| PG-31 | Outbox | Solo `payloadCifrado` + `nonce`; logs de prueba sin OTP |
| PG-32 | Sesión | Solo HMAC/hash de token; cookie no persistida en claro |

---

## 5. Rollback y recuperación compatible con Flyway

### 5.1 Qué no hacer

- Ejecutar DROP manual en bases compartidas “para probar”.
- `flyway clean` en entornos no efímeros.
- Asumir que un script `DOWN` suelto es política de producción.
- `repair` sin evidencia de checksum/historial corrupto y sin autorización.

### 5.2 Entornos efímeros (Testcontainers)

1. Migrar baseline V001–V009 + script DD-02 propuesto (cuando exista como recurso de test o tras aprobación 2).
2. Ejecutar suite PG-*.
3. Destruir contenedor (rollback = descarte del volumen).

### 5.3 Entornos persistentes (solo tras aprobación 3)

| Situación | Acción documentada |
| --- | --- |
| Migración DD-02 aplicada con error de negocio detectado pronto | Respaldo previo obligatorio; migración **correctiva** versionada siguiente (no editar el script ya aplicado); o restore desde backup según runbook |
| Checksum Flyway distinto tras editar histórico | **Prohibido** editar V001–V009 ni el script ya aplicado; usar correctiva |
| Necesidad de retirar tablas DD-02 | Nueva versión `V0NN__dd02_retirar_…` con DROP ordenado (FKs primero), aprobada explícitamente — no “SQL inverso” informal |
| Historial con V010 M4 fantasma | Conciliar con [`DD02-INVENTARIO-FLYWAY.md`](DD02-INVENTARIO-FLYWAY.md) antes de cualquier correctiva |

### 5.3 Orden de DROP en correctiva de retiro (si alguna vez se aprueba)

1. `"IntentoAccesoEstudiante"`
2. `"EntregaCodigoAcceso"`
3. `"SesionEstudiante"`
4. `"CredencialAccesoEstudiante"`

---

## 6. Trazabilidad a escenarios DD02 funcionales

| Escenario funcional | Pruebas PG de apoyo |
| --- | --- |
| DD02-01 / 02 | Integridad + ausencia de filtración (app) |
| DD02-03–08 | Caducidad, intentos, consumo |
| DD02-12a / 12b | PG-21 / PG-22 |
| DD02-13 | PG-20 |
| DD02-10 / 11 | PG-12 / 13 |

---

## 7. Criterio de cierre de este plan de pruebas

Documento revisado por Dirección junto con diccionario, ER y SQL propuesto.  
**No** implica ejecución de migrate ni activación estudiantil.
