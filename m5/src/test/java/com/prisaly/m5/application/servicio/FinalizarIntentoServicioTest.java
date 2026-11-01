package com.prisaly.m5.application.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.intento.MotivoFinalizacionRequeridoException;
import com.prisaly.m5.domain.intento.TransicionIntentoInvalidaException;

@DisplayName("FinalizarIntentoServicio")
class FinalizarIntentoServicioTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");

    private RepositorioIntentosEnMemoria repositorio;
    private FinalizarIntentoServicio servicio;
    private Intento intento;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioIntentosEnMemoria();
        servicio = new FinalizarIntentoServicio(repositorio);
        intento = repositorio.existente(REF, Intento.iniciar());
    }

    @Test
    @DisplayName("finaliza con el motivo indicado y guarda una vez")
    void finalizaIntento() {
        ResumenIntento resumen = servicio.finalizar(REF, MotivoFinalizacion.TIEMPO_VENCIDO);

        assertEquals(EstadoIntento.FINALIZADO, resumen.estado());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), resumen.motivoFinalizacion());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("rechaza una segunda finalización sin guardar ni cambiar el motivo")
    void rechazaSegundaFinalizacion() {
        servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(TransicionIntentoInvalidaException.class,
                () -> servicio.finalizar(REF, MotivoFinalizacion.TIEMPO_VENCIDO));

        assertEquals(1, repositorio.guardados());
        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("propaga el motivo obligatorio sin guardar")
    void rechazaMotivoNulo() {
        assertThrows(MotivoFinalizacionRequeridoException.class, () -> servicio.finalizar(REF, null));

        assertEquals(0, repositorio.guardados());
        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
    }

    @Test
    @DisplayName("informa un intento inexistente sin guardar")
    void rechazaIntentoInexistente() {
        assertThrows(IntentoNoEncontradoException.class,
                () -> servicio.finalizar(new ReferenciaIntento("I-X"), MotivoFinalizacion.TIEMPO_VENCIDO));

        assertEquals(0, repositorio.guardados());
    }
}
