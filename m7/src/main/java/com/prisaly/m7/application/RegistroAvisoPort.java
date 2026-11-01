package com.prisaly.m7.application;

import com.prisaly.m7.domain.AvisoConvocatoria;

public interface RegistroAvisoPort {
    /** Retorna solo si el aviso existe con destinatario/evento/tipo válidos. Propaga errores. */
    void registrar(AvisoConvocatoria aviso);
}
