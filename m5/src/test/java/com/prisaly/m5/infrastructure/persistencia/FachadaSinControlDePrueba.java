package com.prisaly.m5.infrastructure.persistencia;

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.PlatformTransactionManager;

import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.inicio.ElegibilidadNoRatificada;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;
import com.prisaly.m5.application.inicio.LimiteTemporalNoRatificado;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.IniciarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;

/**
 * Solo pruebas de persistencia: la fachada transaccional SIN control de acceso,
 * para probar casos de uso, transacciones y concurrencia contra PostgreSQL.
 * En producción no existe como bean (ver {@code ConfiguracionParticipacion}).
 */
final class FachadaSinControlDePrueba {

    private FachadaSinControlDePrueba() {
    }

    /** Sin inicio operativo: las pruebas de responder y finalizar no lo usan. */
    static FachadaParticipacionTransaccional ensamblar(
            RepositorioIntentos repositorio, PlatformTransactionManager transacciones) {
        return ensamblar(repositorio, new SinParticipaciones(), transacciones,
                new ElegibilidadNoRatificada(), new LimiteTemporalNoRatificado());
    }

    /**
     * Con el inicio completo. Las políticas las elige la prueba: las de
     * {@link PoliticasInicioDePrueba} son dobles, no reglas académicas.
     */
    static FachadaParticipacionTransaccional ensamblar(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            PlatformTransactionManager transacciones,
            ElegibilidadInicio elegibilidad,
            LimiteTemporalInicio limiteTemporal) {
        return new FachadaParticipacionTransaccional(
                new ParticipacionFacade(
                        new ConsultarIntentoServicio(repositorio),
                        new RegistrarRespuestaServicio(repositorio),
                        new ActualizarRespuestaServicio(repositorio),
                        new FinalizarIntentoServicio(repositorio),
                        new IniciarIntentoServicio(participaciones, repositorio, elegibilidad, limiteTemporal)),
                transacciones);
    }

    /** Repositorio de participaciones ausente: iniciar falla si alguna prueba lo usa por error. */
    private static final class SinParticipaciones implements RepositorioParticipaciones {

        private static UnsupportedOperationException noDisponible() {
            return new UnsupportedOperationException("Esta fachada de prueba no inicia intentos");
        }

        @Override
        public Optional<ReferenciaParticipacion> bloquear(ReferenciaConvocatoria c) {
            throw noDisponible();
        }

        @Override
        public ReferenciaParticipacion crearOBloquear(ReferenciaConvocatoria c) {
            throw noDisponible();
        }

        @Override
        public Optional<IntentoEnCurso> intentoEnCurso(ReferenciaParticipacion p) {
            throw noDisponible();
        }

        @Override
        public boolean tieneIntentos(ReferenciaParticipacion p) {
            throw noDisponible();
        }

        @Override
        public Instant ahora() {
            throw noDisponible();
        }

        @Override
        public ReferenciaIntento crearPrimerIntento(
                ReferenciaParticipacion p, Instant inicio, Instant limite) {
            throw noDisponible();
        }
    }
}
