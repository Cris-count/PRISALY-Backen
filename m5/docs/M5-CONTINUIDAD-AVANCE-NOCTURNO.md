# M5 | Informe de continuidad del avance nocturno

Estado al 2026-11-01. Responsable: Integrante 4.

- Rama: `trabajo/m5-avance-nocturno`, creada desde `747a2a4`.
- Los cambios están solo en `m5/`; el resto del árbol es idéntico a `747a2a4`.
- No se publicó nada.

**Integración funcional no terminada.** M5 ya verifica identidad (M1) y
titularidad (M4) en un proxy de protección, pero:

- ningún estudiante accede mientras DD-02 siga abierta;
- no hay endpoints de estudiante (falta ARQ-008.3);
- iniciar, reanudar y repetir siguen sin implementar (M4 y DD-14).

## 1. Commits

| Commit | Contenido |
|---|---|
| `eb4a591` | Adaptador `IdentidadEstudianteDesdeM1` (parche 0001) y dependencia directa de `m1` |
| `9dff295` | Contrato `com.prisaly.m5.api.HistorialParticipacionPort` para M4 y su implementación JDBC |
| `fae45d5` | Titularidad sobre M4, proxy `AccesoProtegidoParticipacion` y sus pruebas |
| `7e7efc1` | Prueba de concurrencia de actualizaciones sobre una misma respuesta |
| (último) | Documentación: este informe y la actualización de los documentos de M1, M4 y REST |

## 2. Funcionalidad desarrollada

**Cadena de un estudiante** (`AccesoProtegidoParticipacion`, patrón Protection Proxy):

1. `IdentidadEstudianteDesdeM1` obtiene `studentId` de
   `StudentAccessPort.requireCurrentStudent()`. Nunca usa `userId` ni datos del
   cliente; cualquier `IdentityDomainException` es "sin identidad".
2. `TitularidadIntentosDesdeM4` resuelve Intento → Participacion en tablas de
   M5 y pregunta a M4
   (`ProgramacionParticipacionPort.consultarConvocatoria(convocatoria, studentId)`).
   M5 no lee `"Convocatoria"`. Además comprueba que el contexto devuelto
   coincide con lo consultado.
3. `ControlAccesoIntentos` falla de forma cerrada:

   | Resultado | Excepción |
   |---|---|
   | Sin identidad | `EstudianteNoIdentificadoException` |
   | Intento inexistente | `IntentoNoEncontradoException` |
   | Intento ajeno o respuesta nula | `AccesoIntentoDenegadoException` |
   | M4 caído | Se propaga `ConsultaProgramacionNoDisponibleException` (503, sin causa SQL) |

4. Solo entonces delega en `FachadaParticipacionTransaccional`.

**Otras piezas:**

- **Interfaz `OperacionesParticipacion`:** la implementan la fachada, el
  decorador transaccional y el proxy, para componerlos sin que la aplicación
  dependa de la infraestructura.
- **Verificación antes de la transacción:** es segura porque la cadena de
  titularidad es inmutable una vez existe la participación. Se comprobó en
  PostgreSQL 16.15: los triggers rechazan cambiar `Convocatoria.idEstudiante`,
  `Participacion.idConvocatoria` e `Intento.idParticipacion`.
- **`HistorialParticipacionPort`:** M4 lo necesita para su
  `ParticipacionesPort`. Recibe convocatorias, no una sesión, para no leer
  tablas de M4. Exige la transacción del llamador.

## 3. Pruebas

| Prueba | Tipo | Casos |
|---|---|---|
| `IdentidadEstudianteDesdeM1Test` | Unitaria | 9: titular, no titular, sin identidad, DD-02 real, token de personal, fallo del proveedor, respuesta incompleta, sin parámetros del cliente, intento desconocido |
| `AccesoProtegidoParticipacionTest` | Unitaria | 7: delegación del titular; ajeno, sin identidad e inexistente en las 4 operaciones sin delegar; sin identidad como parámetro |
| `TitularidadIntentosDesdeM4Test` | Unitaria | 8: propio, ajeno, inexistente, estudiante no uuid, contexto incoherente, respuesta nula, M4 caído, fallo SQL sin filtración |
| `ControlAccesoIntentosTest` | Unitaria | +3: M4 caído, respuesta nula, consulta con el estudiante autenticado |
| `AccesoEstudianteM4M5IT` | PostgreSQL 16.15 | 9: titular opera; suplantación sin escrituras; estudiante inexistente; intento inexistente; sin identidad; M1 real (DD-02); M4 caído (503, sin SQL, sin escrituras); historial; historial sin transacción |
| `RepositorioIntentosJpaIT` | PostgreSQL 16.15 | +1: dos actualizaciones simultáneas de la misma respuesta → revisión 3 |

Se adaptaron dos pruebas existentes a la nueva firma de `TitularidadIntentos`,
conservando sus escenarios: `ControlAccesoIntentosTest` y
`IdentidadEstudianteDesdeM1Test`. No se eliminó ni se desactivó ninguna.

## 4. Pruebas ejecutadas en esta sesión (no equivalen a Maven)

- **Compilación y unitarias:** M5 compila con `javac --release 21 -Werror`, y
  JUnit 5.10.2 aprueba **140/140** unitarias.
  - Excluidas: las IT, `ProgramacionM4ErroresHttpTest` y
    `SolicitudRespuestaProvisionalValidacionTest`, que necesitan librerías no
    disponibles en este entorno.
  - Esperado con Maven: **154** = 127 de `747a2a4` + 27 nuevas.
- **Compilación de las IT:** solo fallan por las librerías ausentes
  (Testcontainers, Spring Boot Test). No hay ningún error de símbolos de M1,
  M4 ni M5.
- **SQL en PostgreSQL 16.15 aislado (V001–V009 y el fixture de M5):**
  - consulta propia de titularidad;
  - `= ANY(uuid[])` del historial;
  - consulta de M4 con el titular (1 fila) y con otro estudiante (0 filas);
  - inmutabilidad de la cadena;
  - el fixture con la nueva `opcion1c`.
- **No se ejecutó** Maven, Testcontainers ni el arranque de `app`: no hay
  acceso a Maven Central ni a Docker.

## 5. Verificación pendiente en Windows

```powershell
git switch trabajo/m5-avance-nocturno
.\mvnw.cmd -pl m5 -am test                  # esperado: 154 en M5
.\mvnw.cmd -pl m5 -am -Pdocker-it verify    # esperado: 30 IT en M5 (20 + 1 + 9)
.\mvnw.cmd -pl app -am test
.\mvnw.cmd -pl app -am -Pdocker-it verify   # carga el contexto completo con los beans nuevos
```

El último comando es importante. `PersonalAuthSecurityIT` levanta la
aplicación completa, y ahora M5 registra beans que necesitan:

- `StudentAccessPort` (M1, `DenyingStudentAccessService`);
- `ProgramacionParticipacionPort` (autoconfiguración de M4);
- `JdbcTemplate`.

## 6. Dependencias pendientes

| Con | Pendiente |
|---|---|
| M1 / Dirección Central | DD-02: autenticación real del estudiante. Hasta entonces el proxy siempre deniega |
| M4 | Adaptador de `ParticipacionesPort` sobre `HistorialParticipacionPort`, que debe obtener las convocatorias de la sesión. Activar `prisaly.m4.consulta-bd0052.enabled` en el ensamblado (sin ello, la titularidad responde 503). Contrato de elegibilidad, ventana y límite temporal |
| M4 / M5 | Protocolo de bloqueo entre editar una sesión y el futuro inicio de intentos |
| M3 | Contrato de contenido para M5 (preguntas y opciones sin respuestas correctas) |
| M6 | Contrato para recibir un intento finalizado |
| Arquitectura | ARQ-008.3 (rutas y DTO); decisiones 4 (ajeno: 403 o 404) y 7 (identidad no disponible: 401, 403 o 503) |

## 7. Decisiones bloqueadas

- **DD-02**, autenticación del estudiante.
- **DD-14**, repetición.
- **Catálogo de `motivoFinalizacion`.**
- **Permitir cambiar una respuesta:** sigue provisional.
- **Titularidad frente a elegibilidad:** una convocatoria retirada sigue
  devolviendo PROPIO, porque M4 entrega `fechaRetiroConvocatoria` como dato y no
  como permiso. Decidir si un retiro bloquea consultar o responder es una
  política académica pendiente.

## 8. Funcionalidades que siguen deshabilitadas

- Endpoints de estudiante de M5: ninguno.
- Iniciar, reanudar y repetir intentos.
- Uso del reloj de PostgreSQL como reloj académico (`TIEMPO_VENCIDO`).
- Consumo de `HistorialParticipacionPort` por M4.

## 9. Riesgos

- **Beans intercambiables:** tres beans implementan `OperacionesParticipacion`
  (la fachada, el decorador y el proxy). Un controlador de estudiante debe
  inyectar `AccesoProtegidoParticipacion` por su clase, nunca la interfaz.
- **Mensaje de 404:** `IntentoNoEncontradoException` repite en el mensaje la
  referencia que envió el cliente. No es información técnica, pero conviene
  revisarlo cuando haya API.
- **Sin rutas no hay superficie de ataque nueva.** Al exponer endpoints, la
  cadena de seguridad de `app` debe exigir autenticación (hoy
  `anyRequest().authenticated()`).

## 10. Revisión e integración

1. Revisar `git log 747a2a4..trabajo/m5-avance-nocturno` y
   `git diff 747a2a4 trabajo/m5-avance-nocturno -- m5`.
2. Ejecutar los cuatro comandos de la sección 5.
3. Si pasan, y con autorización:
   `git switch integracion/m5-develop` y
   `git merge --ff-only trabajo/m5-avance-nocturno`.
   Después, la rama M5 avanza igual que antes, sin force push.
