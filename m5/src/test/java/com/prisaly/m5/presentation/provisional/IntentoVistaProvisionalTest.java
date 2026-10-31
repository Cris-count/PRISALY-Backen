package com.prisaly.m5.presentation.provisional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.presentation.provisional.IntentoVistaProvisional.RespuestaVista;

@DisplayName("DTO provisionales de M5")
class IntentoVistaProvisionalTest {

    private static final String P1 = "6f1c2c1e-2d7b-4c55-9b0b-0d3e1f2a3b4c";
    private static final String O1 = "a1b2c3d4-e5f6-4789-8abc-def012345678";

    @Test
    @DisplayName("la vista de un intento en curso usa el catálogo EnCurso y no tiene motivo")
    void vistaEnCurso() {
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(new ReferenciaPregunta(P1), new ValorRespuesta(O1));

        IntentoVistaProvisional vista =
                IntentoVistaProvisional.desde(ResumenIntento.de(new ReferenciaIntento("I-1"), intento));

        assertEquals("I-1", vista.idIntento());
        assertEquals("EnCurso", vista.estado());
        assertNull(vista.motivoFinalizacion());
        assertEquals(List.of(new RespuestaVista(P1, O1)), vista.respuestas());
    }

    @Test
    @DisplayName("la vista de un intento finalizado usa Finalizado e incluye el motivo")
    void vistaFinalizada() {
        Intento intento = Intento.iniciar();
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        IntentoVistaProvisional vista =
                IntentoVistaProvisional.desde(ResumenIntento.de(new ReferenciaIntento("I-1"), intento));

        assertEquals("Finalizado", vista.estado());
        assertEquals("TIEMPO_VENCIDO", vista.motivoFinalizacion());
    }

    @Test
    @DisplayName("la vista no expone calificación, rúbrica, retroalimentación ni respuestas correctas")
    void noExponeInformacionReservada() {
        Set<String> campos = Arrays.stream(IntentoVistaProvisional.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        Set<String> camposRespuesta = Arrays.stream(RespuestaVista.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());

        assertEquals(Set.of("idIntento", "estado", "motivoFinalizacion", "respuestas"), campos);
        assertEquals(Set.of("idPregunta", "idOpcionSeleccionada"), camposRespuesta);
    }

    @Test
    @DisplayName("la solicitud se convierte a los valores del dominio")
    void solicitudADominio() {
        SolicitudRespuestaProvisional solicitud = new SolicitudRespuestaProvisional(P1, O1);

        assertEquals(new ReferenciaPregunta(P1), solicitud.pregunta());
        assertEquals(new ValorRespuesta(O1), solicitud.valor());
    }

    @Test
    @DisplayName("el patrón uuid acepta el formato canónico y rechaza otros")
    void patronUuid() {
        assertEquals(true, P1.matches(SolicitudRespuestaProvisional.UUID_REGEX));
        assertEquals(false, "P-1".matches(SolicitudRespuestaProvisional.UUID_REGEX));
        assertEquals(false, (P1 + "0").matches(SolicitudRespuestaProvisional.UUID_REGEX));
    }
}
