package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.ActivacionDocenteRepositorioPort;
import com.prisaly.m1.application.port.ActivacionDocenteRepositorioPort.RegistroActivacion;
import com.prisaly.m1.application.port.AuditoriaActivacionDocentePort.Evento;
import com.prisaly.m1.application.port.ContrasenasBloqueadasPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.application.service.ActivacionDocenteService;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import java.lang.annotation.Annotation;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** D-05 candidate (DC-67): token lifecycle, anti-enumeration, concurrency and audit, with in-memory ports. */
class ActivacionDocenteServiceTest {
    static final String CLAVE = "Activacion#Docente-2026";
    final UUID docente = UUID.randomUUID(), admin = UUID.randomUUID();
    final Reloj reloj = new Reloj();
    final Map<UUID, Usuario> cuentas = new HashMap<>();
    final Memoria activaciones = new Memoria();
    final List<String> entregados = new ArrayList<>();
    final List<String> auditoria = new ArrayList<>();
    final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    ActivacionDocenteService servicio;

    static final class Reloj extends Clock {
        volatile Instant ahora = Instant.parse("2026-11-23T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    /** In-memory port; {@code bloquearPorHash} + {@code marcarConsumido} emulate the row lock atomically. */
    static final class Memoria implements ActivacionDocenteRepositorioPort {
        final Map<String, RegistroActivacion> filas = new HashMap<>();
        @Override public synchronized void registrar(RegistroActivacion r) { filas.put(r.hashToken(), r); }
        @Override public synchronized int revocarVigentes(UUID id, Instant t) {
            int n = 0;
            for (var e : filas.entrySet()) {
                var r = e.getValue();
                if (r.idUsuario().equals(id) && r.consumidoEn() == null && r.revocadoEn() == null) {
                    e.setValue(new RegistroActivacion(r.hashToken(), id, r.emisor(), r.emitidoEn(), r.expiraEn(), null, t));
                    n++;
                }
            }
            return n;
        }
        @Override public synchronized Optional<RegistroActivacion> bloquearPorHash(String h) {
            return Optional.ofNullable(filas.get(h));
        }
        @Override public synchronized boolean marcarConsumido(String h, Instant t) {
            var r = filas.get(h);
            if (r == null || r.consumidoEn() != null || r.revocadoEn() != null) { return false; }
            filas.put(h, new RegistroActivacion(h, r.idUsuario(), r.emisor(), r.emitidoEn(), r.expiraEn(), t, null));
            return true;
        }
    }

    private UsuarioRepositorioPort usuarios() {
        return new UsuarioRepositorioPort() {
            @Override public Optional<Usuario> buscarPorId(UUID id) { return Optional.ofNullable(cuentas.get(id)); }
            @Override public Optional<Usuario> buscarPorCorreo(String c) { return Optional.empty(); }
            @Override public boolean existePorId(UUID id) { return cuentas.containsKey(id); }
            @Override public boolean existePorCorreo(String c) { return false; }
            @Override public Usuario registrar(Usuario u) { cuentas.put(u.getIdUsuario(), u); return u; }
            @Override public void cambiarEstado(UUID id, String e, OffsetDateTime f) { cuenta(id, cuentas.get(id).getPasswordHash(), e); }
            @Override public boolean esCuentaEstudiante(UUID id) { return false; }
            @Override public synchronized void actualizarPasswordHash(UUID id, String h, OffsetDateTime f) {
                cuenta(id, h, cuentas.get(id).getEstado());
            }
        };
    }

    private void cuenta(UUID id, String hash, String estado) {
        cuentas.put(id, new Usuario(id, new Rol(2, "DOCENTE", null), "Docente", "docente.d05@prisaly.test", hash, estado,
                OffsetDateTime.now(), null));
    }

    @BeforeEach
    void preparar() {
        cuenta(docente, null, "Activo");
        ContrasenasBloqueadasPort bloqueadas = new ContrasenasBloqueadasPort() {
            @Override public boolean contiene(String n) { return n.equals("password1234"); }
            @Override public String huellaSha256() { return "x"; }
            @Override public int tamano() { return 1; }
        };
        servicio = new ActivacionDocenteService(usuarios(), activaciones,
                (id, correo, token, expira) -> entregados.add(token),
                (evento, id, actor, motivo) -> auditoria.add(evento + ":" + motivo),
                bloqueadas, bcrypt, reloj, Duration.ofHours(24), new SecureRandom());
    }

    private String emitir() {
        servicio.emitir(docente, admin);
        return entregados.get(entregados.size() - 1);
    }

    private void invalida(String token, String clave) {
        var e = assertThrows(IdentityDomainException.class, () -> servicio.activar(token, clave));
        assertEquals(ActivacionDocenteService.CODE_ACTIVACION_INVALIDA, e.code());
        assertFalse(e.getMessage().contains(String.valueOf(token)), "the token never appears in errors");
    }

    @Test
    void emiteTokenAleatorioYSoloPersisteSuHash() {
        String t1 = emitir();
        assertEquals(43, t1.length(), "32 bytes Base64url without padding");
        var fila = activaciones.filas.values().iterator().next();
        assertEquals(ActivacionDocenteService.hash(t1), fila.hashToken());
        assertNotEquals(t1, fila.hashToken());
        assertEquals(reloj.ahora.plus(Duration.ofHours(24)), fila.expiraEn());
        assertFalse(activaciones.filas.toString().contains(t1), "clear token is never stored");
        assertTrue(auditoria.contains("EMISION:null"));
        assertFalse(auditoria.toString().contains(t1), "clear token is never audited");
    }

    @Test
    void activaUnaVezConBcryptYElTokenNoSeReutiliza() {
        String t = emitir();
        servicio.activar(t, CLAVE);
        String hash = cuentas.get(docente).getPasswordHash();
        assertNotNull(hash);
        assertTrue(bcrypt.matches(CLAVE, hash), "stored with the existing encoder");
        assertTrue(auditoria.contains("CONSUMO:null"));
        invalida(t, "Otra#Clave-Distinta-2026");
        assertTrue(auditoria.contains("RECHAZO:CONSUMIDO"));
        assertEquals(hash, cuentas.get(docente).getPasswordHash(), "reuse does not change the credential");
    }

    @Test
    void tokenCaducadoSeRechazaEnLaFronteraExacta() {
        String t = emitir();
        reloj.ahora = reloj.ahora.plus(Duration.ofHours(24));
        invalida(t, CLAVE);
        assertTrue(auditoria.contains("RECHAZO:CADUCADO"));
        assertNull(cuentas.get(docente).getPasswordHash());
    }

    @Test
    void reemisionRevocaElTokenAnterior() {
        String viejo = emitir();
        String nuevo = emitir();
        assertNotEquals(viejo, nuevo);
        assertTrue(auditoria.contains("REVOCACION:REEMISION"));
        invalida(viejo, CLAVE);
        assertTrue(auditoria.contains("RECHAZO:REVOCADO"));
        servicio.activar(nuevo, CLAVE);
        assertNotNull(cuentas.get(docente).getPasswordHash());
    }

    @Test
    void sinEnumeracionTodosLosRechazosSonIguales() {
        String t = emitir();
        invalida(null, CLAVE);
        invalida("corto", CLAVE);
        invalida("A".repeat(43), CLAVE); // well-formed but unknown
        cuenta(docente, null, "Inactivo");
        invalida(t, CLAVE);
        assertTrue(auditoria.contains("RECHAZO:FORMATO") && auditoria.contains("RECHAZO:INEXISTENTE")
                && auditoria.contains("RECHAZO:CUENTA_NO_ACTIVA"), auditoria.toString());
    }

    @Test
    void contrasenaInvalidaNoConsumeElToken() {
        String t = emitir();
        var corta = assertThrows(IdentityDomainException.class, () -> servicio.activar(t, "corta"));
        assertEquals("PASSWORD_TOO_SHORT", corta.code());
        var comun = assertThrows(IdentityDomainException.class, () -> servicio.activar(t, "PASSWORD1234"));
        assertEquals("PASSWORD_COMMON", comun.code());
        assertNull(cuentas.get(docente).getPasswordHash());
        servicio.activar(t, CLAVE);
        assertNotNull(cuentas.get(docente).getPasswordHash());
    }

    @Test
    void noSeEmiteParaCuentasConCredencialInactivasOInexistentes() {
        cuenta(docente, "hash-existente", "Activo");
        assertEquals(ActivacionDocenteService.CODE_ACTIVACION_NO_PERMITIDA,
                assertThrows(IdentityDomainException.class, () -> servicio.emitir(docente, admin)).code());
        cuenta(docente, null, "Inactivo");
        assertThrows(IdentityDomainException.class, () -> servicio.emitir(docente, admin));
        assertEquals("USER_NOT_FOUND",
                assertThrows(IdentityDomainException.class, () -> servicio.emitir(UUID.randomUUID(), admin)).code());
        assertTrue(entregados.isEmpty());
    }

    @Test
    void activacionesConcurrentesDelMismoTokenConsumenUnaSolaVez() throws Exception {
        String t = emitir();
        var pool = Executors.newFixedThreadPool(8);
        var salida = new CountDownLatch(1);
        var exitos = new AtomicInteger();
        var rechazos = new AtomicInteger();
        try {
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    salida.await();
                    try { servicio.activar(t, CLAVE); exitos.incrementAndGet(); }
                    catch (IdentityDomainException e) {
                        if (ActivacionDocenteService.CODE_ACTIVACION_INVALIDA.equals(e.code())) { rechazos.incrementAndGet(); }
                    }
                    return null;
                });
            }
            salida.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, exitos.get());
        assertEquals(7, rechazos.get());
    }

    @Test
    void vigenciaObligatoriaYSinComponenteSpring() {
        assertThrows(NullPointerException.class, () -> new ActivacionDocenteService(usuarios(), activaciones,
                (a, b, c, d) -> { }, (a, b, c, d) -> { }, null, bcrypt, reloj, null, new SecureRandom()));
        assertThrows(IllegalArgumentException.class, () -> new ActivacionDocenteService(usuarios(), activaciones,
                (a, b, c, d) -> { }, (a, b, c, d) -> { }, servicioBloqueadas(), bcrypt, reloj, Duration.ZERO,
                new SecureRandom()));
        for (Annotation a : ActivacionDocenteService.class.getAnnotations()) {
            assertFalse(a.annotationType().getName().startsWith("org.springframework"), a.toString());
        }
    }

    private static ContrasenasBloqueadasPort servicioBloqueadas() {
        return new ContrasenasBloqueadasPort() {
            @Override public boolean contiene(String n) { return false; }
            @Override public String huellaSha256() { return "x"; }
            @Override public int tamano() { return 0; }
        };
    }

    @SuppressWarnings("unused")
    private static final Evento[] EVENTOS = Evento.values();
    // ---------- DC-77: explicit revocation ----------
    @Test
    void revocacionExplicitaInvalidaElTokenYEsIdempotente() {
        String t = emitir();
        assertEquals(1, servicio.revocar(docente, admin));
        assertTrue(auditoria.contains("REVOCACION:ADMINISTRADOR"));
        invalida(t, CLAVE);
        assertTrue(auditoria.contains("RECHAZO:REVOCADO"));
        assertNull(cuentas.get(docente).getPasswordHash());
        int antes = auditoria.size();
        assertEquals(0, servicio.revocar(docente, admin), "nothing live: no error");
        assertEquals(antes, auditoria.size(), "an empty revocation is not audited as a revocation");
        String nuevo = emitir();
        servicio.activar(nuevo, CLAVE);
        assertNotNull(cuentas.get(docente).getPasswordHash());
    }

    @Test
    void revocarExigeUsuarioExistenteYActor() {
        assertEquals("USER_NOT_FOUND",
                assertThrows(IdentityDomainException.class, () -> servicio.revocar(UUID.randomUUID(), admin)).code());
        assertThrows(NullPointerException.class, () -> servicio.revocar(docente, null));
        assertThrows(NullPointerException.class, () -> servicio.revocar(null, admin));
    }
}
