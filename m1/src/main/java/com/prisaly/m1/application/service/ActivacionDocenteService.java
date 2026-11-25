package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.ActivacionDocenteRepositorioPort;
import com.prisaly.m1.application.port.ActivacionDocenteRepositorioPort.RegistroActivacion;
import com.prisaly.m1.application.port.AuditoriaActivacionDocentePort;
import com.prisaly.m1.application.port.AuditoriaActivacionDocentePort.Evento;
import com.prisaly.m1.application.port.ContrasenasBloqueadasPort;
import com.prisaly.m1.application.port.EntregaActivacionDocentePort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaContrasena;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * D-05 CANDIDATE (DC-BE-CIERRE-FUNCIONAL-NOCTURNO-67): secure first credential for a teacher created by
 * {@code POST /v1/admin/docentes} (which stores no password). DC-BE-D05-AUTORIZACION-QA-77 (QA only): assembled by
 * {@code ActivacionDocenteConfig} behind {@code prisaly.m1.activacion-docente.enabled} (default OFF: no bean, no route)
 * with the candidate table of {@code db/d05-propuesta}. The validity period has no default: it must be supplied
 * explicitly. Acceptance of the schema and publication in develop remain pending Dirección Central review.
 *
 * <ul>
 *   <li><b>Issue</b> ({@link #emitir}): only for an existing, active, non-student account <b>without</b> credential.
 *       Revokes previous unconsumed tokens, generates 32 bytes from {@link SecureRandom} (Base64url), stores only the
 *       SHA-256 hash with its expiry, hands the token to {@link EntregaActivacionDocentePort} and audits. The caller
 *       (future admin endpoint) must have verified the administrator actor, as {@code AdministracionIdentidadService}
 *       does.</li>
 *   <li><b>Activate</b> ({@link #activar}): locks the token row; unknown, expired, consumed or revoked tokens, inactive
 *       accounts and accounts that already have a credential are rejected with the <b>same</b>
 *       {@link #CODE_ACTIVACION_INVALIDA} (no enumeration; the reason is only audited). The password must satisfy
 *       DM1-08 ({@link PoliticaContrasena}) and is stored with the existing {@link PasswordEncoder}; an invalid password
 *       leaves the token unconsumed. Consumption and password update must share the caller's write transaction.</li>
 * </ul>
 * The token and the password are never logged, returned or put in exception messages.
 */
public final class ActivacionDocenteService {
    public static final String CODE_ACTIVACION_INVALIDA = "ACTIVATION_INVALID";
    public static final String CODE_ACTIVACION_NO_PERMITIDA = "ACTIVATION_NOT_ALLOWED";
    public static final int BYTES_TOKEN = 32;
    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final UsuarioRepositorioPort usuarios;
    private final ActivacionDocenteRepositorioPort activaciones;
    private final EntregaActivacionDocentePort entrega;
    private final AuditoriaActivacionDocentePort auditoria;
    private final ContrasenasBloqueadasPort bloqueadas;
    private final PasswordEncoder codificador;
    private final Clock reloj;
    private final Duration vigencia;
    private final SecureRandom aleatorio;

    public ActivacionDocenteService(UsuarioRepositorioPort usuarios, ActivacionDocenteRepositorioPort activaciones,
            EntregaActivacionDocentePort entrega, AuditoriaActivacionDocentePort auditoria,
            ContrasenasBloqueadasPort bloqueadas, PasswordEncoder codificador, Clock reloj, Duration vigencia,
            SecureRandom aleatorio) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.activaciones = Objects.requireNonNull(activaciones, "activaciones");
        this.entrega = Objects.requireNonNull(entrega, "entrega");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.bloqueadas = Objects.requireNonNull(bloqueadas, "bloqueadas");
        this.codificador = Objects.requireNonNull(codificador, "codificador");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.aleatorio = Objects.requireNonNull(aleatorio, "aleatorio");
        Objects.requireNonNull(vigencia, "vigencia (no default: pending Dirección Central)");
        if (vigencia.isZero() || vigencia.isNegative()) {
            throw new IllegalArgumentException("The activation validity must be positive");
        }
        this.vigencia = vigencia;
    }

    /** Issues a new activation token for {@code idUsuario}; {@code actor} is the verified administrator. */
    public void emitir(UUID idUsuario, UUID actor) {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(actor, "actor");
        Usuario usuario = usuarios.buscarPorId(idUsuario).orElseThrow(
                () -> new IdentityDomainException("USER_NOT_FOUND", "User not found"));
        if (!usuario.isActive() || usuario.getPasswordHash() != null || usuarios.esCuentaEstudiante(idUsuario)) {
            throw new IdentityDomainException(CODE_ACTIVACION_NO_PERMITIDA,
                    "Activation only applies to active staff accounts without a credential");
        }
        Instant ahora = reloj.instant();
        if (activaciones.revocarVigentes(idUsuario, ahora) > 0) {
            auditoria.registrar(Evento.REVOCACION, idUsuario, actor, "REEMISION");
        }
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = ahora.plus(vigencia);
        activaciones.registrar(new RegistroActivacion(hash(token), idUsuario, actor, ahora, expira, null, null));
        entrega.entregar(idUsuario, usuario.getCorreo(), token, expira);
        auditoria.registrar(Evento.EMISION, idUsuario, actor, null);
    }

    /**
     * DC-77: revokes every live token of {@code idUsuario} without issuing a new one; {@code actor} is the verified
     * administrator. Returns how many tokens were revoked (0 is not an error: the operation is idempotent).
     */
    public int revocar(UUID idUsuario, UUID actor) {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(actor, "actor");
        if (usuarios.buscarPorId(idUsuario).isEmpty()) {
            throw new IdentityDomainException("USER_NOT_FOUND", "User not found");
        }
        int revocados = activaciones.revocarVigentes(idUsuario, reloj.instant());
        if (revocados > 0) {
            auditoria.registrar(Evento.REVOCACION, idUsuario, actor, "ADMINISTRADOR");
        }
        return revocados;
    }

    /** Consumes {@code token} and sets the first password. Every invalid-token case fails identically. */
    public void activar(String token, String contrasena) {
        if (token == null || !FORMATO.matcher(token).matches()) {
            throw rechazo(null, "FORMATO");
        }
        String hash = hash(token);
        Instant ahora = reloj.instant();
        RegistroActivacion registro = activaciones.bloquearPorHash(hash).orElse(null);
        if (registro == null) { throw rechazo(null, "INEXISTENTE"); }
        if (registro.consumidoEn() != null) { throw rechazo(registro.idUsuario(), "CONSUMIDO"); }
        if (registro.revocadoEn() != null) { throw rechazo(registro.idUsuario(), "REVOCADO"); }
        if (!ahora.isBefore(registro.expiraEn())) { throw rechazo(registro.idUsuario(), "CADUCADO"); }
        Usuario usuario = usuarios.buscarPorId(registro.idUsuario()).orElse(null);
        if (usuario == null || !usuario.isActive()) { throw rechazo(registro.idUsuario(), "CUENTA_NO_ACTIVA"); }
        if (usuario.getPasswordHash() != null) { throw rechazo(registro.idUsuario(), "YA_TIENE_CREDENCIAL"); }
        // DM1-08 before consuming: an invalid password keeps the token usable.
        PoliticaContrasena.validar(contrasena, usuario.getCorreo(), bloqueadas::contiene);
        if (!activaciones.marcarConsumido(hash, ahora)) { throw rechazo(registro.idUsuario(), "CONSUMIDO"); }
        usuarios.actualizarPasswordHash(registro.idUsuario(), codificador.encode(contrasena),
                ahora.atOffset(ZoneOffset.UTC));
        auditoria.registrar(Evento.CONSUMO, registro.idUsuario(), registro.idUsuario(), null);
    }

    /** SHA-256 (hex) of the token: the only form ever persisted or compared. */
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 unavailable", imposible);
        }
    }

    private IdentityDomainException rechazo(UUID idUsuario, String motivo) {
        auditoria.registrar(Evento.RECHAZO, idUsuario, null, motivo);
        return new IdentityDomainException(CODE_ACTIVACION_INVALIDA, "Invalid or expired activation");
    }
}
