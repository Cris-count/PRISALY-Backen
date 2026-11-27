package com.prisaly.m5.application.investigacion;

import static com.prisaly.m5.application.investigacion.InvestigacionA1RechazadaException.Codigo.*;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * CB-02B (A1): reglas puras de las notas del estudiante. Concurrencia optimista con {@code revisionEsperada}
 * ({@code 0} crea), borrado lógico e idempotente. El texto es del estudiante y no se interpreta.
 */
public final class RegistroNotasA1 {

    public static final int MAX_TEXTO = 4000;

    public record Nota(UUID idNota, String texto, long revision, boolean eliminada, Instant fechaActualizacion) {
        public Nota {
            Objects.requireNonNull(idNota, "idNota");
            Objects.requireNonNull(texto, "texto");
            if (revision < 1) {
                throw new IllegalArgumentException("revision");
            }
        }
    }

    private RegistroNotasA1() { }

    public static Nota guardar(Optional<Nota> actual, UUID idNota, String texto, long revisionEsperada, Instant ahora) {
        Objects.requireNonNull(idNota, "idNota");
        validarTexto(texto);
        if (actual.isEmpty()) {
            if (revisionEsperada != 0) {
                throw new InvestigacionA1RechazadaException(REVISION_OBSOLETA, "la nota no existe; para crearla la revisión esperada es 0");
            }
            return new Nota(idNota, texto, 1, false, ahora);
        }
        Nota n = actual.get();
        if (n.eliminada()) {
            throw new InvestigacionA1RechazadaException(NOTA_ELIMINADA, idNota.toString());
        }
        if (n.revision() != revisionEsperada) {
            throw new InvestigacionA1RechazadaException(REVISION_OBSOLETA, "esperada " + revisionEsperada + ", actual " + n.revision());
        }
        return new Nota(idNota, texto, n.revision() + 1, false, ahora);
    }

    /** Vacío si ya estaba eliminada (DELETE repetido = sin cambios). */
    public static Optional<Nota> eliminar(Optional<Nota> actual, UUID idNota, long revisionEsperada, Instant ahora) {
        Nota n = actual.orElseThrow(() -> new InvestigacionA1RechazadaException(NOTA_INEXISTENTE, String.valueOf(idNota)));
        if (n.eliminada()) {
            return Optional.empty();
        }
        if (n.revision() != revisionEsperada) {
            throw new InvestigacionA1RechazadaException(REVISION_OBSOLETA, "esperada " + revisionEsperada + ", actual " + n.revision());
        }
        return Optional.of(new Nota(idNota, n.texto(), n.revision() + 1, true, ahora));
    }

    private static void validarTexto(String texto) {
        if (texto == null || texto.isBlank() || texto.codePointCount(0, texto.length()) > MAX_TEXTO
                || texto.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r')) {
            throw new InvestigacionA1RechazadaException(TEXTO_INVALIDO, "1-" + MAX_TEXTO + " caracteres, sin caracteres de control");
        }
    }
}
