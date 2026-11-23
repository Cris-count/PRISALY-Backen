package com.prisaly.config.m6;

import com.prisaly.config.MapeoContextoAcademicoM3;
import com.prisaly.m3.api.AccesoContextoAcademicoDenegadoException;
import com.prisaly.m3.api.ContextoAcademicoConsultaPort;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * M6 consumer adapter over M3's protected producer: maps the snapshot with {@link MapeoContextoAcademicoM3} and
 * translates M3 denial into M6 denial without its cause (it may carry the capability). Technical failures propagate.
 */
final class ContextoAcademicoM3ParaM6 implements ConsultaContextoAcademicoPort {
    private final ContextoAcademicoConsultaPort m3;

    ContextoAcademicoM3ParaM6(ContextoAcademicoConsultaPort m3) {
        this.m3 = Objects.requireNonNull(m3, "m3");
    }

    @Override
    public Optional<ContextoAcademico> findByCasoId(UUID idCaso, String capacidad) {
        try {
            return m3.consultar(idCaso, capacidad).map(MapeoContextoAcademicoM3::aContextoAcademico);
        } catch (AccesoContextoAcademicoDenegadoException denegado) {
            throw new AccesoEvaluacionDenegadoException();
        }
    }
}
