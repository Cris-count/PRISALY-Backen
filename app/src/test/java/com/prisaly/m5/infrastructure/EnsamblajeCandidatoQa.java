package com.prisaly.m5.infrastructure;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.infrastructure.contenido.ConsultaContenidoIntentoTransaccional;
import com.prisaly.m5.infrastructure.programacion.CoordinacionInicioDesdeM4;
import com.prisaly.m5.infrastructure.programacion.ElegibilidadCandidataDesdeM4;
import com.prisaly.m5.infrastructure.programacion.LimiteIndividualCandidatoDesdeM4;

import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>Solo pruebas QA</b> (DC-BE-CIERRE-FUNCIONAL-09, fase 4). Reutiliza el ensamblado real de
 * {@link ConfiguracionParticipacion} sustituyendo únicamente las dos políticas por las <b>candidatas no
 * ratificadas</b> ({@link ElegibilidadCandidataDesdeM4}, {@link LimiteIndividualCandidatoDesdeM4}). Todo lo
 * demás (Proxy, control de titularidad, transacciones, coordinación con M4, contenido M3) es el código productivo.
 * Vive en {@code app/src/test}: no se empaqueta ni puede activarse en producción.
 */
public final class EnsamblajeCandidatoQa {

    private EnsamblajeCandidatoQa() {
    }

    public static AccesoProtegidoParticipacion conPoliticasCandidatas(RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones, PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad, TitularidadIntentos titularidad,
            ProgramacionInicioCoordinadoPort coordinacion, ProgramacionAutenticadaPort autenticada,
            ProgramacionParticipacionPort programacion, ContenidoCasoEstudiantilPort contenido) {
        return ConfiguracionParticipacion.ensamblar(repositorio, participaciones, transacciones, identidad, titularidad,
                new ElegibilidadCandidataDesdeM4(autenticada), new LimiteIndividualCandidatoDesdeM4(autenticada),
                new CoordinacionInicioDesdeM4(coordinacion, identidad),
                new ConsultaContenidoIntentoTransaccional(repositorio, programacion, contenido));
    }
}
