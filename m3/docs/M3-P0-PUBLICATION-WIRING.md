# P0 — Registro automático prematuro de publicación

Referencia de auditoría reportada: 1960eff. Rama comprobada por lectura de HEAD:
feature/m3-gestion-casos. Registro de corrección pendiente de validación manual.

## Causa y cambio mínimo

CasoPublicationAdapter tenía @Repository y era descubierto por el escaneo de app
desde com.prisaly. Su constructor exige CaseAuthorizationPort, pero no se encontró
un proveedor productivo registrado. Las implementaciones de prueba no satisfacen
esa dependencia en el contexto completo. Falta cotejar el primer stack trace de
la ejecución reportada para atribuir formalmente los 26 errores a esta causa.

Se retiran exclusivamente @Repository y su import de CasoPublicationAdapter.
El adaptador conserva el constructor obligatorio, rechazo de authorization null,
autorización, REQUIRED, comprobación de aislamiento, m3-v2, guards F3-03, SQL,
validaciones académicas, relectura y rollback. No hay dependencia opcional, bypass,
mock productivo ni autorización permisiva por defecto.

## Archivos afectados y pruebas

- `src/main/java/com/prisaly/m3/infrastructure/persistence/jpa/CasoPublicationAdapter.java`:
  eliminación del estereotipo y su import, sin otros cambios productivos.
- `src/test/java/com/prisaly/m3/infrastructure/persistence/jpa/CasoPublicationContractTest.java`:
  dos pruebas adicionales. El scanner Spring predeterminado no descubre el adaptador
  ni un componente alternativo M3 que implemente los puertos de publicación o
  autorización; el único constructor sigue exigiendo el puerto y rechaza null.
- `docs/M3-P0-PUBLICATION-WIRING.md`: este registro.

Se reutilizan sin duplicación missingCaseAndDeniedAuthorizationUseExistingErrors
y publicationAuthorizationPrecedesStalePreconditionAndDoesNotWrite de
CasoPersistenceAdapterIT. Esta última verifica rechazo FORBIDDEN sin cambios
persistidos. La regresión mantiene el rechazo de rúbrica incompleta y no crea un
caso de éxito artificial.

La prueba del scanner cubre componentes auto-detectables de M3; no acredita toda
configuración externa futura o un @Bean definido en app. El contexto completo debe
validarse por separado. Los mocks añadidos se limitan a src/test.

## Límite funcional y ensamblaje pendiente

La publicación no queda disponible automáticamente en Spring. Su construcción
explícita requiere el proveedor institucional real. Los consumidores obligatorios
que se configuren sin él deben seguir fallando de forma segura, sin sustitutos.

M1 debe aportar las garantías de identidad y permisos ratificados; la política
de propiedad del caso corresponde a la frontera de recurso aprobada. app deberá
implementar y registrar el adaptador de CaseAuthorizationPort y ensamblar el
publicador y su servicio mediante configuración explícita autorizada. No se cambia
M1, M6, app, POM ni contratos en esta corrección. AutorizacionContextoAcademicoPort
no sustituye permisos de publicación. DD-03 sigue bloqueando el recorrido académico
de éxito donde Rubrica.isComplete no puede satisfacerse desde la rúbrica persistida.

## Evidencia pendiente

No se ejecutaron Git, Maven, Docker, compilación ni pruebas. No se declara resuelto
el P0, recuperadas las 26 pruebas ni arranque confirmado. El Integrante 2 deberá
registrar resultados, entorno y primer error real si persiste el fallo.

Desde PRISALY-Backen, validación manual:

```powershell
mvn -pl m3 -am test
mvn -pl m3 -am -Pdocker-it verify
mvn -pl app -am -Pdocker-it verify
```

La última ejecución cubre los contextos de integración disponibles de app; contrastar
sus clases y conteos con las 26 pruebas de la auditoría. No eliminar, omitir ni
relajar pruebas para conseguir arranque.
