# DC-M4-24H-01 — validación focalizada

6 de noviembre de 2026. Nicolás / M4. Rama `feature/m4-alineado-20261104`, SHA **3563177fb673ce374197907e199c4817d138d705**. Trabajo local y PM4-21 preservados. Sin modificación funcional, de contratos o migraciones; sin commit ni push. Dictamen: **compatible condicionado a runtime PostgreSQL**.

## Evidencia nueva de esta revisión

- `.\mvnw.cmd -B -DskipTests package`: **BUILD SUCCESS**, reactor completo M1–M7/app. Compilación de tests incluida; ejecución de tests omitida por el flag. El bloqueo anterior de CaseAuthorizationM1Config ya no se reproduce en esta base. No acredita arranque Spring.
- JAR app generado, SHA-256 **7D95F69C19240D2E1593BCEA2DC11050EEE8E8FB9B4EFDA0C3D9B0EFD312C5FC**. No fue arrancado ni probado por HTTP.
- Ejecución selectiva nueva: **39 registros, 21 aprobados, 18 omitidos, 0 fallos y 0 errores**. Las aprobadas son 20 de SesionAcademicaDomainTest y 1 de M4MigrationResourcesTest. Las omisiones fueron provocadas deliberadamente al no suministrar m4.baseline.migrations, para identificar su causa sin requerir Docker ni ejecutar limpieza.
- Evidencia exportada sin propiedades JVM/secretos: `docs/evidencias/DC-M4-24H-01/resumen-seguro.json`, con nombres y motivos exactos de las 18 omisiones. Logs locales ignorados por Git: dc-m4-24h-build.log y dc-m4-24h-tests.log.
- Docker sigue sin pipe dockerDesktopLinuxEngine. No se ejecutaron IT reales, limpieza, concurrencia, rollback ni HTTP en esta revisión. Las cifras centrales 883/18 y las comunicadas por M5 no se presentan como nuevas pruebas locales.

Comando diagnóstico ejecutado, sin propiedad de línea base:

```powershell
.\mvnw.cmd -B -pl m4 -am test '-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest,Bd0052BaselineIT,M4M1ContratosPostgreSqlIT,ProgramacionParticipacionPostgreSqlIT' '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Contratos D-06 revisados en el código vigente

| Control | Evidencia estática y límite |
|---|---|
| ParticipacionesPort | Implementador real único ParticipacionesM4DesdeM5; fallback M4 ConditionalOnMissingBean. No existe puente adicional app. |
| HistorialParticipacionPort | HistorialParticipacionJdbc único; exige transacción MANDATORY y consulta Participacion por convocatorias, sin interpretar error SQL como ausencia. |
| ProgramacionInicioCoordinadoPort | Un adaptador JDBC y registro condicionado a ausencia de otro bean. No se encontró reserva alternativa agregada. |
| Escritura READ COMMITTED | La fachada M5 coordina dentro de TransactionTemplate de escritura. M4 exige transacción activa, no read-only, y consulta el aislamiento real: solo admite read committed. La plantilla de escritura usa aislamiento por defecto, no lo fija; una configuración incompatible se rechaza, no se autoriza. REPEATABLE_READ nuevo solo afecta consultas read-only. |
| Orden | La fachada llama CoordinacionInicioDesdeM4 antes de IniciarIntentoServicio. M4 bloquea Sesion, luego Convocatoria y revalida asociación/titularidad. M5 bloquea/crea Participacion y continúa Intento. La lectura inicial de localización no es un lock invertido. |
| Rollback/huérfanos | La misma transacción engloba reserva e inicio; excepciones no se convierten en éxito. HistorialM4M5IT contiene fallo entre Participacion/Intento y rollbacks de inicio/edición. Es cobertura prevista, no resultado runtime nuevo. |
| Fail-closed | Sin proveedor de historial: 503; sin identidad: denegación; error de consulta nunca false. Reserva sin TX, read-only, aislamiento incompatible, titularidad ajena o fallo SQL no autoriza. Elegibilidad/límite temporal no ratificados bloquean primer inicio. |

La cardinalidad efectiva en contexto Spring, carreras y ausencia de huérfanos siguen pendientes. No se encontró defecto reproducible que justifique cambiar D-06 ni abrir PR funcional.

## Identificación de las 18 IT omitidas

Las tres clases tienen `@EnabledIfSystemProperty(named="m4.baseline.migrations", matches=".+")`. Motivo reproducido en XML: `System property [m4.baseline.migrations] does not exist`. La distribución coincide con las 18 omisiones informadas; sin el XML original de la auditoría central no se certifica identidad documental con aquella corrida.

- **Bd0052BaselineIT, 4:** rf09RealDeBaseDatosRechazaBorrador; publicadoProgramaYBloqueaContenidoConTriggerReal; rollbackRevierteSesionYMarcaAcademicaEnMismaTransaccion; diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial.
- **M4M1ContratosPostgreSqlIT, 6:** lecturaPropiaYListadoUsanIdentidadYPermisoPersistidosM1; lecturaAjenaYFiltroAjenoSeRechazan; cabeceraDeOtroDocenteNoSuplantaLaIdentidadReal; creacionSinCabeceraObtieneDocenteDeM1; permisoRevocadoEnPostgresqlImpideLectura; sinAutenticacionNoHayLecturaYAccesoLegadoNoAutoriza.
- **ProgramacionParticipacionPostgreSqlIT, 8 registros omitidos:** consultaSesionDevuelveContextoOficialSinEntidadesMutables; titularidadRechazaOtroEstudianteYNoConfundeIdUsuario; referenciaInexistenteYAjenaNoRevelanContexto; consumidorResuelveParticipacionIntentoYUsaSoloPuertoM4; informaRetiroCierreYCasoArchivadoSinConvertirDatosEnPermiso; observadoEnProvieneDelRelojPostgresql; esquemaNoDisponibleFallaSinAutorizarNiUsarTablasPropuestas; contextoTemporalEsInformativoSinCerrarNiAutorizar(String,long).

La última es parametrizada: 3 estados × 5 fronteras = 15 invocaciones al habilitarse. Por ello 4+6+8 omitidos equivalen a **32 casos habilitados**, no a 18 ejecuciones PostgreSQL. La cuarta prueba de baseline aplica V010 archivada deliberadamente para demostrar su riesgo: **excluirla de la ejecución canónica**. Si Dirección exige cerrarla, necesita autorización adicional expresa para ese diagnóstico en otro contenedor desechable; no es una migración a desplegar.

## Ejecución preparada para otro entorno

Precondiciones: Java 21; Maven wrapper; Docker Engine operativo; descarga de postgres:16.15 y postgres:16-alpine según suites; SHA exacto 3563177 y fuentes sin cambios funcionales. No pasar DATABASE_URL/credenciales compartidas ni usar compose con volumen prisaly_pg_data. Las suites usan PostgreSQLContainer y propiedades dinámicas, sin URLs de una base de compañeros. Deshabilitar reutilización Testcontainers. Registrar ejecutor, entorno, hora y SHA antes de correr.

**Autorización específica pendiente antes de ejecutar:** el responsable debe autorizar `Flyway.clean()` y el DDL de preparación/restauración exclusivamente sobre los contenedores efímeros creados por estas suites. Bd0052BaselineIT y ProgramacionParticipacionPostgreSqlIT hacen clean por caso; esto no autoriza clean/repair en otro entorno. La orden actual pide preparar esa autorización, no presume que ya se concedió. No se ejecutó limpieza aquí.

Desde la raíz del checkout autorizado, después de obtener esa autorización:

```powershell
$candidateSha = (git rev-parse HEAD).Trim()
if ($candidateSha -ne '3563177fb673ce374197907e199c4817d138d705') { throw 'Conciliar nuevo SHA antes de ejecutar' }
docker info
if ($LASTEXITCODE -ne 0) { throw 'Docker no disponible' }
$baselinePath = (Resolve-Path 'app/src/main/resources/db/migration').Path.Replace('\','/')
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
$selectedIT = 'Bd0052BaselineIT#rf09RealDeBaseDatosRechazaBorrador+publicadoProgramaYBloqueaContenidoConTriggerReal+rollbackRevierteSesionYMarcaAcademicaEnMismaTransaccion,M4M1ContratosPostgreSqlIT,ProgramacionParticipacionPostgreSqlIT,SesionAcademicaPostgreSqlIT,ParticipacionesM4DesdeM5IT,InicioIntentoIT,HistorialM4M5IT,ParticipacionesPortWiringIT,NotificacionPortWiringIT'
.\mvnw.cmd -B -pl app -am verify -Pdocker-it '-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest,M4CoordinacionInicioTest,M4ProviderSecurityTest,ParticipacionesM4DesdeM5Test' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dit.test=$selectedIT" '-Dfailsafe.failIfNoSpecifiedTests=false' "-Dm4.baseline.migrations=$baselinePath"
if ($LASTEXITCODE -ne 0) { throw 'Verificación fallida; conservar log y XML de esta corrida' }
```

Comando preparado y revisado contra los nombres existentes, **no ejecutado aquí con PostgreSQL**. No incluye mvn clean, repair ni el diagnóstico V010. La selección canónica de las suites originalmente omitidas contiene 31 casos habilitados (3+6+22); el caso V010 queda identificado y pendiente, sin eliminarlo ni cambiar sus aserciones. Las otras suites amplían cobertura, no se suman a los 31 como si fueran los mismos casos.

Escenarios runtime obligatorios de HistorialM4M5IT: proveedor único sin historial; participación sin intento; intento existente; convocatoria retirada; otra sesión independiente; carreras inicio/edición en ambos órdenes; dos inicios concurrentes sin duplicados; rollback de inicio sin huérfanos; rollback de edición sin contexto contaminado; fallo entre Participacion/Intento revierte ambos; timeout con recuperación; TX ausente/read-only rechazada y reserva ajena vacía. M4CoordinacionInicioTest agrega rechazo de aislamiento repeatable read con doble; falta acreditar con PostgreSQL el aislamiento efectivo del candidato y un escenario real de aislamiento incompatible. No confundir el doble con esa evidencia.

Recolectar solo XML/logs generados después del inicio de esta corrida; no sumar target antiguos ni corridas repetidas. Los XML pueden contener propiedades y secretos del entorno: revisar y sanitizar antes de compartir. Registrar cardinalidad efectiva de los tres puertos; ParticipacionesPortWiringIT cubre historial, pero por sí solo no acredita cardinalidad de los otros dos. Si no queda verificada, informar esa limitación.

## Acoplamiento M4 → M3 que afecta el cierre

M4 declara dependencia Maven M3 y su infraestructura importa CasoRepositoryPort y el modelo Caso. No se encontraron imports JPA internos M3. Con JdbcTemplate, M4ProviderAutoConfiguration elige CasoAcademicoBd0052Adapter, dependiente del protocolo SQL V007–V009: Publicado/activo, locks y confirmación F3-03 por trigger. Sin JDBC, el fallback CasoAcademicoM3Adapter lee el puerto público pero rechaza solicitarBloqueoAcademico porque no puede acreditar atomicidad F3-03. **No es sustituto productivo completo** para programar sesiones.

Consecuencia real: probar con línea canónica y adaptador JDBC; no usar el fallback como bypass para obtener un recorrido aprobado. El acoplamiento a modelo público y esquema compartido requiere coordinación futura si M3 cambia contratos; no se ha demostrado un defecto que obligue a refactorizarlo para este cierre. No se modifica.

## PM4-21, riesgos y entrega

PM4-21 conserva cuerpo/aserciones y bloqueo: exige Participacion legítima M5, no invitación/Convocatoria sola. Fixtures de IT no acreditan el recorrido HTTP académico autorizado. DD-02, D07 y ratificación D-06 siguen pendientes.

Riesgos abiertos: cardinalidad Spring y transacciones no ejecutadas localmente; falta de escenario PostgreSQL real para aislamiento incompatible; V010 diagnóstica requiere autorización diferenciada; V010 compartida continúa desconocida y condiciona despliegue, sin impedir esta prueba efímera. El JAR compilado no acredita arranque ni entrega HTTP.

Entrega: informe y resumen seguro de evidencia para PR documental separado. No hay PR funcional necesario ni creado. Próxima acción central: asignar ejecutor/entorno y autorizar limpieza efímera delimitada; ejecutar selección canónica, revisar resultados nuevos y mantener explícita la excepción del diagnóstico V010.
