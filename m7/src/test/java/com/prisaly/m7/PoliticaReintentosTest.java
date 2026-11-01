package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m7.application.PoliticaReintentos;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PoliticaReintentosTest {

    private final PoliticaReintentos politica = PoliticaReintentos.ratificada();

    @Test
    void backoffRatificado() {
        assertEquals(Duration.ofMinutes(1), politica.esperaTrasIntento(1));
        assertEquals(Duration.ofMinutes(2), politica.esperaTrasIntento(2));
        assertEquals(Duration.ofMinutes(4), politica.esperaTrasIntento(3));
        assertEquals(Duration.ofMinutes(8), politica.esperaTrasIntento(4));
    }

    @Test
    void topeDeSesentaMinutos() {
        assertEquals(Duration.ofMinutes(60), politica.esperaTrasIntento(10));
    }

    @Test
    void agotaALosCincoIntentos() {
        assertFalse(politica.agotoIntentos(4));
        assertTrue(politica.agotoIntentos(5));
        assertEquals(5, politica.maxIntentos());
    }
}
