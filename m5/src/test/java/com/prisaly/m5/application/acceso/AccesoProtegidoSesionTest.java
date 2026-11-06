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
import com.prisaly.m5.application.ReferenciaSesion;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/** D01: iniciar o reanudar por sesión, con la convocatoria resuelta por M4 y verificada después. */
@DisplayName("AccesoProtegidoParticipacion: iniciar o reanudar por sesión (D01)")
class AccesoProtegidoSesionTest {

    private static final ReferenciaSesion SESION = new ReferenciaSesion("S-1");
    private static final ReferenciaConvocatoria DE_ANA = new ReferenciaConvocatoria("C-ana");
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante("E-ana");

    private Optional<IdentificadorEstudiante> autenticado = Optional.of(ANA);
    private Optional<ReferenciaConvocatoria> resuelta = Optional.of(DE_ANA);
    private RuntimeException falloM4;
    private final List<String> consultas = new ArrayList<>();
    private final List<ReferenciaConvocatoria> delegadas = new ArrayList<>();
    private final ResultadoInicio resultado = new ResultadoInicio(
            new ResumenIntento(new ReferenciaIntento("I-1"), EstadoIntento.EN_CURSO, Optional.empty(), List.of()),
            Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false);

    private final TitularidadIntentos titularidad = new TitularidadIntentos() {
        @Override
        public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
            throw new AssertionError("iniciar no verifica intentos");
        }

        @Override
        public Titularidad deConvocatoria(ReferenciaConvocatoria convocatoria, IdentificadorEstudiante estudiante) {
            consultas.add("convocatoria " + convocatoria.valor());
            return DE_ANA.equals(convocatoria) && ANA.equals(estudiante) ? Titularidad.PROPIO : Titularidad.AJENO;
        }

        @Override
        @SuppressWarnings("OptionalAssignedToNull")
        public Optional<ReferenciaConvocatoria> convocatoriaEnSesion(ReferenciaSesion sesion, IdentificadorEstudiante estudiante) {
            consultas.add("sesion " + sesion.valor() + "/" + estudiante.valor());
            if (falloM4 != null) {
                throw falloM4;
            }
            return resuelta;
        }
    };

    private AccesoProtegidoParticipacion acceso(TitularidadIntentos proveedor) {
        return new AccesoProtegidoParticipacion(new ControlAccesoIntentos(() -> autenticado, proveedor), new OperacionesInicio());
    }

    @Test
    @DisplayName("la convocatoria resuelta por M4 se vuelve a verificar y llega al caso de uso")
    void titularDelega() {
        assertSame(resultado, acceso(titularidad).iniciarOReanudarEnSesion(SESION));
        assertEquals(List.of(DE_ANA), delegadas);
        assertEquals(List.of("sesion S-1/E-ana", "convocatoria C-ana"), consultas);
    }

    @Test
    @DisplayName("una sesión sin convocatoria propia responde como convocatoria no encontrada, sin delegar")
    void sinConvocatoria() {
        resuelta = Optional.empty();
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> acceso(titularidad).iniciarOReanudarEnSesion(SESION));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("una respuesta nula del proveedor se deniega")
    @SuppressWarnings("OptionalAssignedToNull")
    void respuestaNula() {
        resuelta = null;
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> acceso(titularidad).iniciarOReanudarEnSesion(SESION));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("sin identidad no se consulta M4 ni se delega")
    void sinIdentidad() {
        autenticado = Optional.empty();
        assertThrows(EstudianteNoIdentificadoException.class, () -> acceso(titularidad).iniciarOReanudarEnSesion(SESION));
        assertEquals(List.of(), consultas);
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("la indisponibilidad de M4 se propaga y no se delega")
    void m4Indisponible() {
        falloM4 = new ConsultaProgramacionNoDisponibleException();
        assertThrows(ConsultaProgramacionNoDisponibleException.class,
                () -> acceso(titularidad).iniciarOReanudarEnSesion(SESION));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("un proveedor que no implementa la resolución por sesión deniega por defecto")
    void proveedorSinResolucion() {
        TitularidadIntentos sinSesion = (intento, estudiante) -> Titularidad.PROPIO;
        assertThrows(ConvocatoriaNoEncontradaException.class, () -> acceso(sinSesion).iniciarOReanudarEnSesion(SESION));
        assertEquals(List.of(), delegadas);
    }

    @Test
    @DisplayName("la sesión es obligatoria")
    void sesionObligatoria() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> acceso(titularidad).iniciarOReanudarEnSesion(null));
    }

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
