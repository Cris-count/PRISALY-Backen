package com.prisaly.m5.infrastructure.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import com.prisaly.m1.application.service.AutenticacionEstudianteService;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.infrastructure.security.PasswordHashConfig;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SesionEstudianteTitularidadTest {

    @Test
    void laSesionDeM1SoloAbreElIntentoYLaConvocatoriaPropios() {
        UUID estudiante = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();
        Cuentas cuentas = new Cuentas();
        cuentas.alta(UUID.randomUUID(), estudiante, "propio@prisaly.test");
        cuentas.alta(UUID.randomUUID(), ajeno, "ajeno@prisaly.test");
        var encoder = new PasswordHashConfig().passwordEncoder();
        var clave = new ClaveHmacEstudiante("clave-hmac-prueba".getBytes(StandardCharsets.UTF_8));
        var autenticacion = new AutenticacionEstudianteService(cuentas, encoder, clave);
        AtomicReference<String> token = new AtomicReference<>();
        TokenSesionEstudianteActual fuente = () -> Optional.ofNullable(token.get());
        var acceso = new SessionStudentAccessService(cuentas, fuente, clave);
        ReferenciaIntento intento = new ReferenciaIntento(UUID.randomUUID().toString());
        ReferenciaConvocatoria propia = new ReferenciaConvocatoria(UUID.randomUUID().toString());
        ControlAccesoIntentos control = new ControlAccesoIntentos(
                new IdentidadEstudianteDesdeM1(acceso),
                new TitularidadDePrueba(intento, propia, estudiante));

        token.set(autenticacion.autenticar("propio@prisaly.test", "contrasena-segura").token());
        assertEquals(estudiante.toString(), control.verificar(intento).valor());
        assertEquals(estudiante.toString(), control.verificarConvocatoria(propia).valor());

        token.set(autenticacion.autenticar("ajeno@prisaly.test", "contrasena-segura").token());
        assertThrows(AccesoIntentoDenegadoException.class, () -> control.verificar(intento));
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> control.verificarConvocatoria(propia));
        assertThrows(IntentoNoEncontradoException.class,
                () -> control.verificar(new ReferenciaIntento(UUID.randomUUID().toString())));
        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> control.verificarConvocatoria(new ReferenciaConvocatoria(UUID.randomUUID().toString())));

        cuentas.fallar = true;
        assertThrows(IllegalStateException.class, () -> control.verificar(intento));
    }

    private static final class Cuentas implements CuentaEstudiantePort {
        private final Map<String, CredencialEstudiante> correos = new ConcurrentHashMap<>();
        private final Map<UUID, VinculoEstudiante> vinculos = new ConcurrentHashMap<>();
        private final Map<String, SesionGuardada> sesiones = new ConcurrentHashMap<>();
        private boolean fallar;

        void alta(UUID userId, UUID studentId, String correo) {
            String hash = new PasswordHashConfig().passwordEncoder().encode("contrasena-segura");
            vinculos.put(userId, new VinculoEstudiante(userId, studentId, true));
            correos.put(correo, new CredencialEstudiante(userId, hash, true));
        }

        @Override
        public Optional<VinculoEstudiante> vinculo(UUID idUsuario) {
            exigir();
            return Optional.ofNullable(vinculos.get(idUsuario));
        }

        @Override
        public Optional<CredencialEstudiante> porCorreo(String correoNormalizado) {
            exigir();
            return Optional.ofNullable(correos.get(correoNormalizado));
        }

        @Override
        public void abrirSesion(SesionNueva sesion) {
            sesiones.put(Arrays.toString(sesion.tokenHmac()), new SesionGuardada(sesion.userId(), sesion.studentId()));
        }

        @Override
        public Optional<SesionGuardada> sesionVigente(byte[] tokenHmac, OffsetDateTime ahora) {
            exigir();
            return Optional.ofNullable(sesiones.get(Arrays.toString(tokenHmac)));
        }

        @Override
        public void revocarActivacionesAbiertas(UUID idUsuario, OffsetDateTime cuando) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void guardarActivacion(ActivacionNueva activacion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ActivacionGuardada> buscarActivacion(UUID challengeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registrarIntentoFallido(UUID challengeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean consumirYActivar(UUID challengeId, String passwordHash, OffsetDateTime cuando) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revocarSesion(byte[] tokenHmac, OffsetDateTime cuando) {
            throw new UnsupportedOperationException();
        }

        private void exigir() {
            if (fallar) {
                throw new IllegalStateException("identidad no disponible");
            }
        }
    }

    private record TitularidadDePrueba(
            ReferenciaIntento intento, ReferenciaConvocatoria convocatoria, UUID titular)
            implements TitularidadIntentos {

        @Override
        public Titularidad de(ReferenciaIntento consultado, IdentificadorEstudiante estudiante) {
            if (!intento.equals(consultado)) {
                return Titularidad.INEXISTENTE;
            }
            return estudiante.valor().equals(titular.toString()) ? Titularidad.PROPIO : Titularidad.AJENO;
        }

        @Override
        public Titularidad deConvocatoria(ReferenciaConvocatoria consultada, IdentificadorEstudiante estudiante) {
            if (convocatoria.equals(consultada) && estudiante.valor().equals(titular.toString())) {
                return Titularidad.PROPIO;
            }
            return Titularidad.AJENO;
        }
    }
}
