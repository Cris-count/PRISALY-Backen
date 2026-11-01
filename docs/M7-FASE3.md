# M7 Fase 3 — Entrega SMTP y worker

Rama: `feature/m7-notificaciones`. Decisiones D1–D11 ratificadas. Sin V010; V001–V009 intactas.

## Qué hace

1. El worker (opcional) reclama filas `Pendiente` con `FOR UPDATE SKIP LOCKED`, incrementa `numeroIntentos` (conteo de reclamos) y fija un lease en `proximoReintento` con `now()` de PostgreSQL.
2. Envía por SMTP **fuera** de la transacción de reclamo.
3. Persiste el resultado **solo** si conserva el lease (`proximoReintento` + `numeroIntentos` coinciden).
4. Estados: `Pendiente` → `Enviado` (aceptación SMTP) o `Fallido` (permanente / intentos agotados). Errores de configuración dejan `Pendiente` y alertan; no marcan el destinatario como `Fallido`.

## Activación

Deshabilitado por defecto. Para habilitar:

```yaml
prisaly:
  m7:
    worker:
      enabled: true
      intervalo-ms: 5000
      lote: 20
      lease-segundos: 60
    correo:
      remitente: noreply@example.com
spring:
  mail:
    host: ${SPRING_MAIL_HOST}
    port: ${SPRING_MAIL_PORT}
    username: ${SPRING_MAIL_USERNAME:}
    password: ${SPRING_MAIL_PASSWORD:}
```

Credenciales solo por entorno; nunca en Git.

## Política ratificada

| Parámetro | Valor |
| --- | --- |
| Máximo de reclamos | 5 |
| Backoff tras intento 1–4 | 1, 2, 4, 8 minutos |
| Tope | 60 minutos |

## Idempotencia (D7)

`RegistroEnvioCorreoJpaAdapter` acepta repeticiones con los mismos datos inmutables en cualquier estado. No crea otra fila ni reactiva `Fallido`/`Enviado`.

## Contratos nuevos

- `ReclamoEnvioPort` / `ReclamoEnvioJpaAdapter`
- `ProveedorCorreoPort` / `JavaMailProveedorCorreoAdapter`
- `ProcesarEnviosPendientesService`
- `PoliticaReintentos`, `ClasificacionErrorCorreo`
- `M7WorkerConfiguration` (`@ConditionalOnProperty`)

## Limitaciones conocidas

- Entrega **al menos una vez**: si SMTP acepta y el proceso cae antes de actualizar PostgreSQL, el lease vencido puede producir un segundo envío.
- `Enviado` = aceptación del servidor SMTP, no prueba de llegada al buzón.
- Sin historial por intento ni `Message-ID` persistido (columnas existentes + logs).
- Sin CHECK SQL de catálogo (V010 no autorizada).
- Un lease abandonado con `numeroIntentos >= 5` se finaliza como `Fallido` en el siguiente ciclo, sin otro SMTP.

## Pruebas

```bash
./mvnw -B -pl m7 test
./mvnw -B -pl m7 -Pdocker-it verify
```

Suites nuevas: política/clasificación/servicio unitarios, `M7WorkerDisabledTest`, `M7EnvioWorkerPostgreSqlIT` (PostgreSQL + GreenMail).
