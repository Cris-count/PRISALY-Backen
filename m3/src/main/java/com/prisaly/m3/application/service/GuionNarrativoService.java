package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio.CasoNarrativa;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio.GuionGuardado;
import com.prisaly.m3.application.port.UnidadTransaccionalNarrativa;
import com.prisaly.m3.application.update.JsonEstricto;
import com.prisaly.m3.domain.narrativa.GuionNarrativoInvalidoException;
import com.prisaly.m3.domain.narrativa.ValidadorGuionNarrativo;
import com.prisaly.m3.domain.rule.CasoDomainException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Autoría docente del guion narrativo (DC-BE-SIM-INCREMENTO-A-15, candidato técnico, no ampliación aprobada).
 *
 * <p>Orden de evaluación, como {@code CasoContenidoService}: identidad (M1) → permiso → existencia del caso → autoría
 * ({@code Caso.idDocente}) → bloqueo académico / estado → cuerpo. Todo ocurre en una transacción con el caso bloqueado
 * ({@code FOR UPDATE}), así que no puede intercalarse una programación de sesión.
 *
 * <ul>
 *   <li>Consultar y guardar el borrador: {@code canModifyCase} (MODIFICAR_CASO + CONSULTAR_CASO) — propuesta D-SIM-8.</li>
 *   <li>Publicar: {@code canPublishCase} (PUBLICAR_CASO) — propuesta D-SIM-8.</li>
 *   <li>Caso programado ({@code fechaBloqueoAcademico}): {@code CONTENIDO_BLOQUEADO}; la base lo garantiza además con
 *       {@code tr_guion_bloqueo_sim}.</li>
 *   <li>Publicar exige el caso {@code Publicado} y activo — propuesta D-SIM-11 ({@code CASO_NO_PUBLICABLE}).</li>
 * </ul>
 */
public final class GuionNarrativoService {
    public static final String CODE_CONTENIDO_BLOQUEADO = "CONTENIDO_BLOQUEADO";
    public static final String CODE_CASO_NO_PUBLICABLE = "CASO_NO_PUBLICABLE";
    public static final String CODE_SIN_BORRADOR = "SIN_BORRADOR";
    public static final String CODE_CASO_NO_EDITABLE = "CASE_NOT_EDITABLE";
    static final String ESTADO_PUBLICADO = "Publicado", ESTADO_ARCHIVADO = "Archivado";
    private static final String DENEGADO = "Narrative script operation denied";

    private final CasoCurrentUserPort identidad;
    private final CaseAuthorizationPort autorizacion;
    private final GuionNarrativoRepositorio guiones;
    private final UnidadTransaccionalNarrativa transaccion;
    private final ValidadorGuionNarrativo validador;

    public GuionNarrativoService(CasoCurrentUserPort identidad, CaseAuthorizationPort autorizacion,
            GuionNarrativoRepositorio guiones, UnidadTransaccionalNarrativa transaccion, ValidadorGuionNarrativo validador) {
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.guiones = Objects.requireNonNull(guiones, "guiones");
        this.transaccion = Objects.requireNonNull(transaccion, "transaccion");
        this.validador = Objects.requireNonNull(validador, "validador");
    }

    /** Vista docente: borrador y publicado vigentes, si existen. */
    public record VistaDocente(UUID idCaso, Optional<GuionGuardado> borrador, Optional<GuionGuardado> publicado) { }

    public VistaDocente consultar(UUID idCaso) {
        UUID usuario = autorizar(idCaso, false);
        return transaccion.ejecutar(() -> {
            propio(idCaso, usuario);
            return new VistaDocente(idCaso, guiones.borrador(idCaso), guiones.publicadoVigente(idCaso));
        });
    }

    public GuionGuardado guardarBorrador(UUID idCaso, String documentoJson) {
        UUID usuario = autorizar(idCaso, false);
        return transaccion.ejecutar(() -> {
            CasoNarrativa caso = propio(idCaso, usuario);
            if (caso.bloqueado()) { throw new CasoDomainException(CODE_CONTENIDO_BLOQUEADO, "The case is programmed"); }
            if (ESTADO_ARCHIVADO.equals(caso.estado())) {
                throw new CasoDomainException(CODE_CASO_NO_EDITABLE, "Archived case");
            }
            validar(documentoJson);
            return guiones.guardarBorrador(idCaso, usuario, documentoJson);
        });
    }

    public GuionGuardado publicar(UUID idCaso) {
        UUID usuario = autorizar(idCaso, true);
        return transaccion.ejecutar(() -> {
            CasoNarrativa caso = propio(idCaso, usuario);
            if (caso.bloqueado()) { throw new CasoDomainException(CODE_CONTENIDO_BLOQUEADO, "The case is programmed"); }
            if (!ESTADO_PUBLICADO.equals(caso.estado()) || !caso.activo()) {
                throw new CasoDomainException(CODE_CASO_NO_PUBLICABLE, "The case must be published and active");
            }
            if (guiones.borrador(idCaso).isEmpty()) {
                throw new CasoDomainException(CODE_SIN_BORRADOR, "There is no draft to publish");
            }
            return guiones.publicarBorrador(idCaso);
        });
    }

    private UUID autorizar(UUID idCaso, boolean publicar) {
        Objects.requireNonNull(idCaso, "idCaso");
        UUID usuario = VerificacionCaso.usuarioActual(identidad);
        VerificacionCaso.exigir(() -> autorizacion != null && (publicar
                ? autorizacion.canPublishCase(usuario.toString(), idCaso.toString())
                : autorizacion.canModifyCase(usuario.toString(), idCaso.toString())), DENEGADO);
        return usuario;
    }

    private CasoNarrativa propio(UUID idCaso, UUID usuario) {
        CasoNarrativa caso = guiones.bloquearCaso(idCaso)
                .orElseThrow(() -> new CasoDomainException("CASE_NOT_FOUND", "Case does not exist"));
        if (!usuario.equals(caso.idDocente())) { throw VerificacionCaso.denegado(DENEGADO); }
        return caso;
    }

    private void validar(String documentoJson) {
        if (documentoJson == null) {
            throw new GuionNarrativoInvalidoException(List.of(new GuionNarrativoInvalidoException.Error("", "DOCUMENTO_REQUERIDO")));
        }
        int bytes = documentoJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > ValidadorGuionNarrativo.MAX_BYTES) {
            // Rejected before parsing: an oversized body never reaches the JSON parser.
            throw new GuionNarrativoInvalidoException(List.of(new GuionNarrativoInvalidoException.Error("", "DOCUMENTO_DEMASIADO_GRANDE")));
        }
        Map<String, Object> documento;
        try {
            documento = JsonEstricto.objeto(documentoJson);
        } catch (IllegalArgumentException invalido) {
            throw new GuionNarrativoInvalidoException(List.of(new GuionNarrativoInvalidoException.Error("", "JSON_INVALIDO")));
        }
        validador.validar(documento, bytes);
    }
}
