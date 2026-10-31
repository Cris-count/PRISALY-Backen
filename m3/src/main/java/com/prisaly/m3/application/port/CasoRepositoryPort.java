package com.prisaly.m3.application.port;

import com.prisaly.m3.domain.model.Caso;
import java.util.Optional;

public interface CasoRepositoryPort {

    Optional<Caso> findById(String caseId);

    void insert(Caso caso);

    void update(Caso caso);
}
