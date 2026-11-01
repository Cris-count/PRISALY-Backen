package com.prisaly.m4.infrastructure.adapter;

import java.util.Objects;
import java.util.UUID;

import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

/**
 * Adaptación al contrato público M3. No importa entidades ni repositorios JPA M3.
 * No escribe una marca F3-03 manualmente. El contrato disponible no confirma
 * atomicidad, actividad del caso ni persistencia del bloqueo por programación;
 * por ello no puede completar F3-03 ni convertir un booleano del agregado en
 * prueba de que se programó una sesión en las tablas oficiales.
 */
public final class CasoAcademicoM3Adapter implements CasoAcademicoPort {
    private final CasoRepositoryPort casos;

    public CasoAcademicoM3Adapter(CasoRepositoryPort casos) {
        this.casos = Objects.requireNonNull(casos);
    }

    @Override
    public boolean estaPublicado(UUID casoId) {
        return casos.findById(Objects.requireNonNull(casoId).toString())
                .filter(caso -> corresponde(caso, casoId)).map(Caso::isPublished).orElse(false);
    }

    @Override
    public void solicitarBloqueoAcademico(UUID casoId) {
        Objects.requireNonNull(casoId);
        throw noConfirmado();
    }

    private boolean corresponde(Caso caso, UUID id) {
        try {
            return id.equals(UUID.fromString(caso.id()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private ReglaNegocioException noConfirmado() {
        return new ReglaNegocioException("M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO",
                "El contrato M3 disponible no confirma el bloqueo académico atómico de la programación");
    }
}
