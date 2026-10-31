# Resultados ejecutados — 31/10/2026

Regresión completa: **BUILD SUCCESS**, 207 pruebas, 0 fallos, 0 errores, 0 omitidas. Finalizó a las 15:48:39 America/Bogota, duración 2:49. M4: 157 ejecuciones (117 Surefire y 40 Failsafe). Surefire incluye una comprobación Docker, por lo que no todas sus ejecuciones son unitarias puras. Ver [conteo](conteo.md) y [suites/casos sin propiedades del entorno](suites.json).

Se ejecutó `mvnw.cmd verify -Pdocker-it` con la ruta absoluta de las migraciones de app. Compilaron los nueve proyectos del reactor. M2/M5/M6/M7 de esta rama no aportan suites: su éxito de ensamblado no demuestra integración funcional. No se suman las 140 pruebas históricas de M5 aislado a estas 207.

## Persistencia y reglas reales

- PostgreSQL **16.15**, nueve migraciones oficiales y exactamente **21 tablas** de negocio; `ddl-auto=validate` compatible con JPA M4.
- Suite SesionAcademicaPostgreSqlIT: **23 pruebas**. Escritura y lectura del mismo registro en Sesion/Convocatoria y contexto público M5 coherente.
- RF-09: Borrador e inactivo rechazados; Publicado/activo programado. F3-03: marca persistida y modificación de contenido evaluable bloqueada por triggers reales.
- Reasignación confirma bloqueo del destino; Borrador y destino duplicado rechazados; convocatorias protegen el origen.
- Rollback real de sesión y marca F3-03, comprobado desde conexión posterior; rollback de reasignación y marca destino.
- Diez creaciones simultáneas: **1 éxito, 9 duplicados esperados, 0 errores inesperados**. Carrera convocatoria/inicio serializada por lock de Sesion.
- Participacion e Intento reales impiden edición de fechas mediante el proveedor de historial exclusivo de pruebas. No certifica un adaptador productivo M5.
- HTTP real de creación con alias descripcion, duración y zona; conflicto de actualización devuelve409 y conserva el registro.
- Pruebas M1/M4 reales: seis escenarios de identidad, propietario y denegación; otras suites cubren proveedores ausentes y acceso estudiantil provisional.
- La reproducción explícita de V010 ocurre solo en un contenedor de prueba: produce23 tablas y la guardia la rechaza. No se aplicó a una base compartida.

## M4–M5 actualizado

Se repitieron **6 comprobaciones** con clases compiladas de la copia aislada M5 `3fc823e`, puertos M4 actuales y PostgreSQL16.15. Ver [salida resumida](contrato-m4-m5.txt). Identidad de estudiante fixture; no se inicia ni reanuda un intento productivo.

Se confirma titular correcto, inexistente404, ajeno404 por filtrado M4 (pendiente ratificación frente al403 provisional), sin identidad401 y denegación DD-02 con proveedor real M1. También se confirma un defecto externo: TraductorErroresM5 no traduce ConsultaProgramacionNoDisponibleException a503. No se cambió M5 para ocultarlo.

Fuente reproducible: `M4M5ConsoleProbe.java` en el directorio superior. Requiere obtener M5 `3fc823e` en una copia aislada y compilarlo. Se ejecuta con Java21, classpath de pruebas M4 más `m5/target/classes` de esa copia, pasando el directorio oficial de migraciones como primer argumento. No requiere cambios de rama ni copiar implementaciones de M4 a M5.

## Paquete y verificación estática

Tras formatear únicamente los Java modificados de M4 con google-java-format1.24.0, `mvnw.cmd package -DskipTests` recompiló y empaquetó correctamente; no se cuenta como otra ejecución de tests.

check-package.ps1 aprobó: JAR M4 local idéntico al incluido en app, cero stubs/clases de prueba, cero V010 automáticas, una copia histórica idéntica al fuente, cero clases de estados retirados y tres autoconfiguraciones registradas. Ver [resultado](package-check.txt).

`git diff --check` aprobado. Sin diferencias en otros módulos ni V001–V009; V010 conserva blob18c4f98131d0caf5a0399b5139c7a3cbf37a3f09 y contenido fuente. Los contratos `m4/api` no cambian.

## Incidencias del entorno y límites

Un intento dentro del aislamiento se detuvo al cargar el agente Mockito; el siguiente no pudo conectar a Docker. La primera selección automática del agente tomó5.7.0 incompatible, se corrigió a5.23.0, pero Docker siguió inaccesible dentro del aislamiento. La ejecución final completa fuera de ese aislamiento pasó usando la configuración habitual, sin cambiar pom de otros módulos.

No se versionan logs crudos ni XML con propiedades del entorno. Los registros locales completos son m4-conciliacion-final-host.log y m4-conciliacion-package.log. Los fallos de entorno no se presentan como pruebas aprobadas.

El candidato sigue pendiente de historial compartido V010, decisiones académicas, adaptadores M5/M7 productivos y revisión de Arquitectura. No certifica despliegue ni integración completa M1–M7.
