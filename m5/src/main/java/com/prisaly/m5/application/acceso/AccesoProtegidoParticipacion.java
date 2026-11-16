package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.port.ConsultaContenidoIntento;
import java.util.function.Function;
import java.util.function.Supplier;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ReferenciaSesion;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.fase.CompletitudIntento;
import com.prisaly.m5.application.fase.EntradaCuestionario;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.FasesNoDisponiblesException;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Proxy de protección (patrón Protection Proxy) de las operaciones de M5 para
 * un estudiante: verifica identidad (M1) y titularidad (M4) con
 * {@link ControlAccesoIntentos} antes de delegar, y no delega si la
 * verificación falla.
 *
 * <p>Ninguna operación recibe la identidad del estudiante: la obtiene el
 * servidor. Es el punto de entrada previsto para los futuros controladores de
 * estudiante, que siguen sin existir (ARQ-008.3 y DD-02 pendientes). Con
 * {@code DenyingStudentAccessService} de M1, toda llamada se deniega.
 */
public final class AccesoProtegidoParticipacion implements OperacionesParticipacion {

    private final ControlAccesoIntentos control;
    private final OperacionesParticipacion operaciones;
    private final ConsultaContenidoIntento contenido;
    private final Function<Supplier<ContenidoIntento>, ContenidoIntento> lecturaContenido;
    /** Disponibilidad de las operaciones candidatas P1–P4 (DC-BE-SIM-B1-B2A-CORRECCION-26, QA-B25-01). */
    private final boolean operacionesCandidatas;

    public AccesoProtegidoParticipacion(ControlAccesoIntentos control, OperacionesParticipacion operaciones) {
        this(control, operaciones,
                ConsultaContenidoIntento.noDisponible(),
                Supplier::get);
    }

    /** El ensamblaje aporta la frontera transaccional; aplicación permanece libre de Spring. Sin operaciones candidatas. */
    public AccesoProtegidoParticipacion(ControlAccesoIntentos control, OperacionesParticipacion operaciones,
            ConsultaContenidoIntento contenido,
            Function<Supplier<ContenidoIntento>, ContenidoIntento> lecturaContenido) {
        this(control, operaciones, contenido, lecturaContenido, false);
    }

    /**
     * @param operacionesCandidatas {@code true} solo si el ensamblaje construyó las fases académicas
     *     ({@code prisaly.m5.exploracion.fases.enabled=true}); con {@code false} P1–P4 fallan cerradas antes de
     *     resolver la identidad, leer contenido o abrir una transacción.
     */
    public AccesoProtegidoParticipacion(ControlAccesoIntentos control, OperacionesParticipacion operaciones,
            ConsultaContenidoIntento contenido,
            Function<Supplier<ContenidoIntento>, ContenidoIntento> lecturaContenido,
            boolean operacionesCandidatas) {
        if (control == null || operaciones == null || contenido == null || lecturaContenido == null) {
            throw new DatoAplicacionInvalidoException("El acceso protegido requiere control y operaciones");
        }
        this.control = control;
        this.operaciones = operaciones;
        this.contenido = contenido;
        this.lecturaContenido = lecturaContenido;
        this.operacionesCandidatas = operacionesCandidatas;
    }

    /** Identidad/titularidad y lectura académica comparten el snapshot abierto por el ensamblaje. */
    @Override
    public ContenidoIntento consultarContenido(ReferenciaIntento intento) {
        return lecturaContenido.apply(() -> {
            var estudiante = control.verificar(intento);
            return contenido.consultar(intento, estudiante);
        });
    }

    /**
     * La convocatoria debe ser del estudiante autenticado; el intento que se
     * reanuda pertenece a ella, así que también es suyo.
     */
    @Override
    public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
        control.verificarConvocatoria(convocatoria);
        return operaciones.iniciarOReanudar(convocatoria);
    }

    /**
     * Inicio o reanudación por sesión (D01): la convocatoria se resuelve exclusivamente con el
     * contrato de M4 para el estudiante autenticado, y después se aplica la misma verificación de
     * {@link #iniciarOReanudar(ReferenciaConvocatoria)}. La elegibilidad no se evalúa aquí.
     */
    public ResultadoInicio iniciarOReanudarEnSesion(ReferenciaSesion sesion) {
        return iniciarOReanudar(control.convocatoriaPropiaEnSesion(sesion));
    }

    @Override
    public ResumenIntento consultarIntento(ReferenciaIntento intento) {
        control.verificar(intento);
        return operaciones.consultarIntento(intento);
    }

    @Override
    public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        control.verificar(intento);
        return operaciones.registrarRespuesta(intento, pregunta, valor);
    }

    /** Operación provisional, pendiente de ratificación académica. */
    @Override
    public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        control.verificar(intento);
        return operaciones.actualizarRespuesta(intento, pregunta, valor);
    }

    /** Guardado con control de revisión (D06), verificado como las demás operaciones del intento. */
    @Override
    public Respuesta guardarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor, int revisionEsperada) {
        control.verificar(intento);
        return operaciones.guardarRespuesta(intento, pregunta, valor, revisionEsperada);
    }

    /**
     * El motivo lo decide el servidor (no hay DTO de finalización): el controlador no lo acepta
     * del cliente.
     */
    @Override
    public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
        control.verificar(intento);
        return operaciones.finalizarIntento(intento, motivo);
    }

    // DC-BE-SIM-B1-B2A-24 (Incremento B, B2a): las cuatro operaciones candidatas pasan por el mismo control de identidad
    // (M1) y titularidad (M4) antes de delegar. No hay ruta HTTP que las publique.
    // DC-BE-SIM-B1-B2A-CORRECCION-26 (QA-B25-01): y antes de eso, por la guarda del interruptor. P4 se compone aquí y no
    // en la fachada, así que no le alcanzaban los rechazos de la fachada sin fases.

    private void exigirOperacionesCandidatas() {
        if (!operacionesCandidatas) {
            throw new FasesNoDisponiblesException();
        }
    }

    @Override
    public EstadoFase consultarFase(ReferenciaIntento intento) {
        exigirOperacionesCandidatas();
        control.verificar(intento);
        return operaciones.consultarFase(intento);
    }

    @Override
    public EntradaCuestionario iniciarCuestionario(ReferenciaIntento intento) {
        exigirOperacionesCandidatas();
        control.verificar(intento);
        return operaciones.iniciarCuestionario(intento);
    }

    @Override
    public NarrativaIntento consultarNarrativa(ReferenciaIntento intento) {
        exigirOperacionesCandidatas();
        control.verificar(intento);
        return operaciones.consultarNarrativa(intento);
    }

    /**
     * Completitud informativa: titularidad, contenido autorizado y respuestas se leen en la misma instantánea que
     * {@link #consultarContenido}, así que el recuento no puede mezclar dos estados del intento.
     */
    @Override
    public CompletitudIntento consultarCompletitud(ReferenciaIntento intento) {
        exigirOperacionesCandidatas();
        ResumenIntento[] respuestas = new ResumenIntento[1];
        ContenidoIntento autorizado = lecturaContenido.apply(() -> {
            var estudiante = control.verificar(intento);
            ContenidoIntento leido = contenido.consultar(intento, estudiante);
            respuestas[0] = operaciones.consultarIntento(intento);
            return leido;
        });
        return CompletitudIntento.de(autorizado, respuestas[0]);
    }
}
