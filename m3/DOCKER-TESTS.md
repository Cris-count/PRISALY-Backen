# M3: PostgreSQL con Testcontainers

## M3-INC-10D — alta de escenario (pendiente de ejecución)

AgregarEscenarioCommand permite exclusivamente escenarios vacíos en Borrador sin
bloqueo académico, con UUID/orden explícitos y activo mediante DEFAULT true.
CasoCommandAddContentIT incorpora siete pruebas PostgreSQL de alta, conservación,
conflictos/rollback, huella, dos escritores y programación con triggers activos.
CasoUpdateContractTest añade tres pruebas de contrato/precondiciones.
Detalle, matriz de cobertura y fuentes faltantes: [M3-INC-10D](docs/M3-INC-10D.md).
Los resultados reportados de 91 unitarias y 31 PostgreSQL son anteriores a 10D.
No se ejecutaron Maven, Docker ni pruebas en esta intervención.

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandAddContentIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT,CasoPersistenceAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

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

- V001–V009, el puerto, el lector y los adaptadores de insercion y actualizacion parcial ya estan presentes. Existen pruebas PostgreSQL de las seis tablas, rollback y concurrencia; no confundirlas con integracion funcional del servicio.
- Siguen pendientes publicacion y copia persistidas, sus decisiones de dominio y la orquestacion del servicio. No ampliar migraciones como parte de RF-09.
- M3-T06 actual comprueba una excepcion de dominio; HTTP 403 sigue pendiente de REST y seguridad.
- M6 queda en su rama separada despues de estabilizar M3; formula oficial pendiente de DD-03.

## Evidencia del volumen compartido pendiente

Cuando Integrante 1 entregue compose y variables: iniciar BD, aplicar migraciones acordadas, guardar y consultar un caso, reiniciar solo el servicio PostgreSQL y recuperar el mismo caso por su identificador. Registrar comandos, fecha y resultados sin secretos. No ejecutar down -v.

Un contenedor efimero de Testcontainers no certifica el volumen del compose del equipo.

## M3-INC-10C — disponibilidad RF-09

El servicio exige Publicado y activo, igual que V009. Caso.isPublished() sigue
consultando solo el estado. CasoMetadataTest incorpora la matriz de seis
combinaciones, comprueba contenido/metadatos intactos y conserva como disponible
un Publicado activo con bloqueo existente. Otra prueba comprueba que no se exige
rubrica, preguntas ni fechas. No se implementa programacion ni autorizacion.

Resultados anteriores reportados por Integrante 2: 89 unitarias, 26 integraciones
anteriores y 33 adicionales aprobadas, con compilacion satisfactoria. Esta anotacion
no sustituye sus reportes ni atribuye esos resultados a 10C. Las pruebas nuevas
no fueron ejecutadas. Conservar los XML de Surefire/Failsafe junto al commit
realmente validado y fecha; no sumar recuentos sin comprobar suites y ejecuciones.

Validacion manual desde la raiz del backend:

```powershell
.\mvnw.cmd -B -pl m3 -am "-Dtest=CasoMetadataTest,CasoApplicationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoPersistenceAdapterIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

Estas IT conservan el contraste positivo de programacion y la proteccion F3-03;
no se presentan como una prueba automatizada nueva de las seis combinaciones V009.
Para contrastarlas directamente, ejecutar el bloque siguiente en psql sobre una
base aislada con V001–V009 y un docente fixture existente. Definir previamente
`docente_uuid` mediante `\set docente_uuid 'UUID_REAL_DEL_DOCENTE_FIXTURE'`.
No usar datos de produccion. Todo queda dentro de una transaccion con ROLLBACK.
Cada rechazo debe identificar especificamente la restriccion de V009; otro error
no cuenta como evidencia RF-09. No se deshabilitan triggers ni se editan migraciones.

```sql
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL statement_timeout = '10s';
SET LOCAL lock_timeout = '5s';
SELECT set_config('m3.rf09_docente', :'docente_uuid', true);
DO $rf09$
DECLARE
  docente uuid := current_setting('m3.rf09_docente')::uuid;
  estado_caso text;
  caso_activo boolean;
  caso_id uuid;
  permitido boolean;
  esperado boolean;
  restriccion text;
BEGIN
  FOREACH estado_caso IN ARRAY ARRAY['Publicado','Borrador','Archivado'] LOOP
    FOREACH caso_activo IN ARRAY ARRAY[true,false] LOOP
      caso_id := gen_random_uuid();
      INSERT INTO public."Caso" ("idCaso","idDocente","nombre","tiempoEstimadoSegundos","estado","activo")
      VALUES (caso_id,docente,'Fixture RF-09',300,estado_caso,caso_activo);
      permitido := false;
      esperado := estado_caso = 'Publicado' AND caso_activo;
      BEGIN
        INSERT INTO public."Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
          "inicioPrevisto","finPrevisto","fechaInicioReal","tiempoMaximoParticipacionSegundos","estado")
        VALUES (gen_random_uuid(),caso_id,docente,'Fixture RF-09',clock_timestamp()-interval '1 minute',
          clock_timestamp()+interval '5 minutes',clock_timestamp(),300,'EnCurso');
        permitido := true;
      EXCEPTION WHEN check_violation THEN
        GET STACKED DIAGNOSTICS restriccion = CONSTRAINT_NAME;
        IF restriccion IS DISTINCT FROM 'tr_sesion_rf09_caso_programable' THEN RAISE; END IF;
      END;
      IF permitido IS DISTINCT FROM esperado THEN
        RAISE EXCEPTION 'RF-09 inesperado: estado=%, activo=%, permitido=%',estado_caso,caso_activo,permitido;
      END IF;
      RAISE NOTICE 'RF-09 estado=%, activo=%, permitido=%',estado_caso,caso_activo,permitido;
    END LOOP;
  END LOOP;
END $rf09$;
ROLLBACK;
```

El bloque usa estados existentes como fixtures, sin introducir transiciones de
Archivado ni un flujo M4. V007 activa el bloqueo de la sesion permitida mediante
su trigger. En caso de error, ejecutar ROLLBACK antes de reutilizar la conexion.
Registrar las seis salidas y cualquier SQLSTATE. Ejecucion PostgreSQL pendiente
del Integrante 2; el contraste realizado aqui fue por inspeccion de V009.
