# M6-INC-02 — Modelos estructurales

Implementación autorizada por Integrante 2. Rama confirmada mediante lectura de
.git/HEAD: feature/m6-evaluacion-calificaciones; base 2c96c91 reportada, sin ejecutar
Git. Solo se crean dos records, dos archivos de pruebas y este documento en M6.
No hay cambios en POM, migraciones, otros módulos ni infraestructura.

## Correspondencia física

Fuentes: V001–V009 locales; estructura V001, comentario de nulidad V002 y triggers
de correspondencia/inmutabilidad V003. No hay algoritmo de evaluación.

| Tabla / columna | SQL | Componente Java / nulidad |
| --- | --- | --- |
| Calificacion.idCalificacion | uuid PK NOT NULL | UUID obligatorio |
| Calificacion.idIntento | uuid NOT NULL, UNIQUE, FK Intento | UUID obligatorio |
| Calificacion.idRubrica | uuid NOT NULL, FK Rubrica | UUID obligatorio |
| Calificacion.valor | numeric(8,2) NOT NULL | BigDecimal obligatorio |
| Calificacion.fechaCalculo | timestamptz NOT NULL DEFAULT now() | Instant obligatorio explícito |
| Calificacion.detalleEvaluacion | jsonb NOT NULL | String obligatorio, documento opaco |
| Calificacion.versionPolitica | varchar(50) nullable | String nullable, hasta 50 puntos de código |
| RetroalimentacionGeneral.idRetroalimentacion | uuid PK NOT NULL | UUID obligatorio |
| RetroalimentacionGeneral.idParticipacion | uuid NOT NULL, UNIQUE, FK Participacion | UUID obligatorio |
| RetroalimentacionGeneral.idDocente | uuid NOT NULL, FK Usuario | UUID obligatorio |
| RetroalimentacionGeneral.contenido | text NOT NULL | String obligatorio |
| RetroalimentacionGeneral.fechaRegistro | timestamptz NOT NULL DEFAULT now() | Instant obligatorio explícito |

## Validaciones y límites

Los campos obligatorios nulos producen NullPointerException mediante
Objects.requireNonNull; capacidad física inválida produce IllegalArgumentException.
No se inventan códigos públicos de errores ni se crea una jerarquía adicional.

NUMERIC(8,2) admite seis dígitos enteros y dos decimales, entre -999999.99 y
999999.99. Se exige representabilidad exacta y se conserva el BigDecimal original,
incluida su escala: 12.500 es válido; 12.501 se rechaza, no se redondea. Se admiten
cero y negativos: SQL no define un CHECK de positividad para Calificacion.valor.
No se reutiliza la validación positiva de RubricaPregunta de M3 porque sería una
regla adicional incorrecta. No se utilizan double, float ni fórmulas académicas.

Instant transporta el instante, sin inventar zona horaria ni fecha actual. Se
rechazan fracciones no representables exactamente en microsegundos PostgreSQL,
en lugar de truncar o redondear. Esta es una validación de fidelidad física, no
una política académica. No se pretende validar aquí todo el rango temporal ni
valores especiales infinity de PostgreSQL. No se inventan fechas por defecto:
el DEFAULT SQL no se ejecuta en el constructor; no hay aún un modelo de alta ni
un adaptador de persistencia. Ambos records requieren fechas suministradas.

JSONB se representa mediante String inmutable, sin parsear, reordenar, recortar,
serializar de nuevo ni definir estructura académica. La referencia Java null se
rechaza; el documento JSON "null" es un valor distinto y se conserva. No hay
parser disponible en M6: la sintaxis JSON y compatibilidad completa con JSONB se
deberán verificar en una frontera futura autorizada. Por tanto un String aceptado
no certifica por sí mismo un documento JSONB válido. No se construye un parser
improvisado ni se agregan dependencias para resolverlo.

contenido acepta cadenas vacías/blancas porque NOT NULL no establece nonblank.
versionPolitica acepta null y cadena vacía; se cuentan puntos de código Unicode,
no unidades UTF-16. No se comprueba existencia/ratificación de la política.

## Integridad e inmutabilidad

Los records y sus componentes estándar son inmutables; no hay setters ni métodos
de actualización/eliminación. Esto representa la calificación histórica sin
habilitar modificaciones: V003 prohíbe UPDATE y DELETE de Calificacion.
La inmutabilidad del record de retroalimentación no declara una prohibición SQL
de editarla; ninguna política de su ciclo de vida se implementa en este incremento.

PK, UNIQUE, FK con ON DELETE RESTRICT y el trigger que comprueba que la rúbrica
pertenece al caso evaluado siguen siendo responsabilidad de PostgreSQL y de futuras
unidades de trabajo. Un record aislado no puede demostrar esas relaciones ni la
unicidad. No hay JPA, asociaciones, repositorios, servicios, Strategy, Proxy o REST.

## Pruebas preparadas y bloqueo de ejecución

CalificacionTest: siete métodos para conservación de campos y JSON, todos los
nulos obligatorios, límites físicos/negativos/cero, ausencia de redondeo, Unicode,
precisión temporal y estructura inmutable. RetroalimentacionGeneralTest: cuatro
métodos para conservación literal, nulos obligatorios, precisión e inmutabilidad.
No son pruebas PostgreSQL y no certifican triggers ni persistencia.

En la implementación inicial M6 no declaraba JUnit ni dependencias de prueba.
Ese bloqueo motivó la autorización posterior registrada a continuación; se conserva
como antecedente, no como estado actual del POM.

No se ejecutaron Git, Maven, Docker, compilaciones ni pruebas. No se declaran
aprobadas. Este incremento no permite todavía afirmar BUILD SUCCESS de test para M6.

### Incorporación autorizada de dependencia de pruebas

Dirección Técnica autorizó agregar exclusivamente spring-boot-starter-test con
scope test si faltaba. La inspección estática confirmó que m6/pom.xml no tenía
dependencias y el POM raíz tampoco declaraba dependencias heredables. Los POM
locales de spring-boot-starter-parent y spring-boot-dependencies 4.1.2 tampoco
declaran dependencias directas: dependencyManagement administra versiones, no
incorpora bibliotecas al classpath. Por ello no había una ruta transitiva declarada
que aportara JUnit a M6. Las dependencias de otros módulos no se heredan por ser
parte del mismo reactor.

Se incorporó una sola declaración en m6/pom.xml:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Sin versión explícita: el BOM local de Spring Boot administra el starter 4.1.2 y
JUnit Jupiter 6.0.3. El POM local del starter declara junit-jupiter transitivo;
scope test en M6 limita esa ruta al classpath de pruebas. Los imports existentes
org.junit.jupiter.api.Test y Assertions corresponden a esa API. El BOM administra
maven-surefire-plugin 3.5.6; M6 y el POM raíz no añaden configuración propia de
Surefire. No se modificaron plugins, modelos, pruebas ni dependencias productivas.
No se agregaron JPA, PostgreSQL ni Testcontainers.

La comprobación utiliza archivos POM del proyecto y del repositorio local .m2,
no una ejecución de Maven. No certifica perfiles externos, opciones de invocación,
resolución de artefactos ni el POM efectivo final. Los once métodos de prueba
siguen sin compilarse/ejecutarse en esta intervención; no se declara aprobación.

Verificación manual desde PRISALY-Backen:

```powershell
.\mvnw.cmd -pl m6 help:effective-pom
.\mvnw.cmd -pl m6 dependency:tree "-Dscope=test"
.\mvnw.cmd -B -pl m6 -am test
```

Las ejecuciones pueden requerir acceso al repositorio Maven. No se ejecutaron
Git, Maven, Docker, compilaciones ni pruebas para realizar esta incorporación.

## Decisiones pendientes

DD-03: fórmula, ponderaciones, escala, redondeo y cobertura de evaluación.
Contratos M3/M5 de contexto académico e intento finalizado; permisos M1;
idempotencia, estados de evaluación, reevaluación/corrección histórica y liberación
de resultados. La dependencia de pruebas ya fue autorizada; siguen pendientes
persistencia y siguientes incrementos. No se avanza a Strategy ni Protection Proxy.
