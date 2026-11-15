package com.prisaly.m3.domain.narrativa;

import java.util.List;
import java.util.Objects;

/** El documento no cumple el formato {@value GuionNarrativo#FORMATO}. Lleva todos los errores encontrados. */
public final class GuionNarrativoInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "GUION_INVALIDO";

    /** {@code ruta} en notación JSON-pointer simplificada (p. ej. {@code espacios[2].titulo}). */
    public record Error(String ruta, String motivo) {
        public Error {
            Objects.requireNonNull(ruta, "ruta");
            Objects.requireNonNull(motivo, "motivo");
        }
    }

    private final transient List<Error> errores;

    public GuionNarrativoInvalidoException(List<Error> errores) {
        super("El guion narrativo no es válido");
        this.errores = List.copyOf(errores);
    }

    public List<Error> errores() {
        return errores;
    }
}
