package com.prisaly.m5.application.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

@DisplayName("ConsultarIntentoServicio")
class ConsultarIntentoServicioTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");

    private RepositorioIntentosEnMemoria repositorio;
    private ConsultarIntentoServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioIntentosEnMemoria();
        servicio = new ConsultarIntentoServicio(repositorio);
    }

    @Test
    @DisplayName("consulta un intento existente sin guardarlo")
    void consultaIntentoExistente() {
        Intento intento = repositorio.existente(REF, Intento.iniciar());
        intento.registrarRespuesta(new ReferenciaPregunta("P-1"), new ValorRespuesta("A"));

        ResumenIntento resumen = servicio.consultar(REF);

        assertEquals(EstadoIntento.EN_CURSO, resumen.estado());
        assertEquals(List.of(new Respuesta(new ReferenciaPregunta("P-1"), new ValorRespuesta("A"))),
                resumen.respuestas());
        assertEquals(0, repositorio.guardados());
    }

    @Test
    @DisplayName("informa la inexistencia del intento")
    void informaIntentoInexistente() {
        IntentoNoEncontradoException error =
                assertThrows(IntentoNoEncontradoException.class, () -> servicio.consultar(REF));

        assertEquals(REF, error.referencia());
    }

    @Test
    @DisplayName("rechaza una referencia nula y un repositorio nulo")
    void rechazaDatosNulos() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> servicio.consultar(null));
        assertThrows(DatoAplicacionInvalidoException.class, () -> new ConsultarIntentoServicio(null));
    }
}
