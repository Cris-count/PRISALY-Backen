# M3: PostgreSQL con Testcontainers

No se crea ni modifica compose.yaml. No se cambia M1, M4 ni M5.

## Ejecucion

Desde la raiz del backend:

- Unitarias: `.\mvnw.cmd -B -pl m3 -am test`
- Unitarias e integracion Docker: `.\mvnw.cmd -B -pl m3 -am -Pdocker-it verify`

El perfil docker-it usa Failsafe para ejecutar *IT. Requiere Docker Desktop con motor Linux disponible. Si Docker no esta disponible, la prueba falla: no se registra como aprobada ni se omite silenciosamente.

PostgreSqlDockerIT inicia postgres:16.15 con puerto y credenciales temporales administrados por Testcontainers. Solo comprueba conexion JDBC y version mayor 16. No crea tablas ni certifica persistencia del agregado, Flyway, F3-03 o BD-005.2.

## Contrato de entorno de app

DATABASE_URL, DATABASE_USER y DATABASE_PASSWORD son las claves existentes. El archivo .env.example sirve de referencia: Spring Boot no carga automaticamente un archivo .env; exportar las variables en el proceso que inicia app. El password de ejemplo vacio debe configurarse localmente.

El nombre de base prisaly en la URL no confirma el nombre del esquema. Coordinar esquema, search_path y permisos Flyway con Integrante 1 antes de configurar migraciones.

## Pendientes

- Recibir columnas, claves, tipos y restricciones verificables de BD-005.2. Los nombres de seis tablas no bastan para implementar JPA o SQL.
- Coordinar numeracion de migraciones antes de agregar SQL al historial compartido.
- Implementar adaptador JPA y puerto de persistencia; aplicar transacciones cuando las operaciones realmente escriban el agregado.
- Probar creacion, publicacion, rechazo sin cambios parciales, bloqueo y copia independiente con las migraciones reales.
- M3-T06 actual comprueba una excepcion de dominio; HTTP 403 sigue pendiente de REST y seguridad.
- M6 queda en su rama separada despues de estabilizar M3; formula oficial pendiente de DD-03.

## Evidencia del volumen compartido pendiente

Cuando Integrante 1 entregue compose y variables: iniciar BD, aplicar migraciones acordadas, guardar y consultar un caso, reiniciar solo el servicio PostgreSQL y recuperar el mismo caso por su identificador. Registrar comandos, fecha y resultados sin secretos. No ejecutar down -v.

Un contenedor efimero de Testcontainers no certifica el volumen del compose del equipo.