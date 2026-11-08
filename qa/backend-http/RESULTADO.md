# DC-EJEC-02-NICOLAS — resultado de ejecución

8 de noviembre de 2026, Colombia. **Campaña HTTP bloqueada por Docker Engine
no disponible. No hay evidencia de respuestas HTTP de PRISALY en esta ejecución.**

- Rama: `test/backend-http-aceptacion`, worktree aislado administrado por Codex.
- SHA inicial y código productivo objetivo: `bc8e378bd6dd50a3b1484b06c268932ba5607dd3`.
- El checkout original, sus informes y el parche previo HistorialM4M5IT se preservaron.
- Cambios de esta entrega: solo `qa/backend-http/`. Sin cambios M1–M7/APP,
  migraciones, endpoints, servicios, repositorios, permisos ni políticas.
- El SHA de entrega y enlace del PR de QA se informan al publicar la revisión;
  el manifiesto fija por separado el SHA productivo y el hash del ejecutor.

## Resultados reales

| Comprobación | Resultado |
|---|---|
| Node 24.13.1; Java Temurin 21.0.10 | Disponibles |
| Sintaxis Node del ejecutor | Aprobada |
| Pruebas propias de saneamiento, entorno, propiedad de limpieza e inventario | 4 aprobadas, 0 fallos; no son HTTP |
| Maven `-B -pl app -am package -DskipTests` | BUILD SUCCESS, exit 0; no ejecuta suites del backend |
| JAR APP SHA-256 | `27dbd734703769a3c18afc57d888626b283955164faf35e1e798cdb5031c0059` |
| Campaña `node qa/backend-http/run.mjs` | Exit **2**, bloqueo de precondición Docker |
| Escenarios HTTP | **0 ejecutados/aprobados, 0 fallidos funcionales, 32 bloqueados** |
| APP/PostgreSQL iniciados | Ninguno; no se enviaron solicitudes ni se aplicaron fixtures |

La ejecución seleccionada de campaña tiene runId
`f68b8d3a-e152-41c6-a659-610c5651a7c5`; consultar `results/DC-EJEC-02/http-summary.json`
para el UUID exacto y marcas UTC. La ejecución de construcción se conserva
por separado en build-summary.json. No sumar resultados de modos auxiliares.

El error reproducible es:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

Reproducir con `docker info --format '{{.ServerVersion}}'` o con el ejecutor,
que registra el fallo y devuelve 2. No se diagnosticó como defecto productivo.
Un intento inicial del propio ejecutor rechazaba `.gitkeep` al contar migraciones;
se corrigió para ignorar únicamente ese marcador. Los intentos iniciales quedan
localmente en evidence; no se presentan como resultados HTTP.

## Escenarios y evidencias

Se prepararon 32 escenarios HTTP: login válido/inválido/inactivo; Bearer ausente,
inválido y revocado; permisos; creación de Borrador y rechazo de propietario
inyectado; Publicado/activo y referencias inválidas; sesión propia/ajena/ausente;
convocatoria/listado/duplicado; DD-02 501 y rechazo M5 sin identidad estudiantil.
La matriz **results/DC-EJEC-02/matrix.csv** contiene endpoint, método, precondición,
esperado, resultado real null, estado bloqueado y referencia al resumen.
No existen archivos de respuestas HTTP porque ninguna solicitud se envió.

Los positivos usan únicamente la base efímera del ejecutor. El seed Publicado
no acredita publicación docente ni habilitación académica. Las 114 pruebas
históricas DC-M4-POSTGRES-02 continúan vinculadas exclusivamente a d8eb62d;
no se repitieron ni se atribuyen al candidato actual.

## Dependencias y límite de aceptación

Solicitudes de conciliación enviadas a **CRISTIAN NUEVO 1**, **Mariana Nuevo Trabajo**
e **Ingrid 4**, con SHA y alcance, para confirmar rutas/permisos M1, REST publicado
M3 y límites HTTP M5/M6. Respuestas específicas pendientes al preparar esta entrega.
La preparación no esperó esas respuestas; se usaron los controladores del candidato.
No se modifican sus módulos para sortear bloqueos.

RF/CU sin asignar en la matriz: catálogo canónico verificable no localizado.
No se presentan documentos secundarios como ratificación V27.

La entrega proporciona pruebas ejecutables y bloqueo reproducible conforme a la
instrucción final. **Aceptación funcional integrada pendiente de ejecutarlas con
Docker operativo y revisar sus respuestas reales.** No acredita cierre productivo,
autorizaciones académicas ni ausencia de los errores APP reportados por otros equipos.
La siguiente acción es ejecutar el mismo comando en este checkout revisado con
Docker Linux operativo; no requiere ampliar permisos o crear endpoints.
