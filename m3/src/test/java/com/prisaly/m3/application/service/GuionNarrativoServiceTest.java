package com.prisaly.m3.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio;
import com.prisaly.m3.domain.narrativa.GuionNarrativoInvalidoException;
import com.prisaly.m3.domain.narrativa.GuionNarrativoPrueba;
import com.prisaly.m3.domain.narrativa.ValidadorGuionNarrativo;
import com.prisaly.m3.domain.rule.CasoDomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Evaluation order and guards of the narrative authoring use case (in-memory doubles, not database evidence). */
class GuionNarrativoServiceTest {
    final UUID autor = UUID.randomUUID(), caso = UUID.randomUUID();
    UUID actual = autor;
    boolean modificar = true, publicarPermiso = true;
    final Repo repo = new Repo();
    int transacciones;

    final CaseAuthorizationPort autorizacion = new CaseAuthorizationPort() {
        public boolean canCreateCase(String u, String d) { return false; }
        public boolean canModifyCase(String u, String c) { return modificar; }
        public boolean canPublishCase(String u, String c) { return publicarPermiso; }
    };

    GuionNarrativoService servicio() {
        return new GuionNarrativoService(() -> actual, autorizacion, repo,
                new com.prisaly.m3.application.port.UnidadTransaccionalNarrativa() {
                    public <T> T ejecutar(java.util.function.Supplier<T> c) { transacciones++; return c.get(); }
                }, new ValidadorGuionNarrativo(Set.of()));
    }

    @Test
    void elAutorGuardaUnBorradorValidoYLoPublica() {
        var borrador = servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO);
        assertEquals("Borrador", borrador.estado());
        var publicado = servicio().publicar(caso);
        assertEquals("Publicado", publicado.estado());
        assertEquals(List.of("bloquear", "guardar", "bloquear", "borrador", "publicar"), repo.llamadas);
        assertEquals(2, transacciones);
    }

    @Test
    void sinIdentidadOSinPermisoNoSeLeeNada() {
        actual = null;
        assertThrows(CasoAuthenticationException.class, () -> servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO));
        actual = autor;
        modificar = false;
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class,
                () -> servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO)).code());
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class, () -> servicio().consultar(caso)).code());
        modificar = true;
        publicarPermiso = false;
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class, () -> servicio().publicar(caso)).code());
        assertEquals(List.of(), repo.llamadas);
        assertEquals(0, transacciones);
    }

    @Test
    void otroDocenteConPermisosNoEsElAutor() {
        actual = UUID.randomUUID();
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class,
                () -> servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO)).code());
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class, () -> servicio().publicar(caso)).code());
        assertFalse(repo.llamadas.contains("guardar"));
    }

    @Test
    void casoInexistenteProgramadoOArchivado() {
        repo.existe = false;
        assertEquals("CASE_NOT_FOUND", assertThrows(CasoDomainException.class,
                () -> servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO)).code());
        repo.existe = true;
        repo.bloqueado = true;
        assertEquals("CONTENIDO_BLOQUEADO", assertThrows(CasoDomainException.class,
                () -> servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO)).code());
        assertEquals("CONTENIDO_BLOQUEADO", assertThrows(CasoDomainException.class, () -> servicio().publicar(caso)).code());
        repo.bloqueado = false;
        repo.estado = "Archivado";
        assertEquals("CASE_NOT_EDITABLE", assertThrows(CasoDomainException.class,
                () -> servicio().guardarBorrador(caso, GuionNarrativoPrueba.VALIDO)).code());
        assertFalse(repo.llamadas.contains("guardar"));
    }

    @Test
    void publicarExigeCasoPublicadoActivoYBorrador() {
        repo.estado = "Borrador";
        assertEquals("CASO_NO_PUBLICABLE", assertThrows(CasoDomainException.class, () -> servicio().publicar(caso)).code());
        repo.estado = "Publicado";
        repo.activo = false;
        assertEquals("CASO_NO_PUBLICABLE", assertThrows(CasoDomainException.class, () -> servicio().publicar(caso)).code());
        repo.activo = true;
        assertEquals("SIN_BORRADOR", assertThrows(CasoDomainException.class, () -> servicio().publicar(caso)).code());
        assertFalse(repo.llamadas.contains("publicar"));
    }

    @Test
    void unCuerpoInvalidoSeRechazaDespuesDeAutoriaYEstadoYNoEscribe() {
        var json = assertThrows(GuionNarrativoInvalidoException.class, () -> servicio().guardarBorrador(caso, "{no es json"));
        assertEquals("JSON_INVALIDO", json.errores().get(0).motivo());
        var grande = assertThrows(GuionNarrativoInvalidoException.class,
                () -> servicio().guardarBorrador(caso, "{\"x\":\"" + "a".repeat(ValidadorGuionNarrativo.MAX_BYTES) + "\"}"));
        assertEquals("DOCUMENTO_DEMASIADO_GRANDE", grande.errores().get(0).motivo());
        assertThrows(GuionNarrativoInvalidoException.class, () -> servicio().guardarBorrador(caso, "{\"formato\":\"x\"}"));
        assertFalse(repo.llamadas.contains("guardar"));
        actual = UUID.randomUUID();
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class, () -> servicio().guardarBorrador(caso, "{no es json")).code(),
                "ownership is checked before the body");
    }

    final class Repo implements GuionNarrativoRepositorio {
        final List<String> llamadas = new ArrayList<>();
        boolean existe = true, bloqueado, activo = true;
        String estado = "Publicado";
        GuionGuardado borrador;

        public Optional<CasoNarrativa> bloquearCaso(UUID id) {
            llamadas.add("bloquear");
            return existe ? Optional.of(new CasoNarrativa(id, autor, estado, activo, bloqueado)) : Optional.empty();
        }
        public Optional<GuionGuardado> borrador(UUID id) { llamadas.add("borrador"); return Optional.ofNullable(borrador); }
        public Optional<GuionGuardado> publicadoVigente(UUID id) { return Optional.empty(); }
        public GuionGuardado guardarBorrador(UUID id, UUID a, String doc) {
            llamadas.add("guardar");
            borrador = new GuionGuardado(UUID.randomUUID(), id, 1, "Borrador", doc, "0".repeat(64), a, Instant.EPOCH, Optional.empty());
            return borrador;
        }
        public GuionGuardado publicarBorrador(UUID id) {
            llamadas.add("publicar");
            return new GuionGuardado(borrador.idGuion(), id, 1, "Publicado", borrador.definicionJson(), borrador.hash(), autor,
                    Instant.EPOCH, Optional.of(Instant.EPOCH));
        }
    }
}
