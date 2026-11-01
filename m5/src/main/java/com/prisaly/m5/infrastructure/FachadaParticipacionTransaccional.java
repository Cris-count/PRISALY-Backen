package com.prisaly.m5.infrastructure;

import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Decorador transaccional de {@link ParticipacionFacade}: cada caso de uso
 * es una única transacción (cargar con bloqueo, operar el dominio, guardar).
 * Cualquier excepción —de dominio, de aplicación o de la base— revierte todo
 * lo escrito por esa operación.
 *
 * <p>La fachada de aplicación sigue libre de Spring. Esta clase es la
 * operación que envuelve {@code AccesoProtegidoParticipacion}: un controlador de
 * estudiante nunca debe usar este decorador directamente, sino el proxy, que
 * verifica identidad y titularidad antes de delegar.
 */
public class FachadaParticipacionTransaccional implements OperacionesParticipacion {

    private final ParticipacionFacade fachada;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public FachadaParticipacionTransaccional(ParticipacionFacade fachada, PlatformTransactionManager transacciones) {
        this.fachada = fachada;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    @Override
    public ResumenIntento consultarIntento(ReferenciaIntento intento) {
        return en(lectura, () -> fachada.consultarIntento(intento));
    }

    @Override
    public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        return en(escritura, () -> fachada.registrarRespuesta(intento, pregunta, valor));
    }

    /** Operación provisional, pendiente de ratificación académica. */
    @Override
    public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        return en(escritura, () -> fachada.actualizarRespuesta(intento, pregunta, valor));
    }

    @Override
    public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
        return en(escritura, () -> fachada.finalizarIntento(intento, motivo));
    }

    private static ResumenIntento en(TransactionTemplate plantilla, Supplier<ResumenIntento> operacion) {
        return plantilla.execute(estado -> operacion.get());
    }
}
