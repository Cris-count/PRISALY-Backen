# M7 — Reconocimiento técnico (Comunicaciones y Avisos)

Documento de reconocimiento **solo lectura**. No implementa SMTP, workers, entidades productivas ni migraciones. No inventa catálogos de dominio.

## 1. Referencias

| Ítem | Valor |
| --- | --- |
| Rama de este documento | `feature/m7-notificaciones` (base `origin/develop`) |
| Rama inspeccionada al reconocer | `feature/m1-identidad-acceso` @ `8cf8dbd` (merge de `develop`) |
| Referencia M1 Fase 3 (Bruno) | `4004516` (publicado en `origin/feature/m1-identidad-acceso`) |
| Ramas contrastadas (solo lectura) | `origin/feature/m4-programacion-convocatorias`, `origin/feature/m5-participacion-intentos`, `origin/feature/m6-evaluacion-calificaciones`, `origin/arquitectura` |
| Esquema | Flyway `V001` / `V002` (BD-005.2); **sin** cambios V001–V009 en este reconocimiento |

## 2. Estado real del módulo `m7` (rama actual)

Ruta: [`m7/`](../m7/)

| Elemento | Estado |
| --- | --- |
| `pom.xml` | Artefacto `m7`; sin dependencias propias (hereda del padre) |
| Código fuente | Solo [`package-info.java`](../m7/src/main/java/com/prisaly/m7/package-info.java): *“Notices and email. Skeleton without business logic.”* |
| Dominio / JPA / ports / REST | Ausentes |
| Tests | Ausentes |
| Ensamblaje | Incluido por el módulo `app` (JAR vacío de dominio) |

**Alcance de la afirmación “esqueleto”:** aplica al **módulo Maven `m7`**, verificado en la rama actual. No implica que M4/M5/M6 carezcan de implementación (ver §6).

## 3. Inventario de persistencia (`Aviso`, `EnvioCorreo`)

Fuente: [`V001__bd0052_estructura.sql`](../app/src/main/resources/db/migration/V001__bd0052_estructura.sql), [`V002__bd0052_restricciones.sql`](../app/src/main/resources/db/migration/V002__bd0052_restricciones.sql). V003–V009 no alteran estas tablas.

### 3.1 `Aviso`

| Columna | Tipo / notas |
| --- | --- |
| `idAviso` | `uuid` NOT NULL, PK |
| `idUsuarioDestino` | `uuid` NOT NULL → FK `Usuario.idUsuario` RESTRICT |
| `idParticipacion` | `uuid` NULL → FK `Participacion.idParticipacion` RESTRICT |
| `tipo` | `varchar(50)` NOT NULL — **sin CHECK de catálogo en V001–V009** |
| `contenido` | `text` NOT NULL |
| `claveEvento` | `varchar(150)` NOT NULL |
| `fechaCreacion` | `timestamptz` NOT NULL DEFAULT `now()` |
| `fechaLectura` | `timestamptz` NULL |

- Unique: `("idUsuarioDestino", "claveEvento")` — base natural de **idempotencia** de aviso por destinatario+evento.
- Sin índices adicionales dedicados en V002.

### 3.2 `EnvioCorreo`

| Columna | Tipo / notas |
| --- | --- |
| `idEnvioCorreo` | `uuid` NOT NULL, PK |
| `idUsuarioDestino` | `uuid` NOT NULL → FK `Usuario` RESTRICT |
| `idConvocatoria` | `uuid` NULL → FK `Convocatoria` RESTRICT |
| `correoDestino` | `varchar(255)` NOT NULL |
| `asunto` | `varchar(255)` NOT NULL |
| `contenido` | `text` NOT NULL |
| `estadoEnvio` | `varchar(25)` NOT NULL DEFAULT `'Pendiente'` — **sin CHECK de catálogo** (V002 lo deja explícitamente fuera) |
| `numeroIntentos` | `int` NOT NULL DEFAULT `0`; CHECK `>= 0` (V002) |
| `claveIdempotencia` | `varchar(150)` NOT NULL, UNIQUE |
| `ultimoError` | `text` NULL |
| `fechaCreacion` | `timestamptz` NOT NULL DEFAULT `now()` |
| `fechaUltimoIntento` | `timestamptz` NULL |
| `proximoReintento` | `timestamptz` NULL |
| `fechaEnvio` | `timestamptz` NULL |

- Índice operativo: `ix_envio_pendiente ("estadoEnvio", "proximoReintento")` (V002).

### 3.3 Encolado vs entrega (obligatorio)

| Hecho | Significado |
| --- | --- |
| INSERT / upsert en `EnvioCorreo` | **Encolado** (persistencia de intención de envío / cola) |
| Incremento de `numeroIntentos`, `fechaUltimoIntento`, `ultimoError` | **Intento de entrega** (aún no implementado) |
| Actualización tras respuesta del proveedor + `fechaEnvio` | **Confirmación o fallo de entrega** (aún no implementado) |

Crear una fila **no** demuestra que un correo haya sido entregado. El diseño futuro debe separar cola, intento, fallo y confirmación del proveedor. El literal por defecto `'Pendiente'` existe en el esquema; **otros valores de `estadoEnvio` no se enumeran aquí** hasta conciliación (V002: checks de estado sin catálogo ratificado).

## 4. Requisitos V27 / arquitectura de comunicaciones

| Fuente | Resultado |
| --- | --- |
| Repo local + `origin/arquitectura` (búsqueda de V27 / notificaciones) | **Catálogo V27 no localizado** como documento versionado con códigos asignables |
| Docs M5 (`M5-DOCUMENTO-TECNICO.md` en rama M5 / contenido mergeado) | Declaran explícitamente que el catálogo V27, CU y ARQ-008.2 **no están en el repositorio**; no asignan números sin esa evidencia |
| README / BD-005.2-REFERENCE | Enumeran tablas `Aviso` y `EnvioCorreo`; no definen API ni política SMTP |

**Conclusión:** requisitos funcionales detallados de notificaciones quedan **pendientes de fuente** (Dirección Central / catálogo V27). Este reconocimiento no inventa tipos de aviso, estados de correo ni políticas de reintento.

## 5. Contraste multi-rama: qué hay dónde

| Módulo | En tip actual (`8cf8dbd`) | En rama de feature publicada | Implicación para M7 |
| --- | --- | --- | --- |
| **M7** | Esqueleto (`package-info` + POM) | No hay `feature/m7-*` remota con implementación | Todo el núcleo productivo de comunicaciones falta |
| **M4** | Implementación presente (dominio, servicios, ports, tests, reviews) | `origin/feature/m4-programacion-convocatorias` — alineado: mismo `NotificacionPort` | **Contrato de consumo hacia M7 ya publicado y usado** |
| **M5** | Implementación sustancial presente (facade, servicios, dominio, docs) | `origin/feature/m5-participacion-intentos` | Depende de M6 para “aviso de intento finalizado”; **no** define aún puerto hacia M7 de correos/avisos in-app |
| **M6** | Solo `package-info` esqueleto | `origin/feature/m6-evaluacion-calificaciones` — también esqueleto | Sin contrato publicado hacia M7 |
| **M1** | Auth personal + `IdentityAccessPort` / `StudentAccessPort` | Incluido vía merges; tip incluye `4004516` | Destinatarios = `Usuario` / correo; auth estudiantil sigue DD-02 |

No se declara “M4/M5 vacíos”: en la rama actual (tras merge de `develop`) **sí hay código**. M6 sí permanece esqueleto **tanto local como en su feature remota**.

## 6. Dependencias de integración

### 6.1 M4 → M7 (contrato disponible)

[`NotificacionPort`](../m4/src/main/java/com/prisaly/m4/application/port/NotificacionPort.java) (rama actual = mismo contrato en `origin/feature/m4-programacion-convocatorias`):

```java
void notificarConvocatoria(UUID sesionId, UUID estudianteId);
```

- Documentado como puerto **hacia M7**.
- Regla de M4: si M7 no está implementado, **se rechaza** la operación; **no** se simula entrega exitosa.
- Fallback productivo en [`M4ProviderAutoConfiguration`](../m4/src/main/java/com/prisaly/m4/infrastructure/config/M4ProviderAutoConfiguration.java): bean `@ConditionalOnMissingBean(NotificacionPort)` lanza `M4-PROVEEDOR-NO-DISPONIBLE` / “No está disponible el proveedor requerido de M7”.
- Uso: [`SesionAcademicaService.convocarEstudiante`](../m4/src/main/java/com/prisaly/m4/application/service/SesionAcademicaService.java) persiste `Convocatoria` y luego llama `notificarConvocatoria`.
- Stub de prueba: `NotificacionStubAdapter` (solo tests; log sin envío real).

**Punto de integración pendiente:** adaptador **productivo** en `m7` (o ensamblaje `app`) que implemente `NotificacionPort` y traduzca la solicitud a encolado idempotente (`EnvioCorreo` y/o `Aviso`), **sin** fingir éxito de SMTP.

### 6.2 M5

- Docs: pendiente “aviso de intento finalizado” hacia **M6** (señal entre módulos), no un contrato M7 versionado.
- FK `Aviso.idParticipacion` anticipa avisos ligados a participación; **quién emite** esos avisos no está cerrado.

### 6.3 M6

- Sin ports ni eventos publicados hacia M7 en rama actual ni en `origin/feature/m6-evaluacion-calificaciones`.

### 6.4 M1

- Destinatario de `Aviso` / `EnvioCorreo`: `Usuario`.
- Correo destino debe resolverse de identidad (correo de usuario / estudiante vinculado); auth estudiantil sigue bloqueada por DD-02 — M7 no debe inventar login de estudiante.

## 7. Contratos de aplicación propuestos (**solo documentación**)

Propuesta para conciliar; **no** implementada en este reconocimiento.

### 7.1 Avisos in-app (idempotentes)

- Comando conceptual: `registrarAviso(idUsuarioDestino, tipo, contenido, claveEvento, idParticipacion?)`.
- Semántica: insertar respetando unique `(idUsuarioDestino, claveEvento)`; reintento del mismo evento no duplica fila (no-op o return existente).
- `tipo`: **valor opaco** hasta catálogo ratificado; no hardcodear enums en código sin conciliación.

### 7.2 Encolado de correo (idempotente)

- Comando conceptual: `encolarCorreo(idUsuarioDestino, correoDestino, asunto, contenido, claveIdempotencia, idConvocatoria?)`.
- Semántica: crear fila de cola con `estadoEnvio` inicial acorde al esquema (hoy default `'Pendiente'`); unique `claveIdempotencia`.
- **No** implica llamada SMTP en el mismo paso de encolado (salvo decisión explícita futura).

### 7.3 Adaptador de `NotificacionPort`

- Implementación M7 de `notificarConvocatoria(sesionId, estudianteId)`:
  1. Resolver convocatoria/usuario/correo vía hechos ya persistidos (sin inventar datos).
  2. Encolar con `claveIdempotencia` estable derivada del evento (p. ej. convocatoria/sesión+estudiante — **formato a conciliar**).
  3. Opcionalmente registrar `Aviso` con `claveEvento` alineada.
  4. Fallar de forma clara si faltan datos; nunca reportar éxito de entrega sin proveedor.

### 7.4 Entrega asíncrona (fase posterior)

- Worker que lea `ix_envio_pendiente`, intente proveedor, actualice intentos/error/`fechaEnvio`.
- Fuera de alcance hasta proveedor + política de reintentos ratificados.

## 8. Decisiones abiertas

1. Catálogo ratificado de `Aviso.tipo`.
2. Catálogo ratificado de `EnvioCorreo.estadoEnvio` (y CHECK futuro vía migración **nueva** autorizada — no tocar V001–V009).
3. Política de reintentos / backoff / máximo de intentos.
4. Proveedor de correo (SMTP u otro) y secretos de entorno.
5. Formato canónico de `claveEvento` / `claveIdempotencia` para convocatoria y otros eventos.
6. Emisión de avisos desde M5/M6 (¿puerto M7 compartido vs eventos internos?).
7. Permisos M1 / API para listar avisos y marcar `fechaLectura`.
8. Incorporación del catálogo V27 al repositorio.

## 9. Riesgos

| Riesgo | Nota |
| --- | --- |
| Convocar sin bean M7 | M4 falla con proveedor no disponible — integración real bloqueada hasta adaptador M7 |
| Confundir encolado con enviado | Reportes/UX incorrectos; el esquema ya separa campos de intento/envío |
| Catálogos inventados | Rompen conciliación V002 / Dirección Central |
| Auth estudiantil provisional | Notificar estudiantes depende de identidad/correo reales; DD-02 abierto |
| Alcance excesivo | Implementar SMTP+REST+workers antes del núcleo de persistencia aumenta incompatibilidad entre módulos |

## 10. Fases posteriores sugeridas (no autorizadas aún)

1. **Núcleo persistencia + idempotencia** (candidato a primer incremento *después* de revisión de este doc): entidades/repositorios solo en `m7`; comandos de §7.1–7.2; tests PostgreSQL de unique keys. **Gate:** no requiere catálogo completo si se tratan `tipo`/`estadoEnvio` como strings validados mínimamente y se documentan como provisionales — *pero* la ratificación sigue siendo obligatoria antes de checks SQL o APIs públicas.
2. **Adaptador `NotificacionPort`**: desbloquear convocatoria M4 sin simular SMTP.
3. **Worker + proveedor**: entrega real; actualizar cola; nunca en esta fase de reconocimiento.
4. **API de lectura de avisos** + permisos M1; UI Angular de M7 aparte y coordinada.
5. **Eventos M5/M6** cuando esos contratos existan.

### Gate de implementación

No implementar M7 productivo hasta al menos:

- acuerdo sobre qué se puede hacer **sin** catálogo cerrado (solo encolado/idempotencia), y
- decisión explícita de Arquitectura/equipo sobre proveedor, estados y emisión de eventos M4 (prioridad) / M5–M6 (después).

## 11. Fuera de alcance de este reconocimiento

- Modificar `PRISALY-Front`, M1, M3–M6, V001–V009.
- Enviar correos reales.
- Commit, push o merge de este documento (pendiente de autorización).
