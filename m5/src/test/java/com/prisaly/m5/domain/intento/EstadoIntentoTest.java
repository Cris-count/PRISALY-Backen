package com.prisaly.m5.domain.intento;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EstadoIntento")
class EstadoIntentoTest {

    @Test
    @DisplayName("solo existen los estados aprobados EnCurso y Finalizado")
    void soloExistenLosEstadosAprobados() {
        assertArrayEquals(
                new EstadoIntento[] {EstadoIntento.EN_CURSO, EstadoIntento.FINALIZADO},
                EstadoIntento.values());
    }

    @Test
    @DisplayName("EnCurso puede pasar a Finalizado")
    void enCursoPuedePasarAFinalizado() {
        assertTrue(EstadoIntento.EN_CURSO.puedeTransicionarA(EstadoIntento.FINALIZADO));
    }

    @Test
    @DisplayName("Finalizado no puede regresar a EnCurso")
    void finalizadoNoPuedeRegresarAEnCurso() {
        assertFalse(EstadoIntento.FINALIZADO.puedeTransicionarA(EstadoIntento.EN_CURSO));
    }

    @Test
    @DisplayName("ningún estado puede transicionar hacia sí mismo")
    void ningunEstadoTransicionaHaciaSiMismo() {
        assertFalse(EstadoIntento.EN_CURSO.puedeTransicionarA(EstadoIntento.EN_CURSO));
        assertFalse(EstadoIntento.FINALIZADO.puedeTransicionarA(EstadoIntento.FINALIZADO));
    }

    @Test
    @DisplayName("solo EnCurso admite modificaciones")
    void soloEnCursoAdmiteModificaciones() {
        assertTrue(EstadoIntento.EN_CURSO.admiteModificaciones());
        assertFalse(EstadoIntento.FINALIZADO.admiteModificaciones());
    }
}
