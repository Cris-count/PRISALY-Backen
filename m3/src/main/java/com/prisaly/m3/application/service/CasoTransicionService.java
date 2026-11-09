package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** State transitions with their own permissions; records who executed each one and when. */
public final class CasoTransicionService {
    public static final String LOGGER_AUDITORIA = "prisaly.auditoria.casos";
    private static final Logger auditoria = LoggerFactory.getLogger(LOGGER_AUDITORIA);

    private final CasoCurrentUserPort identity;
    private final CaseAuthorizationPort authorization;
    private final CasoPublicationPort publication;

    public CasoTransicionService(CasoCurrentUserPort identity, CaseAuthorizationPort authorization,
            CasoPublicationPort publication) {
        this.identity = identity;
        this.authorization = authorization;
        this.publication = publication;
    }

    public CasoUpdateResult publicar(UUID caso, Supplier<String> fingerprint) {
        return ejecutar("PUBLICAR", caso, fingerprint,
                authorization == null ? (u, c) -> false : authorization::canPublishCase,
                (usuario, esperado) -> publication.publish(usuario, caso, esperado));
    }

    public CasoUpdateResult archivar(UUID caso, Supplier<String> fingerprint) {
        return ejecutar("ARCHIVAR", caso, fingerprint,
                authorization == null ? (u, c) -> false : authorization::canArchiveCase,
                (usuario, esperado) -> publication.archive(usuario, caso, esperado));
    }

    @FunctionalInterface
    private interface Transicion { CasoUpdateResult aplicar(String usuario, String esperado); }

    private CasoUpdateResult ejecutar(String accion, UUID caso, Supplier<String> fingerprint,
            BiPredicate<String, String> permitido, Transicion transicion) {
        Objects.requireNonNull(caso, "caso");
        UUID user = VerificacionCaso.usuarioActual(identity);
        try {
            VerificacionCaso.exigir(() -> permitido.test(user.toString(), caso.toString()), "Case transition denied");
            if (publication == null) { throw VerificacionCaso.noDisponible(); }
            CasoUpdateResult resultado = transicion.aplicar(user.toString(), fingerprint.get());
            auditar(accion, caso, user, "EXITOSO");
            return resultado;
        } catch (CasoDomainException rechazo) {
            auditar(accion, caso, user, "RECHAZADO:" + rechazo.code());
            throw rechazo;
        } catch (RuntimeException fallo) {
            auditar(accion, caso, user, "FALLO");
            throw fallo;
        }
    }

    private static void auditar(String accion, UUID caso, UUID usuario, String resultado) {
        auditoria.info("AUDITORIA_CASO accion={} caso={} usuario={} fecha={} resultado={}",
                accion, caso, usuario, Instant.now(), resultado);
    }
}
