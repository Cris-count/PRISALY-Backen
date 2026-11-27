package com.prisaly.m5.application.investigacion;

import static com.prisaly.m5.application.investigacion.InvestigacionA1RechazadaException.Codigo.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * CB-02B (A1): reglas puras de los eventos de investigación. No persiste ni consulta nada: recibe el guion fijado y los
 * eventos ya guardados del intento (leídos con el intento bloqueado) y decide si la solicitud crea un evento, es un
 * reenvío o se rechaza. Unreal nunca decide qué información académica se obtuvo: el cliente solo propone y aquí se
 * valida contra el guion fijado.
 *
 * <p>Idempotencia (A1 §Idempotencia): misma clave y misma huella → reenvío sin duplicar; misma clave con otra huella →
 * {@code CLAVE_REUTILIZADA}; otra clave con el mismo {@code (tipo, referencia)} → el evento existente. La secuencia la
 * asigna el servidor, sin huecos por intento.
 */
public final class RegistroEventosA1 {

    public static final int MAX_EVENTOS_POR_INTENTO = 2000;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    public enum Tipo {
        ESPACIO_VISITADO("espacio.visitado"), INFORMACION_OBTENIDA("informacion.obtenida"),
        INTERVENCION_COMPLETADA("intervencion.completada"), MARCA_FIJADA("marca.fijada");

        private final String codigo;

        Tipo(String codigo) {
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }

        static Tipo de(String codigo) {
            for (Tipo t : values()) {
                if (t.codigo.equals(codigo)) {
                    return t;
                }
            }
            throw new InvestigacionA1RechazadaException(TIPO_EVENTO_INVALIDO, String.valueOf(codigo));
        }
    }

    /** Lo que propone el cliente. {@code hashDefinicion} es la huella del guion con el que trabaja el cliente. */
    public record Solicitud(UUID idEvento, String tipo, String referencia, Boolean valor, String hashDefinicion) {
        public Solicitud {
            Objects.requireNonNull(idEvento, "idEvento");
        }
    }

    public record Evento(UUID idEvento, Tipo tipo, String referencia, Boolean valor, String huella, long secuencia, Instant fechaServidor) {
        public Evento {
            Objects.requireNonNull(idEvento, "idEvento");
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(referencia, "referencia");
            Objects.requireNonNull(huella, "huella");
            if (secuencia < 1) {
                throw new IllegalArgumentException("secuencia");
            }
        }
    }

    /** {@code nuevo=false}: reenvío o evento equivalente ya registrado (sin escritura). */
    public record Resultado(Evento evento, boolean nuevo) { }

    private RegistroEventosA1() { }

    public static Resultado registrar(GuionFijado guion, List<Evento> existentes, Solicitud s, Instant ahora) {
        Objects.requireNonNull(guion, "guion");
        Objects.requireNonNull(existentes, "existentes");
        Objects.requireNonNull(s, "solicitud");
        Tipo tipo = Tipo.de(s.tipo());
        if (!guion.hashDefinicion().equals(s.hashDefinicion())) {
            throw new InvestigacionA1RechazadaException(GUION_INCOMPATIBLE, "la narrativa del cliente no es la fijada en el intento");
        }
        if (s.referencia() == null || !ID.matcher(s.referencia()).matches()) {
            throw new InvestigacionA1RechazadaException(REFERENCIA_INVALIDA, String.valueOf(s.referencia()));
        }
        Boolean valor = tipo == Tipo.MARCA_FIJADA ? s.valor() : null;
        if (tipo == Tipo.MARCA_FIJADA && valor == null) {
            throw new InvestigacionA1RechazadaException(VALOR_REQUERIDO, s.referencia());
        }
        String huella = huella(tipo, s.referencia(), valor);

        Optional<Evento> mismaClave = existentes.stream().filter(e -> e.idEvento().equals(s.idEvento())).findFirst();
        if (mismaClave.isPresent()) {
            if (!mismaClave.get().huella().equals(huella)) {
                throw new InvestigacionA1RechazadaException(CLAVE_REUTILIZADA, s.idEvento().toString());
            }
            return new Resultado(mismaClave.get(), false);
        }
        validarContraGuion(guion, existentes, tipo, s.referencia());
        Optional<Evento> equivalente = existentes.stream()
                .filter(e -> e.tipo() == tipo && e.referencia().equals(s.referencia())).findFirst();
        if (equivalente.isPresent()) {
            if (tipo == Tipo.MARCA_FIJADA && !equivalente.get().valor().equals(valor)) {
                throw new InvestigacionA1RechazadaException(MARCA_CONFLICTIVA, s.referencia());
            }
            return new Resultado(equivalente.get(), false);
        }
        if (existentes.size() >= MAX_EVENTOS_POR_INTENTO) {
            throw new InvestigacionA1RechazadaException(LIMITE_EVENTOS, String.valueOf(MAX_EVENTOS_POR_INTENTO));
        }
        long secuencia = existentes.stream().mapToLong(Evento::secuencia).max().orElse(0) + 1;
        return new Resultado(new Evento(s.idEvento(), tipo, s.referencia(), valor, huella, secuencia, ahora), true);
    }

    private static void validarContraGuion(GuionFijado guion, List<Evento> existentes, Tipo tipo, String referencia) {
        switch (tipo) {
            case ESPACIO_VISITADO -> {
                if (!guion.espacios().contains(referencia)) {
                    throw new InvestigacionA1RechazadaException(ESPACIO_NO_HABILITADO, referencia);
                }
            }
            case INFORMACION_OBTENIDA -> {
                String espacio = guion.espacioDeInformacion().get(referencia);
                if (espacio == null) {
                    throw new InvestigacionA1RechazadaException(INFORMACION_INEXISTENTE, referencia);
                }
                if (!visitado(existentes, espacio)) {
                    throw new InvestigacionA1RechazadaException(ESPACIO_NO_VISITADO, espacio);
                }
            }
            case INTERVENCION_COMPLETADA -> {
                GuionFijado.Intervencion it = guion.intervenciones().get(referencia);
                if (it == null) {
                    throw new InvestigacionA1RechazadaException(INTERVENCION_INEXISTENTE, referencia);
                }
                if (!visitado(existentes, it.idEspacio())) {
                    throw new InvestigacionA1RechazadaException(INTERVENCION_NO_AUTORIZADA, referencia + " sin visitar " + it.idEspacio());
                }
            }
            case MARCA_FIJADA -> {
                if (!guion.marcas().contains(referencia)) {
                    throw new InvestigacionA1RechazadaException(MARCA_INEXISTENTE, referencia);
                }
            }
        }
    }

    private static boolean visitado(List<Evento> existentes, String idEspacio) {
        return existentes.stream().anyMatch(e -> e.tipo() == Tipo.ESPACIO_VISITADO && e.referencia().equals(idEspacio));
    }

    /** SHA-256 de {@code tipo \n referencia \n valor}: identifica el contenido de un evento para la idempotencia. */
    static String huella(Tipo tipo, String referencia, Boolean valor) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(
                    (tipo.codigo() + "\n" + referencia + "\n" + (valor == null ? "" : valor)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
