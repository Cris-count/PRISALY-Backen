package com.prisaly.m5.application.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.application.RepositorioParticipacionesEnMemoria;
import com.prisaly.m5.application.RepositorioParticipacionesEnMemoria.FilaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.inicio.DecisionPendiente;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.inicio.ElegibilidadNoRatificada;
import com.prisaly.m5.application.inicio.InicioNoAutorizadoException;
import com.prisaly.m5.application.inicio.InicioNoDisponibleException;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;
import com.prisaly.m5.application.inicio.LimiteTemporalNoRatificado;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Caso de uso de inicio y reanudación con dobles en memoria. Las políticas
 * "elegible" y "límite fijo" son dobles de prueba, no reglas académicas.
 * Bloqueos, unicidad y transacciones reales: {@code InicioIntentoIT}.
 */
@DisplayName("IniciarIntentoServicio")
class IniciarIntentoServicioTest {

    private static final ReferenciaConvocatoria CONVOCATORIA = new ReferenciaConvocatoria("C-ana");
    private static final Instant AHORA = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant INICIO_PREVIO = Instant.parse("2026-11-01T14:10:00Z");
    private static final Instant LIMITE_PREVIO = Instant.parse("2026-11-01T15:10:00Z");

    private final RepositorioIntentosEnMemoria intentos = new RepositorioIntentosEnMemoria();
    private final RepositorioParticipacionesEnMemoria participaciones = new RepositorioParticipacionesEnMemoria(intentos);
    private final AtomicInteger elegibilidadConsultada = new AtomicInteger();
    private final AtomicInteger limiteConsultado = new AtomicInteger();

    private ElegibilidadInicio elegibilidad(ElegibilidadInicio.Resultado resultado) {
        return convocatoria -> {
            elegibilidadConsultada.incrementAndGet();
            return resultado;
        };
    }

    private LimiteTemporalInicio limite(Duration duracion) {
        return (convocatoria, inicio) -> {
            limiteConsultado.incrementAndGet();
            return Optional.of(inicio.plus(duracion));
        };
    }

    private IniciarIntentoServicio servicio(ElegibilidadInicio elegibilidad, LimiteTemporalInicio limite) {
        participaciones.reloj(AHORA);
        return new IniciarIntentoServicio(participaciones, intentos, elegibilidad, limite);
    }

    private IniciarIntentoServicio autorizado() {
        return servicio(elegibilidad(ElegibilidadInicio.Resultado.ELEGIBLE), limite(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("primer inicio: crea la participación y el intento 1 EnCurso con el reloj de la base")
    void primerInicio() {
        ResultadoInicio resultado = autorizado().iniciarOReanudar(CONVOCATORIA);

        assertFalse(resultado.reanudado());
        assertEquals(EstadoIntento.EN_CURSO, resultado.intento().estado());
        assertEquals(List.of(), resultado.intento().respuestas());
        assertEquals(AHORA, resultado.inicio());
        assertEquals(AHORA.plus(Duration.ofMinutes(30)), resultado.limiteTemporal());
        assertEquals(List.of("participacion", "intento"), participaciones.escrituras());
        List<FilaIntento> filas = participaciones.intentosDe(CONVOCATORIA);
        assertEquals(1, filas.size());
        assertEquals(1, filas.get(0).numero());
        assertEquals(resultado.intento().referencia(), filas.get(0).referencia());
    }

    @Test
    @DisplayName("reanudación: devuelve el mismo intento, sus respuestas, su inicio y su límite, sin escribir")
    void reanudacion() {
        Intento enCurso = Intento.iniciar();
        enCurso.registrarRespuesta(new ReferenciaPregunta("P-1"), new ValorRespuesta("O-1"));
        FilaIntento existente = participaciones.intentoExistente(CONVOCATORIA, enCurso, INICIO_PREVIO, LIMITE_PREVIO);

        ResultadoInicio resultado = autorizado().iniciarOReanudar(CONVOCATORIA);

        assertTrue(resultado.reanudado());
        assertEquals(existente.referencia(), resultado.intento().referencia());
        assertEquals(List.of(new Respuesta(new ReferenciaPregunta("P-1"), new ValorRespuesta("O-1"))),
                resultado.intento().respuestas());
        assertEquals(INICIO_PREVIO, resultado.inicio());
        assertEquals(LIMITE_PREVIO, resultado.limiteTemporal());
        assertEquals(1, participaciones.intentosDe(CONVOCATORIA).size());
        assertEquals(List.of(), participaciones.escrituras());
    }

    @Test
    @DisplayName("reanudar no consulta elegibilidad ni límite: el tiempo no se amplía ni se reinicia")
    void reanudarNoConsultaPoliticas() {
        participaciones.intentoExistente(CONVOCATORIA, Intento.iniciar(), INICIO_PREVIO, LIMITE_PREVIO);

        ResultadoInicio resultado = autorizado().iniciarOReanudar(CONVOCATORIA);

        assertEquals(LIMITE_PREVIO, resultado.limiteTemporal());
        assertEquals(0, elegibilidadConsultada.get());
        assertEquals(0, limiteConsultado.get());
    }

    @Test
    @DisplayName("reanudar dos veces devuelve siempre el mismo intento")
    void reanudarEsIdempotente() {
        IniciarIntentoServicio servicio = autorizado();
        ResultadoInicio primero = servicio.iniciarOReanudar(CONVOCATORIA);
        ResultadoInicio segundo = servicio.iniciarOReanudar(CONVOCATORIA);
        ResultadoInicio tercero = servicio.iniciarOReanudar(CONVOCATORIA);

        assertFalse(primero.reanudado());
        assertTrue(segundo.reanudado() && tercero.reanudado());
        assertEquals(primero.intento().referencia(), tercero.intento().referencia());
        assertEquals(primero.inicio(), tercero.inicio());
        assertEquals(primero.limiteTemporal(), tercero.limiteTemporal());
        assertEquals(1, participaciones.intentosDe(CONVOCATORIA).size());
        assertEquals(1, limiteConsultado.get());
    }

    @Test
    @DisplayName("con el intento finalizado, iniciar sería una repetición: se rechaza sin escribir (DD-14)")
    void intentoFinalizadoNoSeRepite() {
        Intento finalizado = Intento.iniciar();
        finalizado.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        participaciones.intentoExistente(CONVOCATORIA, finalizado, INICIO_PREVIO, LIMITE_PREVIO);

        assertThrows(RepeticionNoHabilitadaException.class, () -> autorizado().iniciarOReanudar(CONVOCATORIA));
        assertEquals(1, participaciones.intentosDe(CONVOCATORIA).size());
        assertEquals(List.of(), participaciones.escrituras());
        assertEquals(0, elegibilidadConsultada.get());
    }

    @Test
    @DisplayName("elegibilidad no ratificada: falla indicando la decisión pendiente y no escribe")
    void elegibilidadNoRatificada() {
        InicioNoDisponibleException error = assertThrows(InicioNoDisponibleException.class,
                () -> servicio(new ElegibilidadNoRatificada(), limite(Duration.ofMinutes(30))).iniciarOReanudar(CONVOCATORIA));

        assertEquals(DecisionPendiente.ELEGIBILIDAD_ACADEMICA, error.decision());
        assertTrue(error.getMessage().contains("elegibilidad"));
        assertEquals(List.of(), participaciones.escrituras());
        assertEquals(0, limiteConsultado.get());
    }

    @Test
    @DisplayName("una respuesta nula de elegibilidad se trata como no ratificada")
    void elegibilidadNula() {
        InicioNoDisponibleException error = assertThrows(InicioNoDisponibleException.class,
                () -> servicio(convocatoria -> null, limite(Duration.ofMinutes(30))).iniciarOReanudar(CONVOCATORIA));
        assertEquals(DecisionPendiente.ELEGIBILIDAD_ACADEMICA, error.decision());
        assertEquals(List.of(), participaciones.escrituras());
    }

    @Test
    @DisplayName("un estudiante no elegible según la regla no inicia")
    void noElegible() {
        assertThrows(InicioNoAutorizadoException.class,
                () -> servicio(elegibilidad(ElegibilidadInicio.Resultado.NO_ELEGIBLE), limite(Duration.ofMinutes(30)))
                        .iniciarOReanudar(CONVOCATORIA));
        assertEquals(List.of(), participaciones.escrituras());
    }

    @Test
    @DisplayName("límite temporal no ratificado: falla indicando la decisión pendiente y no escribe")
    void limiteNoRatificado() {
        InicioNoDisponibleException error = assertThrows(InicioNoDisponibleException.class,
                () -> servicio(elegibilidad(ElegibilidadInicio.Resultado.ELEGIBLE), new LimiteTemporalNoRatificado())
                        .iniciarOReanudar(CONVOCATORIA));

        assertEquals(DecisionPendiente.LIMITE_TEMPORAL, error.decision());
        assertEquals(List.of(), participaciones.escrituras());
    }

    @Test
    @DisplayName("un límite nulo se trata como no ratificado")
    void limiteNulo() {
        InicioNoDisponibleException error = assertThrows(InicioNoDisponibleException.class,
                () -> servicio(elegibilidad(ElegibilidadInicio.Resultado.ELEGIBLE), (c, i) -> null)
                        .iniciarOReanudar(CONVOCATORIA));
        assertEquals(DecisionPendiente.LIMITE_TEMPORAL, error.decision());
    }

    @Test
    @DisplayName("con las políticas de producción el primer inicio nunca se completa")
    void politicasDeProduccion() {
        assertThrows(InicioNoDisponibleException.class,
                () -> servicio(new ElegibilidadNoRatificada(), new LimiteTemporalNoRatificado()).iniciarOReanudar(CONVOCATORIA));
        assertTrue(participaciones.participacionDe(CONVOCATORIA).isEmpty());
    }

    @Test
    @DisplayName("participación existente sin intentos: inicia sobre ella sin crear otra")
    void participacionSinIntentos() {
        participaciones.participacionExistente(CONVOCATORIA);

        ResultadoInicio resultado = autorizado().iniciarOReanudar(CONVOCATORIA);

        assertFalse(resultado.reanudado());
        assertEquals(List.of("intento"), participaciones.escrituras());
    }

    @Test
    @DisplayName("si otra solicitud crea el intento mientras tanto, se reanuda el suyo y no se crea otro")
    void otraSolicitudSeAdelanto() {
        participaciones.alCrearParticipacion(() ->
                participaciones.intentoExistente(CONVOCATORIA, Intento.iniciar(), INICIO_PREVIO, LIMITE_PREVIO));

        ResultadoInicio resultado = autorizado().iniciarOReanudar(CONVOCATORIA);

        assertTrue(resultado.reanudado());
        assertEquals(LIMITE_PREVIO, resultado.limiteTemporal());
        assertEquals(1, participaciones.intentosDe(CONVOCATORIA).size());
        assertFalse(participaciones.escrituras().contains("intento"));
    }

    @Test
    @DisplayName("si otra solicitud se adelantó y su intento ya terminó, se rechaza la repetición")
    void otraSolicitudSeAdelantoYFinalizo() {
        participaciones.alCrearParticipacion(() -> {
            Intento finalizado = Intento.iniciar();
            finalizado.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);
            participaciones.intentoExistente(CONVOCATORIA, finalizado, INICIO_PREVIO, LIMITE_PREVIO);
        });

        assertThrows(RepeticionNoHabilitadaException.class, () -> autorizado().iniciarOReanudar(CONVOCATORIA));
        assertEquals(1, participaciones.intentosDe(CONVOCATORIA).size());
    }

    @Test
    @DisplayName("un error de persistencia al crear el intento se propaga sin convertirse en éxito")
    void errorDePersistencia() {
        FallaAlCrearIntento fallida = new FallaAlCrearIntento(new RepositorioParticipacionesEnMemoria(intentos));
        IniciarIntentoServicio servicio = new IniciarIntentoServicio(fallida, intentos,
                elegibilidad(ElegibilidadInicio.Resultado.ELEGIBLE), limite(Duration.ofMinutes(30)));

        assertThrows(IllegalStateException.class, () -> servicio.iniciarOReanudar(CONVOCATORIA));
        // La reversión de la participación ya creada la garantiza la transacción (InicioIntentoIT).
        assertEquals(List.of("participacion"), fallida.memoria.escrituras());
    }

    @Test
    @DisplayName("rechaza una convocatoria nula y dependencias nulas")
    void rechazaNulos() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> autorizado().iniciarOReanudar(null));
        assertThrows(DatoAplicacionInvalidoException.class,
                () -> new IniciarIntentoServicio(null, intentos, new ElegibilidadNoRatificada(), new LimiteTemporalNoRatificado()));
        assertThrows(DatoAplicacionInvalidoException.class,
                () -> new IniciarIntentoServicio(participaciones, intentos, null, new LimiteTemporalNoRatificado()));
        assertThrows(DatoAplicacionInvalidoException.class,
                () -> new IniciarIntentoServicio(participaciones, intentos, new ElegibilidadNoRatificada(), null));
    }

    /** Participaciones en memoria cuyo alta de intento falla como lo haría la base. */
    private static final class FallaAlCrearIntento implements RepositorioParticipaciones {
        final RepositorioParticipacionesEnMemoria memoria;

        FallaAlCrearIntento(RepositorioParticipacionesEnMemoria memoria) {
            this.memoria = memoria;
        }

        @Override
        public Optional<ReferenciaParticipacion> bloquear(ReferenciaConvocatoria convocatoria) {
            return memoria.bloquear(convocatoria);
        }

        @Override
        public ReferenciaParticipacion crearOBloquear(ReferenciaConvocatoria convocatoria) {
            return memoria.crearOBloquear(convocatoria);
        }

        @Override
        public Optional<IntentoEnCurso> intentoEnCurso(ReferenciaParticipacion participacion) {
            return memoria.intentoEnCurso(participacion);
        }

        @Override
        public boolean tieneIntentos(ReferenciaParticipacion participacion) {
            return memoria.tieneIntentos(participacion);
        }

        @Override
        public Instant ahora() {
            return AHORA;
        }

        @Override
        public ReferenciaIntento crearPrimerIntento(
                ReferenciaParticipacion participacion, Instant inicio, Instant limiteTemporal) {
            throw new IllegalStateException("fallo de la base al insertar");
        }
    }
}
