# DC-EJEC-01-NICOLAS — compatibilidad de programación y convocatorias

8 de noviembre de 2026. Dictamen: **sin corrección funcional M4 justificada por
el diff publicado; compatibilidad estática conservada en las fronteras revisadas**.
No certifica ejecución integrada de todo el HEAD actual ni cambios locales de otros equipos.

## Versiones y comprobaciones

- `git fetch origin` completado; `origin/develop` vigente:
  **bc8e378bd6dd50a3b1484b06c268932ba5607dd3** (merge PR #54).
- Checkout conservado: `feature/m4-alineado-20261104`, HEAD
  `05a3bce4c24ccd069d23be3f44b4fd449d86269c`. No se actualizó ni limpió el
  árbol con modificaciones locales previas. Revisión del candidato por referencias Git.
- Referencia histórica: **d8eb62d0e4873ea28f426ed3d59392a5cd1ae95c**,
  DC-M4-POSTGRES-02, 114 pruebas PostgreSQL en diez suites, 0 fallos/errores/omisiones.
  `docs/evidencias/DC-M4-POSTGRES-02/summary.json` identifica ese SHA.
  Estas cifras no se atribuyen a bc8e378 ni al parche local previo.
- `git diff --quiet d8eb62d origin/develop` limitado por separado a
  `m4/src`, `m5/src/main`, `app/src/main/resources/db/migration`: **salida 0 en los tres**.
  También sin diferencias en contratos públicos M3, CasoRepositoryPort, fuentes
  productivas M1/M7 y POM raíz/M4/M5/M6/APP inspeccionados.
- Diff productivo relevante: PR #52 valida referencias al reconstruir el agregado
  M3; PR #53 agrega mapeador puro APP y validación estructural de rúbricas M6.
  No cambian el contrato SQL canónico de programación ni la reserva M4/M5.

## M3–M4

Revisados `CasoAcademicoPort`, `CasoAcademicoBd0052Adapter`,
`CasoAcademicoM3Adapter`, `M4ProviderAutoConfiguration` y
`SesionAcademicaService`, además del diff `CasoPersistenceMapper` de M3.

`estaPublicado(UUID)` exige en la implementación efectiva **Publicado y activo**;
el comentario del puerto menciona solo Publicado, sin ampliar por ello el permiso.
La consulta SQL utiliza la referencia exacta y FOR UPDATE dentro de escritura.
Caso inexistente, Borrador o inactivo no habilita programación. Fallos JDBC
producen `M4-PROVEEDOR-NO-DISPONIBLE`, no permiso ni ausencia silenciosa.
El servicio persiste Sesion y confirma F3-03 en la misma transacción;
el trigger oficial conserva la escritura de `fechaBloqueoAcademico`.

El adaptador Java alternativo verifica ID, publicado y activo, pero rechaza
confirmar F3-03 porque su contrato no garantiza bloqueo persistente atómico.
La nueva validación de referencias M3 rechaza reconstrucciones inconsistentes;
no convierte un error en caso válido ni sustituye el trigger. La ruta canónica
con JDBC no consume ese mapper para decidir Publicado/activo.

Coordinación enviada al chat **Mariana Nuevo Trabajo**: solicitar confirmación de
estado/activo/referencia/bloqueo y errores para PATCH/publicación, junto con
SHA/diff revisable. Su información disponible describe GET interno y PATCH en
preparación local, sin nueva publicación identificada en origin/develop.
**Respuesta específica a esta solicitud pendiente; no se presume conformidad.**
Al publicarse un cambio que afecte estos hechos, corresponde la regresión
focalizada de programación, rechazo de inválidos, trigger y rollback sobre ese SHA.

## M4–M5 y D-06

Revisados `ContextoProgramacion`, `ProgramacionParticipacionPort`,
`ProgramacionAutenticadaPort`, `ProgramacionInicioCoordinadoPort`, su adaptador
JDBC, `IdentidadEstudianteDesdeM1`, `TitularidadIntentosDesdeM4`,
`ConfiguracionParticipacion`, `FachadaParticipacionTransaccional` y lector
`ConsultaIntentoEvaluacionDesdeM5`.

Convocatoria conserva Estudiante→Sesion→Caso; el identificador es
`Estudiante.idEstudiante`, obtenido mediante M1. Consulta ajena/inexistente no
revela contexto. M5 conserva protección de intentos y sus repositorios; M4 no
agrega autorización ni acceso directo a Participacion/Intento.

D-06 mantiene transacción de escritura existente, READ COMMITTED y locks
Sesion→Convocatoria antes de Participacion→Intento. Revalida titularidad y contexto,
propaga errores y conserva rollback y prevención de duplicados existentes.
El lector protegido para evaluación conserva REQUIRES_NEW, read-only y
REPEATABLE_READ; no reemplaza la reserva de escritura. Los cambios M6/APP
publicados no alteran esas plantillas ni proveedores. PostgreSQL 16.15 permanece
como versión de las IT; no hay ejecución nueva de persistencia en este seguimiento.

Coordinación enviada al chat **Ingrid 4**: solicitar diff/SHA de cambios actuales
de inicio/lector y propuesta D07. **Respuesta específica pendiente.**

## D07, pruebas y entrega

D07 se trata como idempotencia de operaciones estudiantiles. No se identificó
implementación nueva ni contrato publicado en el diff M5; el controlador POST
preparatorio continúa sin registro MVC y declara D07 pendiente. La reanudación
de D-06 no acredita por sí sola idempotencia durable de peticiones HTTP.
La futura propuesta M5 debe conservar reserva, atomicidad y rollback existentes,
sin permitir que una repetición eluda identidad/titularidad. Su conformidad queda
pendiente de recibir el contrato; no se crea mecanismo M4.

**Sin pruebas nuevas en esta orden**: no cambiaron contratos/proveedores M4,
escritura M5 ni migraciones de programación. No se repite la campaña de 114 ni
se reutilizan cifras unitarias anteriores como resultados de bc8e378. Las dos
pruebas locales previas añadidas a HistorialM4M5IT siguen conservadas y con su
validación PostgreSQL pendiente, fuera del diff publicado revisado.

Defectos reproducidos/corregidos: ninguno en este seguimiento. Archivo nuevo:
este informe. Sin modificaciones productivas, staging, commit, push, PR ni merge.

Trazabilidad documental existente: RF-09 (Publicado/activo), F3-03 (bloqueo
académico), CU-04 y protección de historial/programación según
`docs/PT-DCP-01-M4.md`. No se localizó catálogo V27 oficial identificable en el
workspace: estos vínculos documentales no sustituyen su ratificación ni permiten
inventar un RF para D07. Mantener aprobación DD-02 y demás gates aplicables.
