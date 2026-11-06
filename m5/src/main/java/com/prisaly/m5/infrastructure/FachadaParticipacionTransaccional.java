package com.prisaly.m5.infrastructure;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Decorador transaccional de {@link ParticipacionFacade}: cada caso de uso es una única transacción
 * (cargar con bloqueo, operar el dominio, guardar). Cualquier excepción —de dominio, de aplicación
 * o de la base— revierte todo lo escrito por esa operación.
 *
 * <p>La fachada de aplicación sigue libre de Spring. Este decorador NO es un bean: {@link
 * ConfiguracionParticipacion} lo construye dentro de {@code AccesoProtegidoParticipacion}, que
 * verifica identidad y titularidad antes de delegar. Solo las pruebas de persistencia lo usan sin
 * el proxy.
 */
public class FachadaParticipacionTransaccional implements OperacionesParticipacion {

    private final ParticipacionFacade fachada;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;
    private final Consumer<ReferenciaConvocatoria> coordinacionInicio;

    public FachadaParticipacionTransaccional(
            ParticipacionFacade fachada, PlatformTransactionManager transacciones) {
        this(
                fachada,
                transacciones,
                convocatoria -> {
                    throw new ConsultaProgramacionNoDisponibleException();
                });
    }

    public FachadaParticipacionTransaccional(
            ParticipacionFacade fachada,
            PlatformTransactionManager transacciones,
            Consumer<ReferenciaConvocatoria> coordinacionInicio) {
        this.fachada = fachada;
        this.coordinacionInicio = Objects.requireNonNull(coordinacionInicio);
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * Una sola transacción de escritura para bloquear o crear la participación, leer sus intentos
     * y, si corresponde, insertar el primero. Si cualquier paso falla, no queda ni la participación
     * ni el intento.
     */
    @Override
    public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
        return en(
                escritura,
                () -> {
                    coordinacionInicio.accept(convocatoria);
                    return fachada.iniciarOReanudar(convocatoria);
                });
    }

    @Override
    public ResumenIntento consultarIntento(ReferenciaIntento intento) {
        return en(lectura, () -> fachada.consultarIntento(intento));
    }

    @Override
    public ResumenIntento registrarRespuesta(
            ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        return en(escritura, () -> fachada.registrarRespuesta(intento, pregunta, valor));
    }

    /** Operación provisional, pendiente de ratificación académica. */
    @Override
    public ResumenIntento actualizarRespuesta(
            ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        return en(escritura, () -> fachada.actualizarRespuesta(intento, pregunta, valor));
    }

    @Override
    public Respuesta guardarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor, int revisionEsperada) {
        return en(escritura, () -> fachada.guardarRespuesta(intento, pregunta, valor, revisionEsperada));
    }

    @Override
    public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
        return en(escritura, () -> fachada.finalizarIntento(intento, motivo));
    }

    private static <T> T en(TransactionTemplate plantilla, Supplier<T> operacion) {
        return plantilla.execute(estado -> operacion.get());
    }
}
