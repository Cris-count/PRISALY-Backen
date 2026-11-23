# PRISALY local — DC-OPS-70

Desde la raíz compartida `PRISALY-ENTREGA`, después de instalar el paquete:

```powershell
npm run up
npm run status
npm run down
```

Requisitos: Docker Desktop Linux local, Node >=22/npm y Java/Javac 21 en PATH. Se utilizan los repositorios existentes `PRISALY-Backen` y `PRISALY-Front`. El wrapper Maven fija Maven 3.9.11. No hay dependencias npm del orquestador raíz.

`up` compila el Backend con `-DskipTests package` y Angular con su configuración de producción existente; no significa despliegue productivo. Los artefactos se copian a `.build`, sin editar fuentes. En arranques posteriores se reutilizan si no cambió su huella de contenido. Las imágenes de ejecución son Java 21 JRE y Nginx; PostgreSQL es 16.15 y Mailpit v1.31.2. Se requiere Internet la primera vez si faltan imágenes/dependencias Maven. Si falta `node_modules`, se usa `npm ci`; si existe, se comprueban las versiones principales contra el lockfile y se rechaza una discrepancia sin borrarlo automáticamente.

## Direcciones y servicios

| Servicio propio | Dirección del equipo | Función |
| --- | --- | --- |
| Angular + Nginx | http://localhost:18170 | SPA y proxy de `/api` al Backend |
| Spring Boot | Solo `backend:8080` dentro de Docker | Monolito modular M1–M7; salud vía `/api/health` |
| PostgreSQL 16.15 | Solo `postgres:5432` dentro de Docker | Base nueva `prisaly_ops70` |
| Mailpit UI/API | http://localhost:18025 | Buzón local de captura, sin correo institucional |
| Mailpit SMTP | 127.0.0.1:11025; internamente `mailpit:1025` | Pruebas ficticias, no relay configurado |

Puertos publicados únicamente en loopback. Proyecto fijo `prisaly-ops70-local`, etiquetas `com.prisaly.instance=ops70-root`, redes propias `local` (interna) y `edge` (entrada de Nginx/Mailpit). Backend y PostgreSQL solo están en la interna. No se utiliza el Compose antiguo del Backend, cuyos nombres/volúmenes son globales, ni ningún QA existente.

Para un ensayo limpio independiente puede establecerse `PRISALY_OPS_PROJECT=prisaly-ops71-clean`, conservando ese valor en up/status/down y usando otros puertos. El valor predeterminado del equipo habitual no cambia. Solo se admiten nombres OPS70/OPS71; no nombres de QA protegidos. El smoke test toma su inventario de contenedores al comenzar y no requiere archivos históricos.

Para cambiar puertos, copiar `.env.example` de la raíz a `ops/local/.env` antes del primer arranque o editar allí los tres puertos después de `down`. No se permiten flags, contraseñas o claves en ese archivo. Si hay conflictos de puertos se detiene la operación; no se mata al proceso propietario.

## Persistencia y seguridad

- `down` afecta exclusivamente al proyecto y nunca usa `-v`, `prune` o eliminación de imágenes. Conserva `prisaly-ops70-local_pg_data` y `prisaly-ops70-local_audit_data`.
- La clave aleatoria de PostgreSQL se genera una sola vez en `.secrets/postgres-password`, con permisos locales restringidos, y se monta como Docker secret. Nunca se imprime ni entra en el contexto de build. No hay contraseña de ejemplo reutilizable.
- Conservar juntos los volúmenes y una copia privada de `.secrets`. Si existe el volumen pero falta la clave, `up` se detiene: no cambia la contraseña ni recrea datos.
- Flyway queda limitado a `classpath:db/migration`: V001–V009 y V013 en la base revisada. V014–V019 y propuestas fuera de ese directorio no se promueven. No se copia SQL candidato al directorio oficial.
- No se inicializan catálogo ni administrador. Las cuentas y permisos deben aprovisionarse por un procedimiento autorizado separado. No hay login E2E exitoso acreditado por levantar este entorno.
- DD-02, narrativa, listado candidato M3, POST M5, fases, evaluación M6 y workers se mantienen false. La autenticación sigue en Spring; el proxy no concede identidad ni permisos. Se conserva `personalQaHabilitado=false` en Angular.
- `status` no muestra secretos. Los comandos no cambian `develop`, historial, ramas o remotos.
- Si el proceso se interrumpe, el bloqueo `.build/operation.lock` impide operaciones superpuestas. Antes de retirar manualmente ese archivo, comprobar que no sigue ejecutándose el controlador. No eliminar `.secrets` ni volúmenes para solucionar un bloqueo.

## Verificación reproducible

```powershell
npm run up
npm run check
```

`check` usa el mismo controlador que los comandos npm. Prueba Angular, fallback SPA, salud por proxy, 401 sin identidad, URL `/api` del bundle, historial Flyway, SMTP/HTTP Mailpit y persistencia real tras `down` y otro `up`. Crea únicamente una fila de control en `ops70_validation.persistencia`, fuera del esquema académico; no modifica usuarios, casos o notas. Al aprobar, deja la instancia apagada y conserva el volumen. Las evidencias fechadas quedan en `evidencias/`; un fallo deja los servicios propios disponibles para diagnóstico y no limpia otros proyectos.

## API Gateway y simulador

Esta implementación es **un proxy inverso de entrada**, no un API Gateway formal. Reutiliza la opción de mismo origen descrita en `PRISALY-Front/docs/configuracion-api.md`. Los módulos M1–M7 se comunican por puertos Java en un monolito; no se convierten en microservicios ni se añade Spring Cloud Gateway. La documentación arquitectónica de transferencia respalda ese estilo, pero sus estados funcionales históricos no se usan como aceptación actual.

Si el requisito docente exige expresamente un Gateway con políticas propias (descubrimiento, cuotas distribuidas, autenticación de borde u otros servicios), esta entrega **no acredita esas capacidades**. Requiere conciliación de arquitectura/criterios, no renombrar Nginx como Gateway. Para el arranque local autorizado, Nginx sirve la SPA y reenvía API sin quitar autenticación, cookies, Authorization, ETag o If-Match.

Angular incluye su cliente existente del simulador. **No se inicia Unreal Engine, un servidor de señalización Pixel Streaming, SFU, TURN o un ejecutable de simulación inexistente.** No se cambian sus URLs ni se conecta un recurso ficticio. Es infraestructura web disponible, no evidencia de integración académica del simulador.

Mailpit es compatible como transporte SMTP de captura, pero **M7 no se activa ni se ensambla con un canal ficticio**: sus workers siguen apagados y no se ejecuta una campaña de entrega M7 por levantar Mailpit. La prueba SMTP solo verifica infraestructura local.
