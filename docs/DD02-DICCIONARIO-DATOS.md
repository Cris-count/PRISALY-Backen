# DD-02 — Diccionario de datos (propuesta)

**Estado:** Diseño autorizado — **sin DDL aplicado**  
**Convención:** identificadores citados al estilo BD-005.2 (`"NombreTabla"`, `"nombreColumna"`).  
**Parámetros ratificados:** OTP 8 dígitos; vigencia 5 min; 5 intentos; reenvío 60 s; sesión 8 h; inactividad 30 min; Outbox AES-256-GCM; sesiones persistidas.

FK hacia catálogo existente: `"Usuario"("idUsuario")`, `"Estudiante"("idEstudiante")`.

**Seguridad:** HMAC verifica el OTP en `"codigoHmac"`; AES-GCM protege `"payloadCifrado"` de Outbox — no intercambiables. Nunca persistir OTP ni id de sesión en claro.

---

## 1. CredencialAccesoEstudiante

| Columna | Tipo propuesto | Nulo | Descripción |
| --- | --- | --- | --- |
| idCredencial | uuid | NO | PK |
| idUsuario | uuid | NO | Destinatario (FK Usuario) |
| idEstudiante | uuid | NO | FK Estudiante (titularidad) |
| challengeId | uuid | NO | Identificador opaco de solicitud (único) |
| codigoHmac | bytea o varchar(128) | NO | HMAC-SHA-256 del OTP (hex o binario) |
| keyVersion | varchar(32) | NO | Versión de clave HMAC |
| fechaCreacion | timestamptz | NO | Emisión |
| fechaExpiracion | timestamptz | NO | Vigencia (emisión + 5 min) |
| fechaConsumo | timestamptz | SÍ | Consumo atómico |
| fechaRevocacion | timestamptz | SÍ | Revocación / invalidación por reenvío |
| intentosFallidos | int | NO | Contador (máx. 5) default 0 |
| fechaProximoReenvioPermitido | timestamptz | SÍ | Control reenvío 60 s |

**Restricciones / índices**

- PK `idCredencial`
- UNIQUE `challengeId`
- FK `idUsuario` → Usuario; FK `idEstudiante` → Estudiante
- CHECK `intentosFallidos >= 0 AND intentosFallidos <= 5`
- CHECK `fechaExpiracion > fechaCreacion`
- Índice: (`idUsuario`, `fechaCreacion` DESC) para listar/invalidar vigentes
- Índice parcial opcional: credenciales no consumidas/no revocadas por usuario

---

## 2. IntentoAccesoEstudiante

| Columna | Tipo propuesto | Nulo | Descripción |
| --- | --- | --- | --- |
| idIntentoAcceso | uuid | NO | PK |
| idCredencial | uuid | NO | FK CredencialAccesoEstudiante |
| fechaIntento | timestamptz | NO | Momento |
| resultado | varchar(30) | NO | EXITO / FALLO / BLOQUEADO / EXPIRADO / … |
| motivoCodigo | varchar(80) | SÍ | Código técnico de rechazo (sin secretos) |
| challengeId | uuid | NO | Copia de auditoría |
| huellaCliente | varchar(64) | SÍ | Hash opcional minimizado (no IP cruda sin política) |

**Restricciones / índices**

- PK `idIntentoAcceso`
- FK `idCredencial` ON DELETE RESTRICT
- CHECK `resultado` en catálogo cerrado
- Índice (`idCredencial`, `fechaIntento`)

No almacenar OTP introducido ni HMAC.

---

## 3. SesionEstudiante

| Columna | Tipo propuesto | Nulo | Descripción |
| --- | --- | --- | --- |
| idSesionEstudiante | uuid | NO | PK interno |
| idUsuario | uuid | NO | FK Usuario |
| idEstudiante | uuid | NO | FK Estudiante |
| sesionTokenHmac | bytea o varchar(128) | NO | HMAC/hash del token de cookie |
| keyVersion | varchar(32) | NO | Versión clave |
| fechaCreacion | timestamptz | NO | Alta |
| fechaUltimaActividad | timestamptz | NO | Inactividad 30 min |
| fechaExpiracion | timestamptz | NO | Máx. 8 h desde creación |
| fechaRevocacion | timestamptz | SÍ | Logout / revocación |

**Restricciones / índices**

- PK `idSesionEstudiante`
- UNIQUE `sesionTokenHmac` (o índice único)
- FK Usuario / Estudiante
- CHECK `fechaExpiracion > fechaCreacion`
- Índice (`idEstudiante`, `fechaRevocacion`) para sesiones activas

Token original solo en cookie HttpOnly; no en BD en claro.

---

## 4. EntregaCodigoAcceso (Outbox M7)

| Columna | Tipo propuesto | Nulo | Descripción |
| --- | --- | --- | --- |
| idEntrega | uuid | NO | PK |
| challengeId | uuid | NO | Idempotencia / vínculo lógico a credencial |
| idUsuarioDestino | uuid | NO | FK Usuario |
| correoDestino | varchar(255) | NO | Copia normalizada del correo |
| payloadCifrado | bytea | NO | OTP + metadatos cifrados AES-256-GCM |
| nonce | bytea | NO | Nonce GCM |
| keyVersion | varchar(32) | NO | Versión clave cifrado |
| claveIdempotencia | varchar(150) | NO | UNIQUE (evita doble encolado) |
| estadoEntrega | varchar(25) | NO | Pendiente / Enviado / Fallido / … |
| numeroIntentos | int | NO | Reintentos SMTP default 0 |
| ultimoError | text | SÍ | Mensaje seguro (sin OTP) |
| fechaCreacion | timestamptz | NO | Alta (misma TX que credencial) |
| fechaExpiracion | timestamptz | NO | TTL alineado a OTP |
| fechaUltimoIntento | timestamptz | SÍ | |
| proximoReintento | timestamptz | SÍ | |
| fechaEnvio | timestamptz | SÍ | SMTP aceptado |

**Restricciones / índices**

- PK `idEntrega`
- UNIQUE `claveIdempotencia`
- UNIQUE o índice (`challengeId`) según política de un envío por challenge
- FK `idUsuarioDestino` → Usuario
- Índice (`estadoEntrega`, `proximoReintento`) para claim worker
- CHECK `numeroIntentos >= 0`

Independiente de `"EnvioCorreo"` de convocatorias.

---

## 5. Relaciones lógicas

```text
Usuario 1──* CredencialAccesoEstudiante
Estudiante 1──* CredencialAccesoEstudiante
CredencialAccesoEstudiante 1──* IntentoAccesoEstudiante
CredencialAccesoEstudiante 1──0..1 EntregaCodigoAcceso (vía challengeId / idempotencia)
Usuario 1──* SesionEstudiante
Estudiante 1──* SesionEstudiante
```

`challengeId` une credencial y outbox sin exponer identidad autenticada.

---

## 6. Fuera de alcance de este diccionario

Rutas HTTP, beans Java, feature flag runtime, plantillas de correo, y número de versión Flyway definitivo (ver inventario).
