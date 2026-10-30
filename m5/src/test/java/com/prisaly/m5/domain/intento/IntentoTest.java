package com.prisaly.m5.domain.intento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Intento: ciclo de vida")
class IntentoTest {

    @Test
    @DisplayName("1. un intento nuevo inicia en EnCurso y sin motivo de finalización")
    void unIntentoNuevoIniciaEnCurso() {
        Intento intento = Intento.iniciar();

        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
        assertTrue(intento.estaEnCurso());
        assertFalse(intento.estaFinalizado());
        assertEquals(Optional.empty(), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("2. finalizar un intento en curso lo deja Finalizado")
    void finalizarUnIntentoEnCursoLoDejaFinalizado() {
        Intento intento = Intento.iniciar();

        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
        assertTrue(intento.estaFinalizado());
        assertFalse(intento.estaEnCurso());
    }

    @ParameterizedTest(name = "3. registra el motivo {0}")
    @EnumSource(MotivoFinalizacion.class)
    void finalizarRegistraElMotivo(MotivoFinalizacion motivo) {
        Intento intento = Intento.iniciar();

        intento.finalizar(motivo);

        assertEquals(Optional.of(motivo), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("3b. TIEMPO_VENCIDO es un motivo: el estado resultante es Finalizado")
    void tiempoVencidoNoEsUnEstado() {
        Intento intento = Intento.iniciar();

        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
    }

    @Test
    @DisplayName("4. un intento Finalizado no puede regresar a EnCurso")
    void unIntentoFinalizadoNoRegresaAEnCurso() {
        Intento intento = Intento.iniciar();
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertFalse(intento.estado().puedeTransicionarA(EstadoIntento.EN_CURSO));
        assertFalse(intento.estaEnCurso());
    }

    @Test
    @DisplayName("5. tras finalizar se rechaza cambiar el motivo y se conserva el original")
    void trasFinalizarSeRechazaCambiarElMotivo() {
        Intento intento = Intento.iniciar();
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        assertThrows(TransicionIntentoInvalidaException.class,
                () -> intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE));

        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
    }

    @Test
    @DisplayName("6. una segunda finalización idéntica se rechaza de forma consistente")
    void unaSegundaFinalizacionIdenticaSeRechaza() {
        Intento intento = Intento.iniciar();
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        TransicionIntentoInvalidaException error = assertThrows(
                TransicionIntentoInvalidaException.class,
                () -> intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE));

        assertEquals(EstadoIntento.FINALIZADO, error.origen());
        assertEquals(EstadoIntento.FINALIZADO, error.destino());
        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("7. finalizar sin motivo se rechaza y el intento sigue EnCurso")
    void finalizarSinMotivoSeRechaza() {
        Intento intento = Intento.iniciar();

        assertThrows(MotivoFinalizacionRequeridoException.class, () -> intento.finalizar(null));

        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
        assertEquals(Optional.empty(), intento.motivoFinalizacion());
    }
}
