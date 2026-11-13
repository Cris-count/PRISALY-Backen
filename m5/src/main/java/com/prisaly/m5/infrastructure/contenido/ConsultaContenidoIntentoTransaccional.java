package com.prisaly.m5.infrastructure.contenido;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.ContenidoIntentoNoDisponibleException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.PersistenciaIntentoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.port.ConsultaContenidoIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import java.util.UUID;

/** La titularidad, el vínculo M5, la programación M4 y la proyección M3 comparten snapshot.
 * No es un bean: el proxy y ConfiguracionParticipacion controlan su entrada transaccional.
 * No evalúa políticas de inicio/finalización ni expone respuestas correctas o evaluación M6.
 */
public class ConsultaContenidoIntentoTransaccional implements ConsultaContenidoIntento {
    private final RepositorioIntentos intentos;
    private final ProgramacionParticipacionPort programacion;
    private final ContenidoCasoEstudiantilPort contenido;

    public ConsultaContenidoIntentoTransaccional(RepositorioIntentos intentos,
            ProgramacionParticipacionPort programacion, ContenidoCasoEstudiantilPort contenido) {
        this.intentos = intentos;
        this.programacion = programacion;
        this.contenido = contenido;
    }

    @Override
    public ContenidoIntento consultar(ReferenciaIntento intento, IdentificadorEstudiante identidadAcreditada) {
        var estudiante = UUID.fromString(identidadAcreditada.valor());
        var vinculo = intentos.vinculo(intento).orElseThrow(() -> new IntentoNoEncontradoException(intento));
        var contexto = programacion.consultarConvocatoria(vinculo.idConvocatoria(), estudiante)
                .orElseThrow(ContenidoIntentoNoDisponibleException::new);
        if (!contexto.convocatoriaId().equals(vinculo.idConvocatoria()) || !contexto.estudianteId().equals(estudiante)) {
            throw new PersistenciaIntentoException("Contexto de programación inconsistente");
        }
        if (!contexto.casoPublicadoYActivo()) throw new ContenidoIntentoNoDisponibleException();
        var caso = contenido.consultar(contexto.casoId()).orElseThrow(ContenidoIntentoNoDisponibleException::new);
        if (!caso.idCaso().equals(contexto.casoId())) throw new PersistenciaIntentoException("Contenido de caso inconsistente");
        return new ContenidoIntento(UUID.fromString(intento.valor()), contexto.sesionId(), caso);
    }
}
