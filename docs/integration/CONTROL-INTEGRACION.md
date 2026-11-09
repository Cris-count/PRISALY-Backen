# Control de integración

Este documento complementa el registro único `docs/DC-INTEGRACION-TOTAL-01.md`; no sustituye actas de ratificación.

## Recorridos y dependencias

| Recorrido | Reutilización y estado | Límite que impide cerrarlo |
|---|---|---|
| Docente autenticar y crear borrador | PersonalAuthenticationService, CasoCreationController y autorización CREAR_CASO existentes | Alta institucional de usuarios sigue sin contrato HTTP aprobado |
| M3 contenido, preguntas, opciones y rúbrica | CasoUpdatePort / CasoCommandUpdateAdapter y agregados existentes | Contratos HTTP de contenido y política institucional de modificación no ratificados; no inferir permisos |
| M3 GET/PATCH | Controladores existentes y lectura parametrizable; PATCH conserva denegación | Permisos/visibilidad/estados editables pendientes; no se modifican por inferencia |
| Publicación/archivado | CasoPersistentPublicationService y validaciones reutilizables | Publicación persistente depende de equivalencia de completitud entre rúbrica legacy y estructura SQL; no inventar criterios DD-03. Archivado sin política contractual aprobada |
| M4 programación y convocatoria | Integración existente y D-06 conservadas | Programar un Publicado de fixture demuestra consumo, no publicación HTTP |
| M5 identidad y recursos propios | Puerto M1 y proxy M5 existentes; A02 permite cookie sin suplantación institucional en fixture controlado | DD-02 pendiente; elegibilidad/límite de inicio y D07 sin ratificación; POST siguen sin registrarse |
| M6 evaluación, nota y resultado | Preparación, contexto protegido, lectura de rúbrica/calificación existentes | Fórmula/escala/redondeo DD-03, autorización institucional y escritura/publicación no aprobadas. No añadir un escritor sin consumidor autorizado |
| M7 avisos y correo | Registro transaccional existente; A04 corrige recuperación SMTP | Worker opt-in deshabilitado; SMTP externo y mecanismos de acceso pendientes |
| M2 | Descriptor provisional sin lógica | Sin ámbito funcional definido: no trasladar responsabilidades de otros módulos para aparentar implementación |

Las capacidades pendientes no se reconstruyen ni se duplican. El catálogo encontrado contiene CA propuestos y la documentación ARQ005 señala contratos borrador/no implementados. No constituyen aprobación de endpoints nuevos. DD-04 conserva el último intento autorizado y evaluado.

## Persistencia

No cambia ningún script histórico ni candidato V011/V012. El runtime por defecto sigue con V001–V009 y contrato M4 `bd0052-v009`; la suite estudiantil aplica explícitamente las candidatas en una base nueva con contrato `bd0052-dd02-v012`. Esto no autoriza su uso compartido. No hay entorno compartido identificado ni historial Flyway entregado; la conciliación por versión/script/checksum/resultado de esos entornos sigue como dependencia externa. No se usa `repair`, `target=9` ni una aceptación por contador de tablas.

## Seguridad y despliegue

Origen de desarrollo documentado por frontend: `http://localhost:4200` hacia `http://localhost:8080/api`, mismo sitio con puertos distintos. Se conserva SameSite Strict y HttpOnly. En producción frontend propone `/api` mediante proxy del mismo origen; requiere decisión de despliegue. No se habilita SameSite None ni comodines con credenciales. Para DD-02 controlada las operaciones POST/PUT necesitan `Origin` permitido; el encabezado nunca sirve de identidad. La cookie sigue usando Secure según HTTPS observado por APP: el proxy debe transmitir el esquema de forma confiable, sin confiar indiscriminadamente en cabeceras externas.

## Arquitectura y patrones

Se conserva el monolito modular, las capas y el gestor transaccional existente. No hay nuevas entidades, repositorios, mecanismos D-06 o servicios de identidad paralelos. La autorización HTTP estudiantil reutiliza StudentAccessPort y no escribe un principal de personal.

| Patrón/mecanismo observado | Evidencia existente | Estado y límite |
|---|---|---|
| Builder | M3 CasoBuilder | Construcción de borradores existente |
| Composite | Jerarquía de contenido M3 documentada en el módulo | No cambia este incremento; no se certifica un catálogo oficial por nombre |
| Facade | M5 ParticipacionFacade | Integración existente detrás del proxy protegido |
| Chain of Responsibility | Cadena de validación de acceso M5 | Elegibilidad no ratificada conserva bloqueo |
| State | M4 SesionState, SinIniciar/EnCurso/Finalizado | Existente; no cambia el efecto académico del cierre |
| Adapter | M7 JavaMailProveedorCorreoAdapter y NotificacionM4Adapter | Integración existente; no contar todos los repositorios como GoF |
| Observer | M7 contratos/estructura y M7ObserverTest | Estructural probado; el recorrido productivo usa también coordinación directa |
| Proxy | M6 EvaluacionProtectionProxy, preparación protegida | Parcial: sin cálculo autorizado ni escritura |
| Strategy | M6 EstrategiaEvaluacion | Contrato, sin algoritmo productivo DD-03 |

La orden comunica ocho patrones; el documento local de patrones selecciona cinco conceptuales y el documento general advierte criterios diferentes para Adapter. Este inventario de mecanismos no pretende resolver esa discrepancia ni declarar nueve patrones oficiales. No se añaden patrones para alcanzar una cantidad.

Antipatrones: no se demuestra una God Class nueva ni persistencia paralela en el diff. Se corrigen un almacén sin reclamación de recursos (A01), una dependencia de orden de filtros (A02) y una pérdida de clasificación persistida (A04). La clase HttpApiConfig mantiene configuración de transporte y delega la identidad a M1; no calcula reglas académicas. La matriz de dependencias Maven debe verificarse como DAG en la validación final. Las pruebas existentes verifican proveedores institucionales únicos.

## Frontend/SIM-01 recuperado

Se recuperó `origin/feature/simulador-2d` en un worktree separado, rama `integration/sim01-existente`, conservando autoría y sin fusionar. Angular 22.2; build aprobado con Node 24.15.0, comprobación de URLs aprobada y 70 pruebas ChromeHeadless aprobadas. El Node local 24.13.1 no cumple el mínimo de Angular: se usó un ejecutable aislado obtenido mediante npm, sin actualizar el sistema.

La ruta/escena demostrativa existente se conserva; ParticipacionPendienteGateway continúa fallando explícitamente cuando faltan contratos. No fabrica respuestas ni calificaciones. La demostración visual no prueba integración M5. Arranque reproducible en el worktree frontend:

```powershell
npm ci
npm exec --yes --package=node@24.15.0 -- node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1
```

## Pruebas y alcance de evidencia

Reactor local secuencial: `mvnw -B -pl app -am clean verify`, aprobado. No ejecuta PostgreSQL. Cuatro pruebas auxiliares Node aprobadas; no son escenarios HTTP. El workflow aislado `integration-backend.yml` ejecuta el reactor con PostgreSQL 16.15, arranque HTTP estudiantil controlado, 32 escenarios HTTP existentes y 13 peticiones seleccionadas de Postman/Newman. Los resultados deben obtenerse del artefacto asociado al SHA, no de este plan.

Exclusión explícita: diagnóstico archivado `Bd0052BaselineIT#diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial`; no es una prueba aprobada ni omisión JUnit. Los tres métodos canónicos de esa suite se incluyen. No se ocultan errores ni se presentan mocks como persistencia real.

Los 21 CU históricos se inventarían en `PLAN-21-CU.csv`, excluyendo CU-04 por integración en CU-11. Los CA completos no están recuperados; por eso sus resultados esperados y sus relaciones RF-CU no se inventan. El plan no equivale a ejecución completa. `v27-trazabilidad.json` conserva las relaciones ARQ documentales como tales, con advertencias históricas.
