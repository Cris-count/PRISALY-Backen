# DC-M3-GET-01 — Revisión documental sobre develop actualizado

**Diseño para ratificación; implementación GET bloqueada.** Esta revisión complementa [la propuesta histórica c52ae9e](PROPUESTA-GET-SEGURO-c52ae9e.md), que se conserva sin modificaciones. No constituye contrato definitivo, habilitación de lectura ni autorización para publicar contenido académico.

## 1. Base real y sincronización

- Directorio de trabajo: `C:\Users\57316\OneDrive\Escritorio\PRISALY\m3-sincronizado`.
- Rama: feature/m3-gestion-casos.
- SHA anterior: `c52ae9e101087cde5ea921f71e1245bfa1e9e015`.
- Nuevo SHA procedente de origin/develop: `f1d0c2c03cac5f9091e5ee9063f02c65acdec333`.
- Actualización: cinco commits existentes mediante merge --ff-only; sin reset, rebase ni merge adicional.
- Commits incorporados: 048bff7, d5771cf, 59c37d2, 3cf5465 y f1d0c2c (PR #39).

El documento histórico era el único archivo local sin seguimiento. Antes de actualizar se copió, conservando su ruta relativa, a M3-GET-01-RESPALDO fuera del árbol del repositorio y se contrastó SHA-256. Hash original y posterior al fast-forward:

`D7F533BDA3DC6F26C5AF197C5024BDDFE46CC1CEDB9F7D924DFD4637DE514839`

No hay cambios productivos entre c52ae9e y f1d0c2c en M1, M3, APP, POM o migraciones. La prueba M3CaseAuthorizationAdapterTest solo mueve el método auxiliar user al final de la clase, conservando sus nueve escenarios. El resto de cambios corresponde a recursos y generación Postman de Integrante 1, incluida la retirada de dos ZIP versionados en develop; no se alteraron respaldos externos ni archivos originales del Integrante 2.

La sincronización se publica separadamente del commit documental para distinguir el avance de develop de la propuesta. El SHA final del commit documental y el resultado efectivo del push se registran en el informe de entrega, sin atribuir pruebas ejecutadas sobre f1d0c2c a código funcional nuevo.

## 2. Reutilización y límites confirmados

- CasoRepositoryPort.findById y su adaptador ya leen el agregado mediante snapshot consistente de seis tablas. No se creará otro repositorio, entidad o SQL para la ficha.
- CasoJpaAggregateReader es read-only, REQUIRES_NEW y REPEATABLE_READ. El servicio futuro debe autorizar y proyectar el mismo resultado cargado; no prometer atomicidad con una consulta M1 externa ni una segunda lectura.
- CasoApplicationService contiene creación y operaciones existentes, pero no una consulta REST autorizada. POST no se reconstruye.
- IdentityAccessPort ofrece currentUser, requireUser, hasPermission y hasRole. APP puede consumir esos contratos sin cambios en M1. Propiedad y política del recurso siguen siendo responsabilidad M3.
- M3CaseAuthorizationAdapter exige identidad activa, coincidencia de UUID/propietario y CREAR_CASO para crear. No tiene permiso de lectura y deniega modificar/publicar. **CREAR_CASO no se reutiliza para GET.**
- ContextoAcademicoConsultaPort, AutorizacionContextoAcademicoPort y ContextoAcademicoProtectedReader corresponden al contexto evaluable con consumidor M6_EVALUACION. Su DTO contiene esCorrecta, explicacion y rúbrica. **No se utilizan directamente como respuesta HTTP docente ni se modifica su contrato.**
- Solo existe POST /api/v1/casos en presentación M3. No se añade controlador GET ni se altera la expectativa actual de ausencia de GET de las pruebas integradas.

## 3. DTO docente específico propuesto

**Nombre candidato:** CasoDocenteConsultaResponse, en m3.presentation.dto, como futura proyección de presentación. El nombre, campos, tipos JSON y nulidad deben ratificarse. No se crea clase Java en DC-M3-GET-01.

### Primera proyección candidata: ficha de metadatos

Esta revisión reduce la propuesta histórica de consulta de contenido: recomienda empezar por una ficha docente sin contenido evaluable. Que un campo figure aquí no aprueba su exposición; incluso UUID, título y estado revelan existencia del caso y requieren autorización previa.

| Campo potencial / tipo propuesto | Origen exacto dominio / persistencia | Justificación funcional | Riesgo de exposición | Autorización y decisión pendiente |
| --- | --- | --- | --- | --- |
| idCaso / UUID | Caso.id; Caso.idCaso | Identificar la ficha consultada | Identifica recurso y permite correlación | UUID ya disponible; ratificar formato HTTP y política antienumeración |
| titulo / String | Caso.titulo; Caso.nombre | Reconocer el caso sin devolver narrativa | El texto puede incluir información reservada | Acceso institucional aprobado; ratificar nombre JSON y adecuación del título para esta proyección |
| tiempoEstimadoSegundos / entero | Caso.tiempoEstimadoSegundos; columna homónima | Mostrar duración estimada almacenada | Revela metadato académico | Ratificar inclusión; no calcular límites de participación ni reglas de M4/M5 |
| estado / representación por ratificar | Caso.estado; SQL Borrador/Publicado/Archivado | Mostrar ciclo de vida del recurso visible | Puede revelar caso en preparación o archivado | Ratificar estados visibles y serialización; Publicado NO otorga acceso universal |
| activo / boolean, opcional | Caso.activo; Caso.activo | Posible indicador administrativo | Revela disponibilidad interna | No incluir en primera ficha salvo ratificación expresa; no equiparar activo a visible |
| fechaCreacion / instante, opcional | Caso.fechaCreacion; Caso.fechaCreacion | Trazabilidad si la interfaz la necesita | Metadato de actividad | Ratificar necesidad, formato y nulidad antes de incluir |
| fechaActualizacion / instante nullable, opcional | Caso.fechaActualizacion; Caso.fechaActualizacion | Identificar revisión registrada | Permite inferir actividad docente | Omitir salvo necesidad aprobada; no fabricar fecha ni confundirla con control de concurrencia |
| contenidoEvaluableBloqueado / boolean, opcional | Caso.contenidoEvaluableBloqueado, derivado también de fechaBloqueoAcademico | Posible indicación de bloqueo F3-03 | Revela actividad de programación | Requiere ratificación; nunca habilitar edición ni exponer sesiones desde esta ficha |

**Lista inicial recomendada para discusión:** idCaso, titulo, tiempoEstimadoSegundos y estado. Los cuatro son candidatos sujetos a ratificación, no una respuesta aprobada. Los campos opcionales quedan excluidos por defecto de la propuesta inicial. No se adjunta JSON que pudiera confundirse con contrato definitivo ni datos reales de casos.

### Campos excluidos de la ficha inicial

| Campo o estructura | Origen | Motivo |
| --- | --- | --- |
| historia, desarrolloSituacional | Caso | Narrativa académica; contenido fuera del alcance inicial |
| escenarios, contexto, instrucciones | Escenario | Contenido del caso y posible información evaluable |
| preguntas, enunciado, textoAyuda, estadoEdicion, visible | Pregunta | Exponen contenido/estructura de evaluación y decisiones de visibilidad no ratificadas |
| opciones, texto de opción, esCorrecta, explicacion | OpcionRespuesta | No entregar opciones ni claves/explicaciones de corrección |
| rúbrica, puntajeTotal, definicionEvaluacion | Rubrica | Datos evaluables/JSONB; no interpretar semántica DD-03 |
| criterio, valorPregunta y asociaciones | RubricaPregunta / CriterioRubrica legado | Reserva académica; sin equivalencias o reglas nuevas |
| docenteId e identidad personal | Caso.idDocente / M1 | Solo necesarios internamente para autorización; no proponerlos como salida de ficha |
| fechaBloqueoAcademico exacta | Caso | Omitir dato operativo; el indicador opcional de bloqueo requiere decisión separada |
| huella m3-v2, capacidad M6, token, entidades JPA | Infraestructura / seguridad | No crear contrato PATCH ni filtrar credenciales o detalles internos |
| conteos de preguntas/opciones/rúbricas | Agregado | No necesarios para la ficha; pueden revelar estructura evaluativa |

Un mapper futuro debe construir el DTO por lista explícita, no serializar Caso ni copiar propiedades dinámicamente. Excluir una propiedad de salida no sustituye la autorización del recurso ni autoriza al cliente a conocer su existencia.

## 4. Dependencias de identidad y autorización

Flujo de diseño, no implementación:

1. Conservar autenticación M1 y Spring Security. Identidad efectiva desde currentUser, no desde docenteId, rol o permiso declarado por el cliente.
2. Exigir la política de consulta que ratifique Dirección, expresable con contratos institucionales existentes. No crear un código de permiso ni usar CREAR_CASO como lectura.
3. Validar UUID y cargar una vez el caso mediante persistencia existente, solo tras elegibilidad general cuando sea posible.
4. Evaluar propietario, estado y demás metadatos sobre el mismo resultado. Un caso Publicado de otro docente no queda autorizado por ese estado.
5. Devolver exclusivamente la ficha aprobada si la decisión es explícitamente favorable. Ausencia/error del proveedor de autorización no concede acceso; fallo de BD no se convierte en caso inexistente.

El adaptador actual puede ampliarse con un puerto local de consulta solo después de aprobar su firma/política y el ensamblaje APP con Cristian. No se añade una segunda implementación de CaseAuthorizationPort, ni Primary ni fallback permisivo. CaseAuthorizationPort e IdentityAccessPort existentes permanecen intactos durante esta preparación.

## 5. Matriz de pruebas GET diseñada

**Estado de todas las pruebas GET: DISEÑADAS, NOT RUN.** No hay controlador ni clase DTO nueva. El resultado esperado conocido es una condición de seguridad/datos; no implica un código HTTP aprobado para el endpoint. Las pruebas de MVC deben fijar códigos solo después de la ratificación correspondiente.

| ID | Escenario | Precondiciones | Resultado esperado conocido | Decisión pendiente | Nivel |
| --- | --- | --- | --- | --- | --- |
| G01 | Docente autenticado y autorizado | M1 activo, política de consulta ratificada favorable, recurso visible y propiedad conforme | Una consulta consistente y exclusivamente campos aprobados de ficha | Permiso/política efectiva, DTO y éxito HTTP | Unitaria + MVC + seguridad integrada |
| G02 | Identidad inválida | Sin principal UUID válido, token ausente/inválido/revocado o cuenta inactiva | No entrega datos; conserva denegación de autenticación M1; sin consulta de contenido tras fallo general | Contrato formal GET; 401 ya existe en cadena general, no se crea uno nuevo | Seguridad + MVC |
| G03 | Usuario sin permiso de lectura ratificado | Autenticación válida, decisión institucional negativa | Fail-closed; no ficha ni contenido; CREAR_CASO no sustituye consulta | Política/código existente aplicable y respuesta de denegación | Unitaria + seguridad integrada |
| G04 | Docente no propietario | Identidad válida, caso de otro docente, incluso con permiso general | No acceso implícito; solo excepciones expresamente aprobadas | Excepciones y política de no visibilidad; sin elegir 403/404 | Unitaria + MVC + PostgreSQL |
| G05 | Caso inexistente | UUID válido, sujeto elegible, repositorio sin fila | Ausencia interna diferenciada de error técnico; sin datos de otros casos | Respuesta HTTP/cuerpo de ausencia y antienumeración | Unitaria + MVC + PostgreSQL |
| G06 | Caso Borrador | Caso persistido Borrador; probar propietario y ajeno | No se infiere visibilidad por existencia/propiedad sola; aplica política ratificada | Si Borrador es consultable y por quién | Unitaria + integración PostgreSQL |
| G07 | Caso Publicado | Caso Publicado; probar propietario y otro docente | Publicado no equivale a acceso para cualquier docente | Consumidores, propiedad/excepciones y estado visible | Unitaria + seguridad integrada |
| G08 | Caso Archivado | Fila Archivado, sin ejecutar transición | No transición ni acceso inventado; solo política de consulta aprobada | Tratamiento de Archivado y respuesta de recurso oculto | Unitaria + MVC + PostgreSQL |
| G09 | Caso no visible | Estado/activo/propiedad no cumple política aprobada | No ficha, propietario, estado ni señal de existencia prohibida | Ratificar 403/404 y uniformidad de error; no decidir aquí | MVC + seguridad |
| G10 | Respuestas correctas y datos reservados | Agregado fixture con esCorrecta, explicacion, narrativa, preguntas y rúbrica | Ninguna de esas estructuras/propiedades aparece en la ficha, errores o logs | Lista definitiva de campos; exclusiones propuestas deben aprobarse | Unitaria de proyección + MVC |
| G11 | Fallo del proveedor de autorización | Excepción, ausencia, timeout, decisión indeterminada o no disponible | Denegación sin contenido ni excepción sensible; nunca autorización por defecto | Código de error técnico/denegación del contrato GET | Unitaria + seguridad integrada |
| G12 | Fallo de persistencia | Acceso general elegible, BD/mapper falla | Error técnico conservado internamente y sanitizado hacia fuera; no Optional vacío ficticio | Respuesta de servidor ratificada; no 404 por fallo de BD | Unitaria + integración PostgreSQL |
| G13 | UUID inválido | Formato malformado, incompleto o no canónico | No consultar fila ni conceder acceso por valor arbitrario | Regla exacta de formato y respuesta HTTP | Unitaria + MVC |
| G14 | Protección de datos | Cliente intenta aportar docenteId, roles o permisos en cabeceras/parámetros | Solo M1 acredita identidad; sin efecto autorizador de los datos aportados | Aceptación/rechazo de parámetros según contrato | MVC + seguridad |
| G15 | Snapshot y concurrencia de lectura | Cambio concurrente de estado/propietario; lector consistente existente | No autorizar una versión y devolver otra; no mezclar raíz/hijos | Punto temporal de autorización y revocación entre M1/M3 | Integración PostgreSQL |
| G16 | GET sobre contenido bloqueado F3-03 | Marca oficial de bloqueo ya existente | No modifica estado, fechas, hijos, marca ni huella; no debilita bloqueo | Visibilidad de ficha bloqueada, sin permisos de edición | Integración PostgreSQL |
| G17 | Proveedores Spring | Contexto APP completo con política futura ratificada | Un proveedor efectivo por puerto; puente M1 único; no activar publicación o lector M6 | Firma y ensamblaje local de consulta coordinados | Contexto + seguridad integrada |
| G18 | Regresión POST | DTO/controlador POST actuales, M1 y PostgreSQL reales | Mantener creación autorizada, rechazo sin permiso/identidad, UUID único y commit visible | Ninguna decisión GET debe cambiar creación | Unitarias actuales + M3CasoCreationIT |
| G19 | Ficha con metadatos nulos/Unicode | Título Unicode, fechas opcionales ausentes, duración persistida | Proyección exacta sin coerción ni valores fabricados; opcionales no ratificados ausentes | Nulidad, nombres JSON, enum y formato temporal | Unitaria DTO + integración PostgreSQL |

No se modificarán pruebas históricas para simular éxito de GET. La prueba actual que exige solo POST sigue siendo correcta mientras GET no esté autorizado e implementado; cualquier adaptación futura debe conservar comprobaciones de ausencia de PATCH/publicación.

## 6. ARQ-008.3 y decisiones institucionales pendientes

En el inventario del checkout actualizado no apareció el texto completo vigente de ARQ-008.3/OpenAPI; solo referencias documentales y V008 SQL, que no es un contrato REST. DC-M3-REST-GET-PATCH.md reconoce la ruta GET /api/v1/casos/{idCaso} por instrucción de DT, sin cerrar autorización, proyección, estados o errores. No se inventan rutas adicionales ni se afirma cumplimiento de una especificación no disponible.

Dirección/Arquitectura deben ratificar:

1. Fuente/versionado de ARQ-008.3 y conformidad de la ruta reconocida, UUID y envoltorio de respuesta.
2. Consumidor inicial y política de consulta: permiso institucional existente o regla explícita aprobada, sin reutilizar CREAR_CASO ni crear permisos nuevos.
3. Propiedad, excepciones y exclusión/inclusión de estudiante; Publicado no autoriza por sí solo.
4. Borrador/Publicado/Archivado, activo y bloqueo F3-03 como condiciones de visibilidad, sin nuevas transiciones.
5. DTO definitivo de ficha y campos opcionales; confirmar que narrativa y contenido evaluable siguen fuera del primer incremento.
6. Caso no visible frente a inexistente y sin permiso: códigos, cuerpo y política antienumeración; 403/404 permanece sin decidir.
7. Política ante fallos de autorización/persistencia, revocación concurrente y metadatos de caché; no añadir ETag/PATCH de manera implícita.
8. Puerto local de autorización y ensamblaje APP con Cristian, sin cambios en contratos de M1 ni en proveedor único actual.
9. Autorización posterior de implementación y publicación funcional, que DC-M3-GET-01 no concede.

## 7. Riesgos y límites

- Confundir el DTO de contexto M6 con ficha docente expondría datos evaluables y usaría el consumidor incorrecto.
- Un mapper automático o serialización del agregado puede incorporar campos reservados al evolucionar dominio; se requiere lista explícita y pruebas de exclusión.
- Ser propietario, poseer CREAR_CASO o consultar un Publicado no establece la política de lectura faltante.
- Repositorio vacío y error de BD son hechos distintos; convertir ambos en 404 ocultaría fallos y anticiparía decisiones contractuales.
- La advertencia de Mockito/JDK no se soluciona desactivando seguridad ni modificando pruebas. El reordenamiento remoto del helper no cambia la autorización productiva.
- Los títulos y estados siguen siendo información del recurso: una ficha reducida también exige autorización y tratamiento de no visibilidad ratificados.

Fuera de alcance: clases Java GET, contenido evaluable en respuestas, PATCH, publicación académica, copia persistida, Archivado, M6, modificación de M1, BD-005.2, V001–V009, tablas, fórmulas y permisos nuevos. Sin PR funcional. Cuatro capas y módulos M1–M7 se conservan.

## 8. Verificación y siguientes pasos

La comprobación del diff entre c52ae9e y f1d0c2c no detectó cambios en código productivo M1/M3/APP, POM o migraciones. Se validó sintaxis de postman/build_collection.mjs con node --check y se analizaron los dos JSON de colección/entorno existentes con JSON.parse; sin modificar ni ejecutar peticiones o seeds contra un backend. Las pruebas dinámicas Postman no se declaran ejecutadas.

Regresión nueva ejecutada sobre `f1d0c2c03cac5f9091e5ee9063f02c65acdec333`:

```powershell
.\mvnw.cmd -B -pl app -am clean verify -Pdocker-it "-Dtest=M3CaseAuthorizationAdapterTest,M3CasoCreationTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=M3CasoCreationIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

**BUILD SUCCESS**, exit 0; reactor integrado M1–M3–APP satisfactorio, contexto Spring completo con PostgreSQL 16.15. Nueve pruebas M3CaseAuthorizationAdapterTest, seis M3CasoCreationTest y nueve M3CasoCreationIT: 15 unitarias y 9 IT aprobadas, cero FAIL/ERROR/SKIPPED. Verifica proveedor único, identidad/propietario/permiso de creación, fail-closed y persistencia POST. Duración 3:40 min; finalizó el 5 de noviembre a las 22:39:33 Bogotá.

No se sustituyen estos resultados por los históricos de c52ae9e ni se presentan como ejecución de GET. Los filtros Maven seleccionan las clases pertinentes; no certifican todas las suites de otros módulos. No se usó skipTests ni se modificaron pruebas para pasar. El commit posterior es exclusivamente documental, sin nuevo candidato funcional ni razón para repetir esta regresión. GET permanece DISEÑADO / NOT RUN.

Próximos pasos autorizados: revisar esta propuesta con Dirección y Arquitectura, cerrar decisiones pendientes y conservar la matriz. Solo después de autorización separada podrá programarse el GET. **Dictamen: BLOQUEADO PARA IMPLEMENTACIÓN; preparación documental DC-M3-GET-01 completada.**
