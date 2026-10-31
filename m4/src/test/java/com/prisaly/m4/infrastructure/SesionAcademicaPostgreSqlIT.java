package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.prisaly.m4.TestM4Application;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.CasoAcademicoStubAdapter;
import com.prisaly.m4.infrastructure.adapter.IdentidadStubAdapter;
import com.prisaly.m4.infrastructure.adapter.ParticipacionesStubAdapter;
import com.prisaly.m4.infrastructure.persistence.JpaConvocatoriaRepository;
import com.prisaly.m4.infrastructure.persistence.JpaSesionRepository;
import com.prisaly.m4.infrastructure.persistence.SesionAcademicaEntity;

/**
 * Suite de integración de M4 contra PostgreSQL 16 real ejecutándose en Docker (Testcontainers).
 * 
 * Cubre las Fases 1 a 7 de la orden de validación:
 * - Fase 1: Conexión, contenedor y Flyway en PostgreSQL
 * - Fase 2: Creación, consulta y persistencia real de sesiones
 * - Fase 3: Validación exhaustiva de RF-09 (Borrador vs Publicado)
 * - Fase 4: Convocatorias y participaciones (relaciones y restricciones)
 * - Fase 5: Pruebas de rollback y transaccionalidad Spring
 * - Fase 6: Pruebas de concurrencia (duplicación, bloqueo optimista)
 * - Fase 7: Integración con contratos M1, M3 y M5
 */
@SpringBootTest(classes = TestM4Application.class)
@DisplayName("M4 — Integración Real con PostgreSQL 16 en Docker")
public class SesionAcademicaPostgreSqlIT {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("prisaly_test")
            .withUsername("prisaly")
            .withPassword("prisaly_secret");

    @BeforeAll
    static void startContainer() {
        postgres.start();
        // Ejecutar Flyway explícitamente para asegurar que las tablas existan antes de inicializar JPA
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.migrate();
        flyway.validate();
        System.out.println("M4 conexión sin secretos: " + postgres.getJdbcUrl()
                + "; usuario=" + postgres.getUsername());
        for (var migration : flyway.info().all()) {
            System.out.println("M4 Flyway: " + migration.getVersion() + " " + migration.getState());
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres.isRunning()) {
            System.out.println("M4 PostgreSQL logs:\n" + postgres.getLogs());
        }
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private SesionAcademicaService service;

    @Autowired
    private JpaSesionRepository jpaSesionRepository;

    @Autowired
    private JpaConvocatoriaRepository jpaConvocatoriaRepository;

    @Autowired
    private CasoAcademicoStubAdapter casoAcademicoStub;

    @Autowired
    private IdentidadStubAdapter identidadStub;

    @Autowired
    private ParticipacionesStubAdapter participacionesStub;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetState() {
        jpaConvocatoriaRepository.deleteAll();
        jpaSesionRepository.deleteAll();
        casoAcademicoStub.limpiar();
        identidadStub.limpiar();
        participacionesStub.limpiar();
    }

    private Connection getRawConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    // =========================================================================
    // FASE 1: Verificación de conexión y esquema PostgreSQL
    // =========================================================================
    @Nested
    @DisplayName("Fase 1: Verificación de PostgreSQL y Migraciones Flyway")
    class Fase1VerificacionPostgreSql {

        @Test
        @DisplayName("1.1 — Conexión y versión 16 de PostgreSQL comprobadas")
        void verificarConexionYVersion() throws Exception {
            assertTrue(postgres.isRunning(), "El contenedor Docker debe estar activo");

            try (Connection conn = getRawConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT current_setting('server_version_num')::integer, current_database(), current_user")) {
                assertTrue(rs.next());
                int version = rs.getInt(1);
                String db = rs.getString(2);
                String user = rs.getString(3);

                assertTrue(version >= 160000 && version < 170000, "Versión debe ser PostgreSQL 16");
                assertEquals("prisaly_test", db);
                assertEquals("prisaly", user);
            }
        }

        @Test
        @DisplayName("1.2 — Tablas creadas por migración V010 existen en PostgreSQL")
        void verificarTablasExistentes() throws Exception {
            var recursos = Collections.list(getClass().getClassLoader().getResources(
                    "db/migration/V010__m4_sesiones_convocatorias.sql"));
            assertEquals(1, recursos.size(), "V010 debe tener un único origen en el classpath de pruebas");
            System.out.println("M4 V010 classpath: " + recursos);
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('sesiones_academicas', 'convocatorias', 'flyway_schema_history')")) {
                ResultSet rs = stmt.executeQuery();
                List<String> tablas = new ArrayList<>();
                while (rs.next()) {
                    tablas.add(rs.getString("table_name"));
                }
                assertTrue(tablas.contains("sesiones_academicas"), "Tabla 'sesiones_academicas' debe existir");
                assertTrue(tablas.contains("convocatorias"), "Tabla 'convocatorias' debe existir");
                assertTrue(tablas.contains("flyway_schema_history"), "Historial de Flyway debe existir");
            }
        }

        @Test
        @DisplayName("1.3 — Restricciones CHECK aprobadas existen en el catálogo de PostgreSQL")
        void verificarRestriccionesCatalogo() throws Exception {
            try (Connection conn = getRawConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT conname FROM pg_constraint WHERE conname IN ('chk_sesion_fechas', 'chk_sesion_estado', 'uq_convocatoria_sesion_estudiante')")) {
                List<String> constraints = new ArrayList<>();
                while (rs.next()) {
                    constraints.add(rs.getString("conname"));
                }
                assertTrue(constraints.contains("chk_sesion_fechas"), "Debe existir restricción chk_sesion_fechas");
                assertTrue(constraints.contains("chk_sesion_estado"), "Debe existir restricción chk_sesion_estado");
                assertTrue(constraints.contains("uq_convocatoria_sesion_estudiante"), "Debe existir restricción de unicidad en convocatorias");
            }
        }
    }

    // =========================================================================
    // FASE 2: Creación, consulta y modificación de sesiones
    // =========================================================================
    @Nested
    @DisplayName("Fase 2: Creación, Consulta y Modificación de Sesiones")
    class Fase2SesionesPersistencia {

        @Test
        @DisplayName("2.1 — Crear y consultar sesión: datos persistidos fielmente en PostgreSQL")
        void crearYConsultarSesion() throws Exception {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(2);
            LocalDateTime fin = inicio.plusHours(2);

            SesionAcademica sesion = service.crearSesion(
                    docenteId, casoId, "Taller de Dinámica Grupal", "Descripción de prueba", inicio, fin);

            assertNotNull(sesion.getId());

            // 1. Consulta vía Servicio
            SesionAcademica consultada = service.consultarSesion(sesion.getId());
            assertEquals("Taller de Dinámica Grupal", consultada.getTitulo());
            assertEquals(EstadoSesion.PROGRAMADA, consultada.getEstado());

            // 2. Comprobación directa vía JDBC en PostgreSQL
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT titulo, descripcion, estado, version FROM sesiones_academicas WHERE id = ?")) {
                stmt.setObject(1, sesion.getId());
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next(), "La sesión debe estar en PostgreSQL");
                assertEquals("Taller de Dinámica Grupal", rs.getString("titulo"));
                assertEquals("Descripción de prueba", rs.getString("descripcion"));
                assertEquals("PROGRAMADA", rs.getString("estado"));
                assertEquals(0, rs.getInt("version"));
            }
        }

        @Test
        @DisplayName("2.2 — Modificación de sesión: actualización reflejada en PostgreSQL y version incrementada")
        void actualizarSesionPersistida() throws Exception {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(1);
            LocalDateTime fin = inicio.plusHours(1);

            SesionAcademica creada = service.crearSesion(docenteId, casoId, "Título Original", "Desc", inicio, fin);

            LocalDateTime nuevoInicio = inicio.plusDays(1);
            LocalDateTime nuevoFin = nuevoInicio.plusHours(3);

            SesionAcademica actualizada = service.actualizarSesion(
                    docenteId, creada.getId(), "Título Actualizado", "Nueva Desc", nuevoInicio, nuevoFin);

            assertEquals("Título Actualizado", actualizada.getTitulo());

            // Comprobación directa en PostgreSQL
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT titulo, descripcion, version FROM sesiones_academicas WHERE id = ?")) {
                stmt.setObject(1, creada.getId());
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals("Título Actualizado", rs.getString("titulo"));
                assertEquals("Nueva Desc", rs.getString("descripcion"));
                assertEquals(1, rs.getInt("version"), "El campo version de bloqueo optimista debe ser 1");
            }
        }

        @Test
        @DisplayName("2.3 — Restricción de base de datos chk_sesion_fechas rechaza fechas inválidas directamente en SQL")
        void baseDatosRechazaFechasInvalidas() throws Exception {
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO sesiones_academicas (id, caso_id, docente_id, titulo, fecha_inicio, fecha_fin, estado) " +
                         "VALUES (?, ?, ?, 'Invalido', '2026-11-10 12:00:00', '2026-11-10 11:00:00', 'PROGRAMADA')")) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, UUID.randomUUID());
                stmt.setObject(3, UUID.randomUUID());

                SQLException ex = assertThrows(SQLException.class, stmt::executeUpdate);
                assertTrue(ex.getMessage().contains("chk_sesion_fechas"), 
                        "PostgreSQL debe arrojar violación de chk_sesion_fechas");
            }
        }
    }

    // =========================================================================
    // FASE 3: Validación de RF-09
    // =========================================================================
    @Nested
    @DisplayName("Fase 3: Validación Exhaustiva de RF-09")
    class Fase3ValidacionRF09 {

        @Test
        @DisplayName("3.1 — RF-09: Rechazo de caso en Borrador (no publicado) sin inserción en PostgreSQL")
        void rechazoCasoEnBorrador() throws Exception {
            UUID docenteId = UUID.randomUUID();
            UUID casoBorradorId = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(2);
            LocalDateTime fin = inicio.plusHours(1);

            // M3 stub configurado explícitamente como NO publicado (Borrador)
            casoAcademicoStub.registrarEstado(casoBorradorId, false);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () ->
                    service.crearSesion(docenteId, casoBorradorId, "Sesión Rechazada", null, inicio, fin));

            assertEquals("M4-CASO-NO-PUBLICADO", ex.getCodigo());

            // Verificación directa en PostgreSQL: 0 registros para este caso
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM sesiones_academicas WHERE caso_id = ?")) {
                stmt.setObject(1, casoBorradorId);
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "PostgreSQL NO debe contener ninguna sesión de un caso en Borrador");
                System.out.println("M4 RF-09 SQL: caso Borrador (stub M3), sesiones=0");
            }
        }

        @Test
        @DisplayName("3.2 — RF-09: Aceptación de caso Publicado, persistencia en BD y solicitud de bloqueo F3-03")
        void aceptacionCasoPublicadoYBloqueo() throws Exception {
            UUID docenteId = UUID.randomUUID();
            UUID casoPublicadoId = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(3);
            LocalDateTime fin = inicio.plusHours(2);

            // M3 stub configurado como Publicado
            casoAcademicoStub.registrarEstado(casoPublicadoId, true);

            SesionAcademica sesion = service.crearSesion(
                    docenteId, casoPublicadoId, "Sesión Caso Publicado", "Válida", inicio, fin);

            assertNotNull(sesion.getId());
            assertTrue(casoAcademicoStub.fueBloqueoSolicitado(casoPublicadoId), 
                    "La primera sesión debe solicitar a M3 el bloqueo académico F3-03");

            // Comprobación directa en PostgreSQL: 1 registro
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM sesiones_academicas WHERE caso_id = ?")) {
                stmt.setObject(1, casoPublicadoId);
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PostgreSQL debe registrar exactamente 1 sesión");
                System.out.println("M4 RF-09 SQL: caso Publicado (stub M3), sesiones=1; bloqueo F3-03 simulado");
            }
        }
    }

    // =========================================================================
    // FASE 4: Convocatorias y Verificación de Acceso (para M5)
    // =========================================================================
    @Nested
    @DisplayName("Fase 4: Convocatorias y Participaciones")
    class Fase4Convocatorias {

        @Test
        @DisplayName("4.1 — Convocatoria en sesión PROGRAMADA es rechazada por regla de dominio")
        void rechazoConvocatoriaSesionProgramada() {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();

            SesionAcademica sesion = service.crearSesion(
                    docenteId, casoId, "Sesión Programada", null, 
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () ->
                    service.convocarEstudiante(docenteId, sesion.getId(), estudianteId));

            assertEquals("M4-CONVOCATORIA-NO-PERMITIDA", ex.getCodigo());
        }

        @Test
        @DisplayName("4.2 — Convocatoria en sesión ABIERTA persistida con relación foránea válida en PostgreSQL")
        void convocarEstudianteYVerificarBD() throws Exception {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();

            SesionAcademica sesion = service.crearSesion(
                    docenteId, casoId, "Sesión Convocatoria", null, 
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

            // Abrir sesión para permitir convocatorias según regla de negocio de M4
            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.ABIERTA);

            Convocatoria conv = service.convocarEstudiante(docenteId, sesion.getId(), estudianteId);
            assertNotNull(conv.getId());

            // Comprobación directa en PostgreSQL
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT sesion_id, estudiante_id, confirmada FROM convocatorias WHERE id = ?")) {
                stmt.setObject(1, conv.getId());
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(sesion.getId(), rs.getObject("sesion_id", UUID.class));
                assertEquals(estudianteId, rs.getObject("estudiante_id", UUID.class));
                assertFalse(rs.getBoolean("confirmada"));
            }
        }

        @Test
        @DisplayName("4.2 — Violación de clave foránea en convocatorias para sesión inexistente rechazada por PostgreSQL")
        void violacionClaveForaneaConvocatoria() throws Exception {
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO convocatorias (id, sesion_id, estudiante_id) VALUES (?, ?, ?)")) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, UUID.randomUUID()); // Sesión inexistente
                stmt.setObject(3, UUID.randomUUID());

                SQLException ex = assertThrows(SQLException.class, stmt::executeUpdate);
                assertTrue(ex.getMessage().contains("convocatorias_sesion_id_fkey"), 
                        "PostgreSQL debe rechazar la inserción por violación de llave foránea");
            }
        }

        @Test
        @DisplayName("4.3 — Contrato M5: verificarAccesoSesion valida estado y convocatoria")
        void verificarAccesoEstudiante() {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            UUID estudianteConvocado = UUID.randomUUID();
            UUID estudianteNoConvocado = UUID.randomUUID();

            SesionAcademica sesion = service.crearSesion(
                    docenteId, casoId, "Acceso M5", null,
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

            // Sesión en estado PROGRAMADA -> no accesible
            assertFalse(service.verificarAccesoSesion(sesion.getId(), estudianteConvocado));

            // Abrir sesión -> permite convocatorias
            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.ABIERTA);
            service.convocarEstudiante(docenteId, sesion.getId(), estudianteConvocado);

            // Sesión ABIERTA -> accesible para convocado, inaccesible para no convocado
            assertTrue(service.verificarAccesoSesion(sesion.getId(), estudianteConvocado));
            assertFalse(service.verificarAccesoSesion(sesion.getId(), estudianteNoConvocado));

            // Iniciar sesión (EN_CURSO) -> accesible para convocado
            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.EN_CURSO);
            assertTrue(service.verificarAccesoSesion(sesion.getId(), estudianteConvocado));

            // Cerrar sesión -> inaccesible para todos
            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.CERRADA);
            assertFalse(service.verificarAccesoSesion(sesion.getId(), estudianteConvocado));
        }
    }

    // =========================================================================
    // FASE 5: Pruebas de Rollback y Transaccionalidad
    // =========================================================================
    @Nested
    @DisplayName("Fase 5: Pruebas de Rollback e Integridad Transaccional")
    class Fase5Rollback {

        @Test
        @DisplayName("5.1 — Transacción fallida revierte inserción: ningún registro parcial en PostgreSQL")
        void transaccionFallidaHaceRollbackEnPostgreSql() throws Exception {
            UUID sesionId = UUID.randomUUID();
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            assertThrows(RuntimeException.class, () -> {
                txTemplate.execute(status -> {
                    SesionAcademicaEntity entity = new SesionAcademicaEntity();
                    entity.setId(sesionId);
                    entity.setDocenteId(docenteId);
                    entity.setCasoId(casoId);
                    entity.setTitulo("Sesión en Transacción Fallida");
                    entity.setFechaInicio(LocalDateTime.now().plusDays(1));
                    entity.setFechaFin(LocalDateTime.now().plusDays(1).plusHours(2));
                    entity.setEstado(EstadoSesion.PROGRAMADA);
                    entity.setCreadoEn(LocalDateTime.now());
                    entity.setActualizadoEn(LocalDateTime.now());
                    entity.setVersion(0);

                    jpaSesionRepository.saveAndFlush(entity);

                    // Provocar una falla no recuperable dentro de la misma transacción
                    throw new RuntimeException("Falla forzada para verificar rollback transaccional");
                });
            });

            // Comprobación directa vía JDBC en PostgreSQL: el registro NO debe existir
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM sesiones_academicas WHERE id = ?")) {
                stmt.setObject(1, sesionId);
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), 
                        "PostgreSQL debe tener 0 registros: el rollback debe ser total sin residuos parciales");
                System.out.println("M4 rollback SQL: escritura enviada con saveAndFlush, sesiones residuales=0");
            }
        }
    }

    // =========================================================================
    // FASE 6: Pruebas de Concurrencia
    // =========================================================================
    @Nested
    @DisplayName("Fase 6: Pruebas de Concurrencia")
    class Fase6Concurrencia {

        @Test
        @DisplayName("HTTP: actualizar a un rango duplicado responde 409 por la restricción PostgreSQL real")
        void actualizacionDuplicadaResponde409() throws Exception {
            UUID docente = UUID.randomUUID();
            UUID caso = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(3);
            LocalDateTime fin = inicio.plusHours(1);
            service.crearSesion(docente, caso, "Primera", null, inicio, fin);
            var segunda = service.crearSesion(docente, caso, "Segunda", null,
                    inicio.plusDays(1), fin.plusDays(1));
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new com.prisaly.m4.presentation.controller.SesionController(service))
                    .setControllerAdvice(new com.prisaly.m4.presentation.controller.M4ExceptionHandler()).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/m4/sesiones/" + segunda.getId()).header("X-Docente-Id", docente)
                    .contentType("application/json")
                    .content("{\"titulo\":\"Duplicada\",\"fechaInicio\":\"" + inicio
                            + "\",\"fechaFin\":\"" + fin + "\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.codigo").value("M4-SESION-DUPLICADA"));
            assertEquals("Segunda", service.consultarSesion(segunda.getId()).getTitulo());
            System.out.println("M4 HTTP: duplicado PostgreSQL real -> 409; actualización fallida revertida");
        }

        @Test
        @DisplayName("6.1 — Creación concurrente idéntica: solo una tiene éxito, PostgreSQL sin duplicados")
        void creacionConcurrenteSinDuplicados() throws Exception {
            int hilos = 10;
            ExecutorService executor = Executors.newFixedThreadPool(hilos);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(hilos);

            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(5);
            LocalDateTime fin = inicio.plusHours(2);

            AtomicInteger exitos = new AtomicInteger(0);
            AtomicInteger fallos = new AtomicInteger(0);
            List<Throwable> inesperados = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < hilos; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        service.crearSesion(docenteId, casoId, "Taller Concurrente", "Desc", inicio, fin);
                        exitos.incrementAndGet();
                    } catch (Exception ex) {
                        boolean duplicado = ex instanceof ReglaNegocioException regla
                                && "M4-SESION-DUPLICADA".equals(regla.getCodigo());
                        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
                            if (causa instanceof SQLException sql
                                    && "23505".equals(sql.getSQLState())
                                    && sql.getMessage().contains("uq_sesion_caso_docente_fechas")) {
                                duplicado = true;
                            }
                        }
                        if (duplicado) {
                            fallos.incrementAndGet();
                        } else {
                            inesperados.add(ex);
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            latch.countDown(); // Lanzar todos los hilos simultáneamente
            try {
                assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }

            assertTrue(inesperados.isEmpty(), () -> "Fallos inesperados: " + inesperados);
            assertEquals(1, exitos.get(), "Exactamente una creación debe tener éxito");
            assertEquals(hilos - 1, fallos.get(), "Los demás intentos deben ser rechazados");
            System.out.println("M4 concurrencia: éxitos=" + exitos.get()
                    + ", duplicados esperados=" + fallos.get() + ", inesperados=" + inesperados.size());

            // Comprobación directa en PostgreSQL: exactamente 1 registro
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM sesiones_academicas WHERE caso_id = ? AND docente_id = ?")) {
                stmt.setObject(1, casoId);
                stmt.setObject(2, docenteId);
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), 
                        "PostgreSQL debe contener exactamente 1 sesión sin duplicados");
            }
        }

        @Test
        @DisplayName("6.2 — Bloqueo optimista detecta modificaciones concurrentes sobre la misma sesión")
        void bloqueoOptimistaEnModificacionConcurrente() {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(1);
            LocalDateTime fin = inicio.plusHours(2);

            SesionAcademica sesion = service.crearSesion(docenteId, casoId, "Sesión Original", "Desc", inicio, fin);

            // Simular dos lecturas concurrentes de la entidad
            SesionAcademicaEntity copia1 = jpaSesionRepository.findById(sesion.getId()).orElseThrow();
            SesionAcademicaEntity copia2 = jpaSesionRepository.findById(sesion.getId()).orElseThrow();

            // Modificación 1 guarda con éxito (version pasa a 1)
            copia1.setTitulo("Modificación 1");
            jpaSesionRepository.saveAndFlush(copia1);

            // Modificación 2 intenta guardar con version desactualizada (version 0)
            copia2.setTitulo("Modificación 2");
            assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
                jpaSesionRepository.saveAndFlush(copia2);
            }, "JPA y PostgreSQL deben lanzar fallo de bloqueo optimista");
        }
    }

    // =========================================================================
    // FASE 7: Verificación de Contratos M1, M3, M4, M5
    // =========================================================================
    @Nested
    @DisplayName("Fase 7: Verificación de Integración M1–M3–M4–M5")
    class Fase7IntegracionContratos {

        @Test
        @DisplayName("M5 simulado: historial impide actualizar programación sin modificar PostgreSQL")
        void historialImpideActualizarProgramacion() throws Exception {
            UUID docente = UUID.randomUUID();
            UUID caso = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(2);
            var sesion = service.crearSesion(docente, caso, "Historial protegido", null,
                    inicio, inicio.plusHours(1));
            participacionesStub.registrarHistorial(sesion.getId());

            var error = assertThrows(ReglaNegocioException.class, () -> service.actualizarSesion(
                    docente, sesion.getId(), "Alterada", null, inicio.plusDays(1), inicio.plusDays(1).plusHours(1)));
            assertEquals("M4-SESION-CON-PARTICIPACIONES", error.getCodigo());
            verificarHistorialSinAlteraciones(sesion.getId(), caso);
        }

        @Test
        @DisplayName("M5 simulado: historial impide reasignar caso sin modificar PostgreSQL")
        void historialImpideReasignarCaso() throws Exception {
            UUID docente = UUID.randomUUID();
            UUID caso = UUID.randomUUID();
            LocalDateTime inicio = LocalDateTime.now().plusDays(2);
            var sesion = service.crearSesion(docente, caso, "Historial protegido", null,
                    inicio, inicio.plusHours(1));
            participacionesStub.registrarHistorial(sesion.getId());

            var error = assertThrows(ReglaNegocioException.class, () -> service.cambiarCaso(
                    docente, sesion.getId(), UUID.randomUUID()));
            assertEquals("M4-SESION-CON-PARTICIPACIONES", error.getCodigo());
            verificarHistorialSinAlteraciones(sesion.getId(), caso);
        }

        private void verificarHistorialSinAlteraciones(UUID sesionId, UUID caso) throws Exception {
            try (var conexion = getRawConnection();
                 var consulta = conexion.prepareStatement(
                         "SELECT caso_id, titulo FROM sesiones_academicas WHERE id = ?")) {
                consulta.setObject(1, sesionId);
                try (var resultado = consulta.executeQuery()) {
                    assertTrue(resultado.next());
                    assertEquals(caso, resultado.getObject("caso_id", UUID.class));
                    assertEquals("Historial protegido", resultado.getString("titulo"));
                }
            }
            System.out.println("M4 historial: señal M5 simulada; caso/título preservados en PostgreSQL");
        }

        @Test
        @DisplayName("7.1 — Contrato M1: Rechazo de docente sin permiso GESTIONAR_SESION sin persistencia")
        void rechazoDocenteSinPermiso() throws Exception {
            UUID docenteSinPermiso = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();

            identidadStub.configurarPermiso(docenteSinPermiso, "GESTIONAR_SESION", false);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () ->
                    service.crearSesion(docenteSinPermiso, casoId, "Sin Permiso", null, 
                            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2)));

            assertEquals("M4-SIN-PERMISO", ex.getCodigo());

            // Comprobar 0 registros en PostgreSQL
            try (Connection conn = getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM sesiones_academicas WHERE docente_id = ?")) {
                stmt.setObject(1, docenteSinPermiso);
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }

        @Test
        @DisplayName("7.2 — Contrato M4-M5: Sesión cerrada no permite acceso a intentos de estudiantes")
        void sesionCerradaNoPermiteAccesoM5() {
            UUID docenteId = UUID.randomUUID();
            UUID casoId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();

            SesionAcademica sesion = service.crearSesion(
                    docenteId, casoId, "Sesion M5 Cierre", null,
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.ABIERTA);
            service.convocarEstudiante(docenteId, sesion.getId(), estudianteId);

            assertTrue(service.verificarAccesoSesion(sesion.getId(), estudianteId));

            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.EN_CURSO);
            service.cambiarEstado(docenteId, sesion.getId(), EstadoSesion.CERRADA);
            assertFalse(service.verificarAccesoSesion(sesion.getId(), estudianteId));
        }
    }
}
