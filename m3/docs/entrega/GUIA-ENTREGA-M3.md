# Guía de preparación del entregable académico M3

Fecha: 4 de noviembre de 2026. No se crea ZIP, PR ni commit en esta preparación.
Las rutas del paquete se conservan relativas a la raíz del backend.

## Versión y alcance que debe declarar la portada

- Rama: feature/m3-gestion-casos, confirmada mediante lectura de .git/HEAD.
- Referencia de la rama leída del archivo local: e3d7352cfe7a9acb6a8f2f982e63665ab21b0a39.
- Existen cambios locales posteriores sin confirmar: el SHA no identifica por sí
  solo el paquete actual ni la versión del backend de cada ejecución.
- Maven 132/71: sin SHA/artefacto verificado de esa corrida reportada.
- Newman 13/39: versión exacta del backend no verificada en aquella corrida.
- Bruno: referencia histórica HEAD ac32db0 más cambios y hash de JAR en su
  manifiesto; no transferirla a Maven/Newman ni al paquete actual.
- No se ha verificado aquí un develop actualizado ni su arranque. DC-INT-02
  permanece pausado. No presentar el paquete como producto final integrado.

## Archivos que incluir

| Grupo | Archivos y directorios | Observaciones |
| --- | --- | --- |
| Fuente M3 | m3/src/main/java/ | Todas las capas, conservando rutas |
| Pruebas M3 | m3/src/test/java/ | Distinguir cobertura existente y corridas; no prometer publicación persistente positiva |
| Construcción | m3/pom.xml | Sin modificar; requiere el proyecto multi-módulo y POM padre del equipo |
| Documentación | m3/docs/ y m3/DOCKER-TESTS.md | Incluye los tres documentos de entrega y referencias originales |
| Clientes | bruno/m3-gestion-casos/ y bruno/exports/PRISALY_M3_Postman_Collection.json y PRISALY_M3_Postman_Environment.example.json | Únicamente ejemplos sin secretos; GET/PATCH no ejecutables |
| Evidencia Maven | m3/docs/entrega/evidencias/surefire/ y failsafe/ | Diez XML y siete XML depurados, más resumen 71; sin properties ni salida de consola; no incluir target |
| Evidencia Newman | m3/docs/entrega/evidencias/newman/newman-resultados-seguros-20261104.json | Copia permanente del reporte seguro temporal; original intacto |
| Evidencia Bruno histórica | m3/docs/entrega/evidencias/bruno/bruno-resumen-historico-20261104.json | Derivado con resultados, HTTP y aserciones; sin cabeceras/cuerpos del reporte nativo |
| Procedencia e inventario | evidencias/metadatos/procedencia.json, estado-local.txt, controles-consolidacion.json e INVENTARIO-PAQUETE-M3.csv | Hashes, alcance, límites y lista exacta; no prueban SHA de ejecución |

No modificar ni copiar código ajeno como si fuera autoría M3. El ensamblaje app,
M1, el padre Maven y las migraciones oficiales son dependencias de reproducción
del repositorio completo del equipo. Referenciarlas en la entrega y coordinar
su disponibilidad; no crear sustitutos ni duplicarlas dentro de M3.

Excluir .git, target completo, node_modules, entornos privados, .env locales,
contraseñas, tokens, cookies, cabeceras Authorization y logs crudos sensibles.
No abrir, sobrescribir ni incluir automáticamente PRISALY_XML_M3_M6.zip: es un
archivo previo protegido y no forma parte de una nueva generación autorizada.

## Ubicación de evidencia y calidad de trazabilidad

1. [M3-INC-13B](../M3-INC-13B.md): confirmación de Maven del Integrante 2, 132
   unitarias y 71 integraciones; 9 y 25 de las clases destacadas ya están incluidas.
2. [EVIDENCIA-POSTMAN](../../../bruno/m3-gestion-casos/EVIDENCIA-POSTMAN.md):
   corrida Newman, HTTP por solicitud, 39 aserciones y límites de versión; tabla
   Desktop pendiente separada. No fabricar capturas.
3. [DC-M3-REST-03](../DC-M3-REST-03.md): resultados Bruno y logs históricos,
   compilación/arranque de aquella versión y correcciones realizadas.
4. [DC-INT-02-PREPARACION](../DC-INT-02-PREPARACION.md) y su .patch: trabajo
   preparado, pausado, sin aplicación en develop y sin pruebas de ensamblaje nuevas.

La consolidación posterior copió el reporte seguro Newman y generó derivados
depurados de XML y Bruno. Los originales permanecen intactos. Ver el
[registro permanente](evidencias/README.md). Los XML corroboran 132/71; cuatro
reportes Failsafe de corridas anteriores quedaron fuera. Se conservan dos avisos
Hikari correlacionados con pruebas de agotamiento; otros avisos PostgreSQL y el
log completo de BUILD SUCCESS siguen pendientes. No clasificar sin contexto.

La lista exacta del paquete actual se encuentra en [INVENTARIO-PAQUETE-M3.csv](INVENTARIO-PAQUETE-M3.csv),
con rutas relativas, tamaños y hashes. No incluye fuentes de app ni otros módulos,
target, .git ni archivos de secretos. El inventario no se calcula hash de sí mismo.

## Reproducción manual opcional, no ejecutada en esta preparación

Se requiere Java 21, repositorio completo y dependencias Maven; PostgreSQL 16.15
de las IT se inicia mediante Testcontainers, con Docker disponible. Estos comandos
son una guía; no se presentan como los comandos exactos de una corrida cuyo
ejecutor no los aportó. Desde la raíz del backend:

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoPersistenceAdapterIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT,CasoCommandAddContentIT,CasoJpaAggregateReadIT,CasoJpaAggregateTransactionIT,RubricaJpaReadIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

La segunda selección no es toda la suite de integración. Otras selecciones o
cambios locales pueden producir conteos diferentes. Conservar salida, XML,
fecha, selección y versión; no sumar automáticamente distintas ejecuciones.

Para la colección: importar los dos JSON en Postman, seleccionar entorno y
configurar baseUrl con el prefijo /api y el puerto del backend realmente iniciado.
Usar tokens institucionales locales válidos y un UUID nuevo. No guardarlos en
el ejemplo versionado. Ejecutar 01 antes de 09; si 01 no obtiene un 201 validado,
no ejecutar 09. Ver las precondiciones en el README original de la colección.

Si ya existe Newman instalado, la reproducción manual puede usar un entorno
privado fuera del repositorio y detenerse ante el primer error:

```powershell
newman run bruno/exports/PRISALY_M3_Postman_Collection.json -e "<ruta-al-entorno-privado>" --iteration-count 1 --bail --reporters cli
```

No se instala ni ejecuta Newman ahora. El nombre newman puede no estar en PATH;
la instalación histórica fue temporal. El comando anterior no reconstruye el
orquestador programático original y no debe atribuirse como comando de aquella
corrida. No exportar informes nativos con secretos; seleccionar evidencia segura.
No iniciar ni corregir el ensamblaje de develop como parte de esta entrega.

## Cambios locales y comprobaciones antes de entregar

Último inventario de estado conocido, anterior a crear estos tres documentos
(no se ejecutaron comandos Git para actualizarlo en esta preparación):

Nota posterior: la consolidación autorizó Git de solo lectura y actualizó la
comprobación en evidencias/metadatos/estado-local.txt. El listado siguiente se
conserva como antecedente; los tres Markdown y las evidencias/inventario añadidos
también permanecen sin confirmar. No se realizaron operaciones Git de escritura.

- Modificados: dos tests app M3CasoCreationTest/M3CasoCreationIT; colección
  Postman; EVIDENCIA-POSTMAN; DC-M3-REST-03; M3-INC-13B; dos tests M3
  CasoPersistenceAdapterIT/CasoPublicationContractTest.
- Sin seguimiento en aquel inventario: ZIP protegido; DC-M3-REST-GET-PATCH;
  DC-INT-02-PREPARACION y DC-INT-02-retirar-config-antigua.patch.
- Esta preparación añade exclusivamente los tres Markdown de m3/docs/entrega/.
  No se afirma un nuevo estado del índice ni se modifica ninguno de esos archivos.

Lista de revisión manual del Integrante 2:

- Confirmar archivos reales del paquete, rama y cambios locales sin perder trabajo.
- Obtener la matriz oficial de requisitos asignados y criterios de aceptación;
  no declarar 40 % hasta poder calcularlo sobre esa base.
- Revisar las copias Maven 132/71 consolidadas, adjuntar comandos y log final de
  BUILD SUCCESS cuando estén disponibles y revisar los avisos con su contexto.
- Comprobar la copia permanente Newman; ya no depende únicamente de TEMP.
- Identificar el artefacto efectivamente iniciado para cualquier nueva corrida;
  no inferirlo del checkout ni cambiar retroactivamente las evidencias históricas.
- Si el docente exige Desktop, capturar solicitudes reales ocultando secretos.
- Marcar DC-INT-01/02, GET/PATCH, publicación DD-03, permisos no ratificados,
  copia persistida y archivado como pausados/pendientes, nunca completados.
- Mantener los cambios de tests app DC-INT separados: no fueron ejecutados tras
  su ajuste y no quedan acreditados por las corridas anteriores.
- Revisar datos sensibles antes de empaquetar y mantener rutas relativas. La
  entrega de fuentes de avance no certifica arranque del estado combinado.

No se prepara PR, no se ejecutan operaciones Git y no se realiza empaquetado
automático. Esta guía organiza evidencia existente y no continúa el desarrollo.
