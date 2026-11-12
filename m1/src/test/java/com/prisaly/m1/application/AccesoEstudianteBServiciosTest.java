package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m1.application.service.CierreSesionEstudianteService;
import com.prisaly.m1.application.service.LimiteAccesoEstudianteException;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.application.service.SolicitudAccesoEstudianteService;
import com.prisaly.m1.application.service.ValidacionAccesoEstudianteService;
import com.prisaly.m1.application.support.AccesoEstudianteMemoria;
import com.prisaly.m1.application.support.ClavesDePrueba;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.ConjuntoClavesHmac;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import com.prisaly.m1.infrastructure.security.LimiteAccesoEstudianteEnMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** DD-02 B services with in-memory ports (PostgreSQL behaviour is covered by the ITs). D-B2.1, D-B2.2, D-B2.5, D-B4. */
@ExtendWith(OutputCaptureExtension.class)
class AccesoEstudianteBServiciosTest {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-11T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };
    /** Executes the callback without a real transaction manager (tests the service logic only). */
    private final TransactionTemplate sinTransaccion = new TransactionTemplate() {
        @Override public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    private AccesoEstudianteMemoria accesos;
    private ClavesAccesoEstudiante claves;
    private List<CodigoAccesoDeliveryCommand> entregados;
    private StudentAccessDeliveryPort entrega;
    private SolicitudAccesoEstudianteService solicitud;
    private ValidacionAccesoEstudianteService validacion;
    private LimiteAccesoEstudianteEnMemoria limites;

    @BeforeEach
    void preparar() {
        accesos = new AccesoEstudianteMemoria();
        claves = ClavesDePrueba.claves();
        entregados = new ArrayList<>();
        entrega = entregados::add;
        limites = new LimiteAccesoEstudianteEnMemoria(5, 20, 20, Duration.ofMinutes(15), reloj);
        solicitud = new SolicitudAccesoEstudianteService(accesos, limites, cmd -> entrega.entregarCodigo(cmd), claves,
                sinTransaccion, reloj);
        validacion = new ValidacionAccesoEstudianteService(accesos, limites, claves, reloj);
    }

    private void avanzar(Duration d) {
        ahora.set(ahora.get().plus(d));
    }

    @Test
    void solicitudConCuentaEmiteUnCodigoIndividualFirmadoYNoLoGuardaEnClaro() {
        accesos.alta("est@prisaly.test", true);
        UUID challenge = solicitud.solicitar(" Est@Prisaly.TEST ", "10.0.0.1");

        assertEquals(1, entregados.size());
        CodigoAccesoDeliveryCommand cmd = entregados.get(0);
        assertEquals(challenge.toString(), cmd.challengeId());
        assertEquals("est@prisaly.test", cmd.correoDestinatario());
        assertTrue(PoliticaCodigoAcceso.formatoValido(cmd.codigoTemporal()));
        assertEquals(ahora.get().plus(Duration.ofMinutes(5)), cmd.fechaExpiracion());
        var guardada = accesos.credenciales.get(challenge).datos;
        assertArrayEquals(claves.codigo().firmar(PoliticaCodigoAcceso.mensaje(challenge, cmd.codigoTemporal())),
                guardada.codigoHmac());
        assertFalse(new String(guardada.codigoHmac(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(cmd.codigoTemporal()));
        assertEquals("cod-qa", guardada.kidCodigo());
    }

    @Test
    void correoSinCuentaOInactivoDevuelveUnDesafioIgualSinEnviarNiGuardar() {
        accesos.alta("inactivo@prisaly.test", false);
        UUID sinCuenta = solicitud.solicitar("nadie@prisaly.test", "o");
        UUID inactivo = solicitud.solicitar("inactivo@prisaly.test", "o");
        assertNotEquals(sinCuenta, inactivo);
        assertTrue(entregados.isEmpty());
        assertTrue(accesos.credenciales.isEmpty());
        // A decoy challenge validates exactly like a wrong code.
        var ex = assertThrows(IdentityDomainException.class, () -> validacion.validar(sinCuenta, "12345678", "o"));
        assertEquals("STUDENT_ACCESS_DENIED", ex.code());
    }

    @Test
    void unaNuevaSolicitudRevocaElCodigoAnteriorYReenvioAntesDe60sEs429() {
        accesos.alta("est@prisaly.test", true);
        UUID primero = solicitud.solicitar("est@prisaly.test", "o");
        var ex = assertThrows(LimiteAccesoEstudianteException.class, () -> solicitud.solicitar("est@prisaly.test", "o"));
        assertEquals(60, ex.segundos());
        avanzar(Duration.ofSeconds(60));
        UUID segundo = solicitud.solicitar("est@prisaly.test", "o");
        assertTrue(accesos.credenciales.get(primero).revocada);
        String codigoPrimero = entregados.get(0).codigoTemporal();
        assertThrows(IdentityDomainException.class, () -> validacion.validar(primero, codigoPrimero, "o"));
        assertEquals(1, accesos.credenciales.values().stream().filter(c -> !c.revocada && !c.consumida).count());
        assertNotEquals(primero, segundo);
    }

    @Test
    void falloAlEncolarDevuelveDesafioSinCredencialUtilizableNiSecretosEnElLog(CapturedOutput salida) {
        accesos.alta("est@prisaly.test", true);
        entrega = cmd -> { throw new IllegalStateException("cola caída " + cmd.codigoTemporal()); };
        // The in-memory port is not transactional: a real rollback is proven in the PostgreSQL IT (DD02-12a).
        UUID challenge = solicitud.solicitar("est@prisaly.test", "o");
        assertTrue(challenge != null);
        assertFalse(salida.getAll().contains("est@prisaly.test"));
        assertTrue(salida.getAll().contains("ENCOLADO_FALLIDO"));
        assertFalse(salida.getAll().contains("cola caída"), "the exception message (which carries the code) is not logged");
    }

    @Test
    void codigoCorrectoAbreUnaSesionUnaSolaVezYTodoFalloEsElMismo401() {
        accesos.alta("est@prisaly.test", true);
        UUID challenge = solicitud.solicitar("est@prisaly.test", "o");
        String codigo = entregados.get(0).codigoTemporal();

        var sesion = validacion.validar(challenge, codigo, "o");
        assertEquals(ahora.get().plus(Duration.ofHours(8)), sesion.expira());
        assertFalse(sesion.toString().contains(sesion.token()));
        assertEquals(1, accesos.sesiones.size());
        assertEquals("ses-qa", accesos.sesiones.get(0).datos().kidSesion());

        for (String caso : List.of(codigo, "00000000", "abc")) {
            var ex = assertThrows(IdentityDomainException.class, () -> validacion.validar(challenge, caso, "o"));
            assertEquals("STUDENT_ACCESS_DENIED", ex.code());
        }
        assertThrows(IdentityDomainException.class, () -> validacion.validar(UUID.randomUUID(), codigo, "o"));
        assertEquals(1, accesos.sesiones.size());
    }

    @Test
    void cincoFallosAgotanElCodigoYElCodigoCorrectoYaNoSirve() {
        accesos.alta("est@prisaly.test", true);
        UUID challenge = solicitud.solicitar("est@prisaly.test", "o");
        String codigo = entregados.get(0).codigoTemporal();
        String erroneo = codigo.equals("11111111") ? "22222222" : "11111111";
        for (int i = 0; i < 5; i++) {
            assertThrows(IdentityDomainException.class, () -> validacion.validar(challenge, erroneo, "o"));
        }
        assertEquals(5, accesos.credenciales.get(challenge).intentos);
        assertThrows(IdentityDomainException.class, () -> validacion.validar(challenge, codigo, "o"));
        assertTrue(accesos.sesiones.isEmpty());
    }

    @Test
    void elCodigoVenceALosCincoMinutos() {
        accesos.alta("est@prisaly.test", true);
        UUID challenge = solicitud.solicitar("est@prisaly.test", "o");
        String codigo = entregados.get(0).codigoTemporal();
        avanzar(Duration.ofMinutes(5));
        assertThrows(IdentityDomainException.class, () -> validacion.validar(challenge, codigo, "o"));
        assertTrue(accesos.sesiones.isEmpty());
    }

    @Test
    void veinteValidacionesFallidasDelOrigenDevuelven429InclusoConElCodigoCorrecto() {
        accesos.alta("est@prisaly.test", true);
        UUID challenge = solicitud.solicitar("est@prisaly.test", "atacante");
        String codigo = entregados.get(0).codigoTemporal();
        for (int i = 0; i < 20; i++) {
            UUID otro = UUID.randomUUID();
            assertThrows(IdentityDomainException.class, () -> validacion.validar(otro, "12345678", "atacante"));
        }
        assertThrows(LimiteAccesoEstudianteException.class, () -> validacion.validar(challenge, codigo, "atacante"));
        assertTrue(accesos.sesiones.isEmpty());
    }

    @Test
    void laSesionSeVerificaConKSesActualOAnteriorYElCierreLaRevoca() {
        accesos.alta("est@prisaly.test", true);
        UUID challenge = solicitud.solicitar("est@prisaly.test", "o");
        String token = validacion.validar(challenge, entregados.get(0).codigoTemporal(), "o").token();
        AtomicReference<String> actual = new AtomicReference<>(token);
        var acceso = new SessionStudentAccessService(accesos, () -> Optional.ofNullable(actual.get()), claves, reloj);
        assertEquals(accesos.sesiones.get(0).datos().studentId(), acceso.requireCurrentStudent().studentId());

        // Ordinary rotation: new K-SES first, previous still verifies existing sessions.
        String nueva = com.prisaly.m1.application.support.ClavesDePrueba.especificacion("ses-2026s2", 32);
        var rotadas = new ClavesAccesoEstudiante(claves.codigo(), rotar(nueva));
        var tras = new SessionStudentAccessService(accesos, () -> Optional.ofNullable(actual.get()), rotadas, reloj);
        assertEquals(accesos.sesiones.get(0).datos().studentId(), tras.requireCurrentStudent().studentId());

        var cierre = new CierreSesionEstudianteService(accesos, rotadas, reloj);
        cierre.cerrar(token, "o");
        assertThrows(IdentityDomainException.class, acceso::requireCurrentStudent);
        cierre.cerrar(token, "o");
        assertEquals(1, accesos.auditoria.stream().filter("CIERRE:SESION_REVOCADA"::equals).count(), "idempotent, audited once");
        actual.set("token-desconocido");
        assertThrows(IdentityDomainException.class, acceso::requireCurrentStudent);
    }

    private ConjuntoClavesHmac rotar(String nueva) {
        // Builds "new,previous" from the test key set by reusing the previous version through a fresh spec.
        return ConjuntoClavesHmac.leer("K-SES", nueva + "," + especificacionDe(claves.sesion()));
    }

    private static String especificacionDe(ConjuntoClavesHmac c) {
        byte[] material = c.materialParaComparar().get(0);
        return c.kidActual() + ":" + java.util.Base64.getEncoder().encodeToString(material);
    }
}
