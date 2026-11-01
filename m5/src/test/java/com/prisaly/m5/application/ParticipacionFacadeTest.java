package com.prisaly.m5.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Comprueba la delegación de la fachada por sus efectos sobre servicios
 * reales y un único doble de repositorio.
 */
@DisplayName("ParticipacionFacade")
class ParticipacionFacadeTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");
    private static final ReferenciaPregunta P1 = new ReferenciaPregunta("P-1");
    private static final ValorRespuesta A = new ValorRespuesta("Opción A");
    private static final ValorRespuesta B = new ValorRespuesta("Opción B");

    private RepositorioIntentosEnMemoria repositorio;
    private ParticipacionFacade fachada;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioIntentosEnMemoria();
        repositorio.existente(REF, Intento.iniciar());
        fachada = new ParticipacionFacade(
                new ConsultarIntentoServicio(repositorio),
                new RegistrarRespuestaServicio(repositorio),
                new ActualizarRespuestaServicio(repositorio),
                new FinalizarIntentoServicio(repositorio));
    }

    @Test
    @DisplayName("recorre registrar, actualizar, finalizar y consultar delegando en cada servicio")
    void recorridoCompleto() {
        assertEquals(List.of(new Respuesta(P1, A)), fachada.registrarRespuesta(REF, P1, A).respuestas());
        assertEquals(List.of(new Respuesta(P1, B)), fachada.actualizarRespuesta(REF, P1, B).respuestas());
        assertEquals(EstadoIntento.FINALIZADO,
                fachada.finalizarIntento(REF, MotivoFinalizacion.TIEMPO_VENCIDO).estado());

        ResumenIntento consultado = fachada.consultarIntento(REF);

        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), consultado.motivoFinalizacion());
        assertEquals(List.of(new Respuesta(P1, B)), consultado.respuestas());
        assertEquals(3, repositorio.guardados());
    }

    @Test
    @DisplayName("propaga los errores de dominio y de aplicación sin guardar")
    void propagaErrores() {
        fachada.finalizarIntento(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(IntentoFinalizadoException.class, () -> fachada.registrarRespuesta(REF, P1, A));
        assertThrows(IntentoNoEncontradoException.class,
                () -> fachada.consultarIntento(new ReferenciaIntento("I-X")));
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("exige todos sus servicios")
    void exigeServicios() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> new ParticipacionFacade(null, null, null, null));
    }

    @Test
    @DisplayName("solo expone operaciones implementadas y nunca el agregado mutable")
    void soloExponeOperacionesImplementadas() {
        List<Method> publicos = Arrays.stream(ParticipacionFacade.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();
        for (Method metodo : publicos) {
            assertEquals(ResumenIntento.class, metodo.getReturnType(), metodo.getName());
        }
        Set<String> operaciones = publicos.stream().map(Method::getName).collect(Collectors.toSet());

        assertEquals(Set.of("consultarIntento", "registrarRespuesta", "actualizarRespuesta", "finalizarIntento"),
                operaciones);
    }
}
