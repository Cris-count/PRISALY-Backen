package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import com.prisaly.m1.application.service.ActivacionCuentaEstudianteService;
import com.prisaly.m1.application.service.AutenticacionEstudianteService;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.application.support.CuentaEstudianteMemoria;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.domain.student.FirmasEstudiante;
import com.prisaly.m1.infrastructure.security.PasswordHashConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class CuentaEstudianteServiceTest {

    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ESTUDIANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CORREO = "ana.estudiante@prisaly.test";
    private static final String SECRETO = "jdbc-password-ficticia";
    private static final byte[] CLAVE = "clave-hmac-prueba".getBytes(StandardCharsets.UTF_8);

    private CuentaEstudianteMemoria cuentas;
    private PasswordEncoder encoder;
    private MutableClock reloj;
    private AtomicReference<String> token;
    private ActivacionCuentaEstudianteService activacion;
    private AutenticacionEstudianteService autenticacion;
    private SessionStudentAccessService acceso;
    private ListAppender<ILoggingEvent> logs;
    private Logger root;
    private Level nivelPrevio;

    @BeforeEach
    void setUp() {
        cuentas = new CuentaEstudianteMemoria();
        cuentas.vincular(USUARIO, ESTUDIANTE, CORREO, false, null);
        encoder = new PasswordHashConfig().passwordEncoder();
        reloj = new MutableClock(Instant.parse("2026-11-08T12:00:00Z"));
        token = new AtomicReference<>();
        ClaveHmacEstudiante clave = new ClaveHmacEstudiante(CLAVE);
        activacion = new ActivacionCuentaEstudianteService(cuentas, encoder, clave, reloj);
        autenticacion = new AutenticacionEstudianteService(cuentas, encoder, clave, reloj);
        acceso = new SessionStudentAccessService(
                cuentas, () -> Optional.ofNullable(token.get()), clave, reloj);
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelPrevio = root.getLevel();
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        root.setLevel(Level.TRACE);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(logs);
        root.setLevel(nivelPrevio);
        SecurityContextHolder.clearContext();
    }

    @Test
    void activacionVinculaLaCuentaYNoGuardaElCodigoEnClaro() {
        ActivacionCuentaEstudianteService.CodigoEmitido emitido = activacion.emitir(USUARIO);
        assertFalse(new String(cuentas.hmacGuardado(emitido.challengeId()), StandardCharsets.UTF_8)
                .contains(emitido.codigo()));
        activacion.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        token.set(autenticacion.autenticar(CORREO, "contrasena-segura").token());
        AuthenticatedStudent estudiante = acceso.requireCurrentStudent();
        assertEquals(USUARIO, estudiante.userId());
        assertEquals(ESTUDIANTE, estudiante.studentId());
    }

    @Test
    void codigoRepetidoVencidoOAgotadoNoActiva() {
        ActivacionCuentaEstudianteService.CodigoEmitido emitido = activacion.emitir(USUARIO);
        activacion.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        assertThrows(IdentityDomainException.class,
                () -> activacion.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura"));

        cuentas.vincular(USUARIO, ESTUDIANTE, CORREO, false, null);
        ActivacionCuentaEstudianteService.CodigoEmitido vencido = activacion.emitir(USUARIO);
        reloj.instant = reloj.instant.plusSeconds(16 * 60);
        assertThrows(IdentityDomainException.class,
                () -> activacion.activar(vencido.challengeId(), vencido.codigo(), "contrasena-segura"));

        reloj.instant = Instant.parse("2026-11-08T12:00:00Z");
        cuentas.vincular(USUARIO, ESTUDIANTE, CORREO, false, null);
        ActivacionCuentaEstudianteService.CodigoEmitido limitado = activacion.emitir(USUARIO);
        for (int i = 0; i < 5; i++) {
            assertThrows(IdentityDomainException.class,
                    () -> activacion.activar(limitado.challengeId(), "00000000", "contrasena-segura"));
        }
        assertThrows(IdentityDomainException.class,
                () -> activacion.activar(limitado.challengeId(), limitado.codigo(), "contrasena-segura"));
    }

    @Test
    void unCodigoNuevoRevocaElAnterior() {
        ActivacionCuentaEstudianteService.CodigoEmitido primero = activacion.emitir(USUARIO);
        ActivacionCuentaEstudianteService.CodigoEmitido segundo = activacion.emitir(USUARIO);
        assertThrows(IdentityDomainException.class,
                () -> activacion.activar(primero.challengeId(), primero.codigo(), "contrasena-segura"));
        activacion.activar(segundo.challengeId(), segundo.codigo(), "contrasena-segura");
    }

    @Test
    void contrasenaIncorrectaCuentaInactivaYSinVinculoSeNieganIgual() {
        assertThrows(IdentityDomainException.class, () -> autenticacion.autenticar(CORREO, "contrasena-segura"));
        ActivacionCuentaEstudianteService.CodigoEmitido emitido = activacion.emitir(USUARIO);
        activacion.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        IdentityDomainException mala = assertThrows(IdentityDomainException.class,
                () -> autenticacion.autenticar(CORREO, "otra-contrasena-distinta"));
        IdentityDomainException ausente = assertThrows(IdentityDomainException.class,
                () -> autenticacion.autenticar("nadie@prisaly.test", "contrasena-segura"));
        assertEquals(mala.code(), ausente.code());
        assertThrows(IdentityDomainException.class, () -> activacion.emitir(UUID.randomUUID()));
    }

    @Test
    void elBearerDePersonalNoEsIdentidadEstudiantilYLaRevocacionCierraLaSesion() {
        ActivacionCuentaEstudianteService.CodigoEmitido emitido = activacion.emitir(USUARIO);
        activacion.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        String sesion = autenticacion.autenticar(CORREO, "contrasena-segura").token();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USUARIO, "bearer-personal"));
        assertThrows(IdentityDomainException.class, () -> acceso.requireCurrentStudent());
        token.set(sesion);
        assertEquals(ESTUDIANTE, acceso.requireCurrentStudent().studentId());
        autenticacion.revocar(sesion);
        assertThrows(IdentityDomainException.class, () -> acceso.requireCurrentStudent());
        for (ILoggingEvent event : logs.list) {
            assertFalse(event.getFormattedMessage().contains(SECRETO));
            assertFalse(event.getFormattedMessage().contains(emitido.codigo()));
            assertFalse(event.getFormattedMessage().contains(sesion));
        }
    }

    @Test
    void unFalloTecnicoNoProduceIdentidadNiRegistraElSecreto() {
        token.set("token-opaco");
        cuentas.fallarLectura = true;
        assertThrows(IllegalStateException.class, () -> acceso.requireCurrentStudent());
        for (ILoggingEvent event : logs.list) {
            assertFalse(event.getFormattedMessage().contains(SECRETO), event.getFormattedMessage());
        }
    }

    @Test
    void laFirmaDelCodigoNoEsElCodigo() {
        ActivacionCuentaEstudianteService.CodigoEmitido emitido = activacion.emitir(USUARIO);
        byte[] firma = FirmasEstudiante.hmac(CLAVE, emitido.challengeId() + ":" + emitido.codigo());
        assertArrayEquals(firma, cuentas.hmacGuardado(emitido.challengeId()));
        assertNotEquals(emitido.codigo(), new String(firma, StandardCharsets.UTF_8));
        assertTrue(encoder.matches("contrasena-segura", encoder.encode("contrasena-segura")));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
