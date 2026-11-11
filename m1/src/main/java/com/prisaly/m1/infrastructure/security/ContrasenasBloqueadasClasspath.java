package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.ContrasenasBloqueadasPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * DM1-08: blocklist loaded from {@code seguridad/contrasenas-bloqueadas.txt} (one entry per line; '#' comments).
 * Fails at startup if the resource is missing, so the policy never runs without its list.
 */
@Component
public class ContrasenasBloqueadasClasspath implements ContrasenasBloqueadasPort {
    static final String RECURSO = "seguridad/contrasenas-bloqueadas.txt";
    private final Set<String> lista;
    private final String huella;

    public ContrasenasBloqueadasClasspath() {
        try (InputStream in = ContrasenasBloqueadasClasspath.class.getClassLoader().getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("Password blocklist resource missing: " + RECURSO);
            }
            byte[] bytes = in.readAllBytes();
            this.huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            this.lista = new String(bytes, StandardCharsets.UTF_8).lines().map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean contiene(String normalizada) {
        return lista.contains(normalizada);
    }

    @Override
    public String huellaSha256() {
        return huella;
    }

    @Override
    public int tamano() {
        return lista.size();
    }
}
