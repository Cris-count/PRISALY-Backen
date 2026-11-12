package com.prisaly.m5.infrastructure.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.application.service.SolicitudAccesoEstudianteService;
import com.prisaly.m1.application.service.ValidacionAccesoEstudianteService;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.ConjuntoClavesHmac;
import com.prisaly.m1.infrastructure.security.LimiteAccesoEstudianteEnMemoria;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The M1 student session (DD-02 alternative B, DC-DD02-B4-B5-01) only opens the attempt and the call of its owner.
 * The session is obtained through the real B services (request + validation of an individual code).
 */
class SesionEstudianteTitularidadTest {

    private static String clave(String kid) {
        byte[] v = new byte[32];
        new SecureRandom().nextBytes(v);
        return kid + ":" + Base64.getEncoder().encodeToString(v);
    }

    @Test
    void laSesionDeM1SoloAbreElIntentoYLaConvocatoriaPropios() {
        UUID estudiante = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();
        Cuentas cuentas = new Cuentas();
        cuentas.alta(UUID.randomUUID(), estudiante, "propio@prisaly.test");
        cuentas.alta(UUID.randomUUID(), ajeno, "ajeno@prisaly.test");
        var claves = new ClavesAccesoEstudiante(ConjuntoClavesHmac.leer("K-COD", clave("c")),
                ConjuntoClavesHmac.leer("K-SES", clave("s")));
        var limites = new LimiteAccesoEstudianteEnMemoria(5, 20, 20, Duration.ofMinutes(15), Clock.systemUTC());
        List<CodigoAccesoDeliveryCommand> entregados = new ArrayList<>();
        TransactionTemplate sinTx = new TransactionTemplate() {
            @Override public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> a) {
                return a.doInTransaction(new SimpleTransactionStatus());
            }
        };
        var solicitud = new SolicitudAccesoEstudianteService(cuentas, limites, entregados::add, claves, sinTx, Clock.systemUTC());
        var validacion = new ValidacionAccesoEstudianteService(cuentas, limites, claves);
        AtomicReference<String> token = new AtomicReference<>();
        TokenSesionEstudianteActual fuente = () -> Optional.ofNullable(token.get());
        var acceso = new SessionStudentAccessService(cuentas, fuente, claves);
        ReferenciaIntento intento = new ReferenciaIntento(UUID.randomUUID().toString());
        ReferenciaConvocatoria propia = new ReferenciaConvocatoria(UUID.randomUUID().toString());
        ControlAccesoIntentos control = new ControlAccesoIntentos(
                new IdentidadEstudianteDesdeM1(acceso), new TitularidadDePrueba(intento, propia, estudiante));

        UUID retoPropio = solicitud.solicitar("propio@prisaly.test", "o-propio");
        token.set(validacion.validar(retoPropio, entregados.get(0).codigoTemporal(), "o-propio").token());
        assertEquals(estudiante.toString(), control.verificar(intento).valor());
        assertEquals(estudiante.toString(), control.verificarConvocatoria(propia).valor());

        UUID retoAjeno = solicitud.solicitar("ajeno@prisaly.test", "o-ajeno");
        token.set(validacion.validar(retoAjeno, entregados.get(1).codigoTemporal(), "o-ajeno").token());
        assertThrows(AccesoIntentoDenegadoException.class, () -> control.verificar(intento));
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> control.verificarConvocatoria(propia));
        assertThrows(IntentoNoEncontradoException.class,
                () -> control.verificar(new ReferenciaIntento(UUID.randomUUID().toString())));
        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> control.verificarConvocatoria(new ReferenciaConvocatoria(UUID.randomUUID().toString())));

        cuentas.fallar = true;
        assertThrows(IllegalStateException.class, () -> control.verificar(intento));
    }

    private static final class Cuentas implements AccesoEstudiantePort {
        private final Map<UUID, VinculoEstudiante> vinculos = new ConcurrentHashMap<>();
        private final Map<UUID, CredencialNueva> credenciales = new ConcurrentHashMap<>();
        private final Map<UUID, Boolean> consumidas = new ConcurrentHashMap<>();
        private final Map<String, SesionGuardada> sesiones = new ConcurrentHashMap<>();
        private boolean fallar;

        void alta(UUID userId, UUID studentId, String correo) {
            vinculos.put(userId, new VinculoEstudiante(userId, studentId, correo, true));
        }

        @Override public Optional<VinculoEstudiante> vinculoPorCorreo(String correo) {
            return vinculos.values().stream().filter(v -> v.correo().equals(correo)).findFirst();
        }
        @Override public Optional<VinculoEstudiante> vinculo(UUID idUsuario) {
            exigir();
            return Optional.ofNullable(vinculos.get(idUsuario));
        }
        @Override public void revocarCredencialesAbiertas(UUID idUsuario, OffsetDateTime cuando) { }
        @Override public void guardarCredencial(CredencialNueva c) { credenciales.put(c.challengeId(), c); }
        @Override public Optional<CredencialGuardada> bloquearCredencial(UUID id) {
            CredencialNueva c = credenciales.get(id);
            return c == null ? Optional.empty() : Optional.of(new CredencialGuardada(id, c.userId(), c.studentId(),
                    c.codigoHmac(), c.kidCodigo(), c.expira(), consumidas.containsKey(id), false, 0));
        }
        @Override public void registrarIntentoFallido(UUID challengeId) { }
        @Override public boolean consumir(UUID id, OffsetDateTime ahora) { return consumidas.putIfAbsent(id, true) == null; }
        @Override public void abrirSesion(SesionNueva s) {
            sesiones.put(Arrays.toString(s.tokenHmac()), new SesionGuardada(s.userId(), s.studentId()));
        }
        @Override public Optional<SesionGuardada> sesionVigente(List<byte[]> candidatos, OffsetDateTime ahora) {
            exigir();
            return candidatos.stream().map(h -> sesiones.get(Arrays.toString(h))).filter(java.util.Objects::nonNull).findFirst();
        }
        @Override public Optional<UUID> revocarSesion(List<byte[]> candidatos, OffsetDateTime cuando) { throw new UnsupportedOperationException(); }
        @Override public void auditar(String e, UUID s, String r, String o, OffsetDateTime c) { }
        @Override public Purga purgar(OffsetDateTime ahora, int dias) { return new Purga(0, 0, 0); }

        private void exigir() {
            if (fallar) {
                throw new IllegalStateException("identidad no disponible");
            }
        }
    }

    private record TitularidadDePrueba(ReferenciaIntento intento, ReferenciaConvocatoria convocatoria, UUID titular)
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
