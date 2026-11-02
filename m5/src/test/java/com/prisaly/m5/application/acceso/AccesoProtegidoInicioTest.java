package com.prisaly.m5.application.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/** Proxy de protección en iniciar o reanudar: identidad (M1) y titularidad de la convocatoria (M4). */
@DisplayName("AccesoProtegidoParticipacion: iniciar o reanudar")
class AccesoProtegidoInicioTest {

    private static final ReferenciaConvocatoria DE_ANA = new ReferenciaConvocatoria("C-ana");
    private static final ReferenciaConvocatoria DE_LUIS = new ReferenciaConvocatoria("C-luis");
    private static final ReferenciaConvocatoria INEXISTENTE = new ReferenciaConvocatoria("C-x");
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante("E-ana");

    private Optional<IdentificadorEstudiante> autenticado = Optional.of(ANA);
    private RuntimeException falloM4;
    private final List<String> consultadas = new ArrayList<>();
    private final List<ReferenciaConvocatoria> delegadas = new ArrayList<>();
    private final ResultadoInicio resultado = new ResultadoInicio(
            new ResumenIntento(new ReferenciaIntento("I-1"), EstadoIntento.EN_CURSO, Optional.empty(), List.of()),
            Instant.EPOCH, Instant.EPOCH.plusSeconds(60), true);

    private final TitularidadIntentos titularidad = new TitularidadIntentos() {
        @Override
        public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
            throw new AssertionError("iniciar no verifica intentos sino la convocatoria");
        }

        @Override
        public Titularidad deConvocatoria(ReferenciaConvocatoria convocatoria, IdentificadorEstudiante estudiante) {
            consultadas.add(convocatoria.valor() + "/" + estudiante.valor());
            if (falloM4 != null) {
                throw falloM4;
            }
            if (INEXISTENTE.equals(convocatoria)) {
                return Titularidad.INEXISTENTE;
            }
            return DE_ANA.equals(convocatoria) && ANA.equals(estudiante) ? Titularidad.PROPIO : Titularidad.AJENO;
        }
    };

    private final AccesoProtegidoParticipacion acceso = new AccesoProtegidoParticipacion(
            new ControlAccesoIntentos(() -> autenticado, titularidad), new OperacionesInicio());

    @Test
    @DisplayName("el titular autenticado llega al caso de uso con su convocatoria")
    void titularDelega() {
        assertSame(resultado, acceso.iniciarOReanudar(DE_ANA));
        assertEquals(List.of(DE_ANA), delegadas);
        assertEquals(List.of("C-ana/E-ana"), consultadas);
    }

    @Test
    @DisplayName("una convocatoria ajena no se inicia ni se reanuda, y no se distingue de una inexistente")
    void convocatoriaAjena() {
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> acceso.iniciarOReanudar(DE_LUIS));
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> acceso.iniciarOReanudar(INEXISTENTE));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("el mensaje no repite la referencia enviada por el cliente")
    void mensajeSinReferencia() {
        ConvocatoriaNoEncontradaException error =
                assertThrows(ConvocatoriaNoEncontradaException.class, () -> acceso.iniciarOReanudar(DE_LUIS));
        assertEquals(false, error.getMessage().contains("C-luis"));
    }

    @Test
    @DisplayName("sin identidad no se consulta M4 ni se delega")
    void sinIdentidad() {
        autenticado = Optional.empty();
        assertThrows(EstudianteNoIdentificadoException.class, () -> acceso.iniciarOReanudar(DE_ANA));
        assertEquals(List.of(), consultadas);
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("M4 caído: se propaga su error y no se delega")
    void m4Caido() {
        falloM4 = new ConsultaProgramacionNoDisponibleException();
        assertThrows(ConsultaProgramacionNoDisponibleException.class, () -> acceso.iniciarOReanudar(DE_ANA));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("una respuesta nula del proveedor de titularidad se deniega")
    void titularidadNula() {
        TitularidadIntentos nula = new TitularidadIntentos() {
            @Override
            public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
                return null;
            }

            @Override
            public Titularidad deConvocatoria(ReferenciaConvocatoria convocatoria, IdentificadorEstudiante estudiante) {
                return null;
            }
        };
        AccesoProtegidoParticipacion conNula = new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(() -> autenticado, nula), new OperacionesInicio());
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> conNula.iniciarOReanudar(DE_ANA));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("la titularidad por defecto (sin M4) deniega iniciar")
    void titularidadPorDefectoDeniega() {
        TitularidadIntentos soloIntentos = (intento, estudiante) -> Titularidad.PROPIO;
        AccesoProtegidoParticipacion conDefecto = new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(() -> autenticado, soloIntentos), new OperacionesInicio());
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> conDefecto.iniciarOReanudar(DE_ANA));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("una convocatoria nula es un dato inválido")
    void convocatoriaNula() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> acceso.iniciarOReanudar(null));
        assertEquals(List.of(), consultadas);
    }

    /** Doble del caso de uso: solo registra iniciar o reanudar. */
    private final class OperacionesInicio implements OperacionesParticipacion {
        @Override
        public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
            delegadas.add(convocatoria);
            return resultado;
        }

        @Override
        public ResumenIntento consultarIntento(ReferenciaIntento intento) {
            throw new AssertionError();
        }

        @Override
        public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
            throw new AssertionError();
        }

        @Override
        public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
            throw new AssertionError();
        }

        @Override
        public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
            throw new AssertionError();
        }
    }
}
