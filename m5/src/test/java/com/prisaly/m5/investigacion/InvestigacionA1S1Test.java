package com.prisaly.m5.investigacion;

import static com.prisaly.m5.application.investigacion.InvestigacionA1RechazadaException.Codigo.*;
import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.application.update.JsonEstricto;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.FaseAcademica;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.application.investigacion.InvestigacionA1;
import com.prisaly.m5.application.investigacion.InvestigacionA1RechazadaException;
import com.prisaly.m5.application.investigacion.RegistroEventosA1;
import com.prisaly.m5.application.investigacion.RegistroEventosA1.Solicitud;
import com.prisaly.m5.application.investigacion.RegistroNotasA1;
import com.prisaly.m5.domain.intento.EstadoIntento;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CB-02B (A1) · S1: rechazos de la investigación con el modelo de eventos. Sin base de datos ni HTTP (el esquema y el
 * contrato están pendientes de D-ESQ-1): el repositorio es un doble en memoria y la titularidad, el estado y el guion
 * fijado son dobles de los puertos que en M5 cubren AccesoProtegidoParticipacion y FasesIntento.
 */
class InvestigacionA1S1Test {

    // Caso A de M3 (CB-02) con una consecuencia de marca para cubrir marca.fijada.
    static final String GUION = """
            {"formato":"prisaly-guion-1","inicio":"hospital",
             "espacios":[
              {"idEspacio":"hospital","titulo":"Hospital","recursoVisual":"edificio:hospital",
               "informaciones":[{"idInformacion":"motivo-ingreso","texto":"Ingreso por crisis de ansiedad."}]},
              {"idEspacio":"comisaria","titulo":"Comisaría","recursoVisual":"edificio:comisaria"}],
             "personajes":[
              {"idPersonaje":"dr-martinez","nombre":"Dr. Martínez","recursoVisual":"npc:hosp-npc-doctor"},
              {"idPersonaje":"oficial-rios","nombre":"Oficial Ríos","recursoVisual":"npc:pol-npc-oficial"}],
             "intervenciones":[
              {"idIntervencion":"medico-conversacion","idEspacio":"hospital","idPersonaje":"dr-martinez",
               "dialogos":[{"idPersonaje":"dr-martinez","texto":"Buenos días."}],
               "consecuencias":[{"tipo":"marca","marca":"hablo-con-medico","valor":true}]},
              {"idIntervencion":"oficial-conversacion","idEspacio":"comisaria","idPersonaje":"oficial-rios",
               "dialogos":[{"idPersonaje":"oficial-rios","texto":"Recibimos una llamada."}]}]}
            """;
    static final String HASH = "687c7a6f".repeat(8);
    static final UUID GUION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    final UUID intentoA = UUID.randomUUID(), intentoB = UUID.randomUUID();
    final Map<UUID, EstadoIntento> estados = new HashMap<>();
    final Map<UUID, FaseAcademica> fasesPorIntento = new HashMap<>();
    final Set<UUID> propios = new java.util.HashSet<>();
    final Memoria repo = new Memoria();
    InvestigacionA1 servicio;

    @BeforeEach
    void preparar() {
        for (UUID i : List.of(intentoA, intentoB)) {
            estados.put(i, EstadoIntento.EN_CURSO);
            fasesPorIntento.put(i, FaseAcademica.EXPLORACION);
        }
        propios.add(intentoA); // el estudiante autenticado es el titular de A; B es de otro estudiante
        InvestigacionA1.FaseYGuion fases = new InvestigacionA1.FaseYGuion() {
            public EstadoFase estado(ReferenciaIntento r) {
                UUID id = UUID.fromString(r.valor());
                FaseAcademica fase = fasesPorIntento.get(id);
                return new EstadoFase(id, estados.get(id), fase, Optional.of(Instant.EPOCH),
                        fase == FaseAcademica.CUESTIONARIO ? Optional.of(Instant.EPOCH.plusSeconds(60)) : Optional.empty(),
                        Instant.EPOCH.plusSeconds(3600), Instant.EPOCH);
            }

            public NarrativaIntento narrativa(ReferenciaIntento r) {
                return new NarrativaIntento(UUID.fromString(r.valor()), GUION_ID, 1, HASH, GUION);
            }
        };
        servicio = new InvestigacionA1(r -> {
            if (!propios.contains(UUID.fromString(r.valor()))) throw new AccesoIntentoDenegadoException();
        }, fases, repo, JsonEstricto::objeto, Clock.fixed(Instant.parse("2026-11-26T20:00:00Z"), ZoneOffset.UTC));
    }

    Solicitud evento(String tipo, String ref) {
        return new Solicitud(UUID.randomUUID(), tipo, ref, null, HASH);
    }

    static void rechaza(InvestigacionA1RechazadaException.Codigo codigo, Executable accion) {
        assertEquals(codigo, assertThrows(InvestigacionA1RechazadaException.class, accion).codigo());
    }

    @Test
    void edificioQueNoPerteneceAlCasoSeRechazaSinEscribir() {
        rechaza(ESPACIO_NO_HABILITADO, () -> servicio.registrarEvento(intentoA, evento("espacio.visitado", "fiscalia")));
        assertTrue(repo.eventos.isEmpty());
    }

    @Test
    void informacionInexistenteONoAlcanzadaSeRechaza() {
        rechaza(INFORMACION_INEXISTENTE, () -> servicio.registrarEvento(intentoA, evento("informacion.obtenida", "huella-dactilar")));
        // Existe en el guion, pero el estudiante no ha visitado el Hospital: Unreal no decide qué se obtuvo.
        rechaza(ESPACIO_NO_VISITADO, () -> servicio.registrarEvento(intentoA, evento("informacion.obtenida", "motivo-ingreso")));
        servicio.registrarEvento(intentoA, evento("espacio.visitado", "hospital"));
        assertTrue(servicio.registrarEvento(intentoA, evento("informacion.obtenida", "motivo-ingreso")).nuevo());
    }

    @Test
    void intervencionNoAutorizadaOInexistenteSeRechaza() {
        rechaza(INTERVENCION_INEXISTENTE, () -> servicio.registrarEvento(intentoA, evento("intervencion.completada", "fiscal-conversacion")));
        rechaza(INTERVENCION_NO_AUTORIZADA, () -> servicio.registrarEvento(intentoA, evento("intervencion.completada", "oficial-conversacion")));
        servicio.registrarEvento(intentoA, evento("espacio.visitado", "comisaria"));
        assertTrue(servicio.registrarEvento(intentoA, evento("intervencion.completada", "oficial-conversacion")).nuevo());
    }

    @Test
    void intentoDeOtroEstudianteSeRechazaAntesDeLeerNada() {
        assertThrows(AccesoIntentoDenegadoException.class, () -> servicio.registrarEvento(intentoB, evento("espacio.visitado", "hospital")));
        assertThrows(AccesoIntentoDenegadoException.class, () -> servicio.guardarNota(intentoB, UUID.randomUUID(), "nota", 0));
        assertThrows(AccesoIntentoDenegadoException.class, () -> servicio.progreso(intentoB, 0));
        assertTrue(repo.eventos.isEmpty());
        assertTrue(repo.notas.isEmpty());
    }

    @Test
    void intentoCerradoOEnCuestionarioNoAdmiteEscrituras() {
        fasesPorIntento.put(intentoA, FaseAcademica.CUESTIONARIO);
        rechaza(INTENTO_CERRADO, () -> servicio.registrarEvento(intentoA, evento("espacio.visitado", "hospital")));
        estados.put(intentoA, EstadoIntento.FINALIZADO);
        rechaza(INTENTO_CERRADO, () -> servicio.registrarEvento(intentoA, evento("espacio.visitado", "hospital")));
        rechaza(INTENTO_CERRADO, () -> servicio.guardarNota(intentoA, UUID.randomUUID(), "nota", 0));
        assertTrue(repo.eventos.isEmpty());
        assertTrue(repo.notas.isEmpty());
    }

    @Test
    void identificadorDeEventoReutilizadoConOtroContenidoSeRechaza() {
        UUID clave = UUID.randomUUID();
        var primero = servicio.registrarEvento(intentoA, new Solicitud(clave, "espacio.visitado", "hospital", null, HASH));
        var reenvio = servicio.registrarEvento(intentoA, new Solicitud(clave, "espacio.visitado", "hospital", null, HASH));
        assertTrue(primero.nuevo());
        assertFalse(reenvio.nuevo());
        assertEquals(primero.evento(), reenvio.evento());
        rechaza(CLAVE_REUTILIZADA, () -> servicio.registrarEvento(intentoA, new Solicitud(clave, "espacio.visitado", "comisaria", null, HASH)));
        assertEquals(1, repo.eventos.get(intentoA).size());
    }

    @Test
    void revisionDeNotaObsoletaSeRechaza() {
        UUID nota = UUID.randomUUID();
        assertEquals(1, servicio.guardarNota(intentoA, nota, "Primera versión", 0).revision());
        assertEquals(2, servicio.guardarNota(intentoA, nota, "Segunda versión", 1).revision());
        rechaza(REVISION_OBSOLETA, () -> servicio.guardarNota(intentoA, nota, "Pisaría la segunda", 1));
        rechaza(REVISION_OBSOLETA, () -> servicio.eliminarNota(intentoA, nota, 1));
        servicio.eliminarNota(intentoA, nota, 2);
        servicio.eliminarNota(intentoA, nota, 2); // DELETE repetido: sin cambios
        rechaza(NOTA_ELIMINADA, () -> servicio.guardarNota(intentoA, nota, "Revivir", 3));
        assertTrue(servicio.progreso(intentoA, 0).notas().isEmpty());
    }

    @Test
    void narrativaIncompatibleConLaVersionFijadaSeRechaza() {
        rechaza(GUION_INCOMPATIBLE, () -> servicio.registrarEvento(intentoA,
                new Solicitud(UUID.randomUUID(), "espacio.visitado", "hospital", null, "0".repeat(64))));
        assertTrue(repo.eventos.isEmpty());
    }

    @Test
    void secuenciaDelServidorSinHuecosEIdempotenciaPorContenido() {
        var a = servicio.registrarEvento(intentoA, evento("espacio.visitado", "hospital"));
        rechaza(ESPACIO_NO_HABILITADO, () -> servicio.registrarEvento(intentoA, evento("espacio.visitado", "fiscalia")));
        var b = servicio.registrarEvento(intentoA, evento("intervencion.completada", "medico-conversacion"));
        var repetido = servicio.registrarEvento(intentoA, evento("espacio.visitado", "hospital")); // otra clave, mismo contenido
        assertEquals(List.of(1L, 2L), repo.eventos.get(intentoA).stream().map(RegistroEventosA1.Evento::secuencia).toList());
        assertFalse(repetido.nuevo());
        assertEquals(a.evento().idEvento(), repetido.evento().idEvento());
        assertEquals(List.of(b.evento()), servicio.progreso(intentoA, 1).eventos());
    }

    @Test
    void marcasDelGuionYMarcaConflictiva() {
        rechaza(MARCA_INEXISTENTE, () -> servicio.registrarEvento(intentoA, new Solicitud(UUID.randomUUID(), "marca.fijada", "culpable", true, HASH)));
        rechaza(VALOR_REQUERIDO, () -> servicio.registrarEvento(intentoA, evento("marca.fijada", "hablo-con-medico")));
        servicio.registrarEvento(intentoA, new Solicitud(UUID.randomUUID(), "marca.fijada", "hablo-con-medico", true, HASH));
        rechaza(MARCA_CONFLICTIVA, () -> servicio.registrarEvento(intentoA, new Solicitud(UUID.randomUUID(), "marca.fijada", "hablo-con-medico", false, HASH)));
    }

    @Test
    void tiposYReferenciasDeTextoLibreSeRechazan() {
        rechaza(TIPO_EVENTO_INVALIDO, () -> servicio.registrarEvento(intentoA, evento("edificio.visitado", "hospital")));
        rechaza(REFERENCIA_INVALIDA, () -> servicio.registrarEvento(intentoA, evento("espacio.visitado", "Hospital Central")));
    }

    @Test
    void elProgresoDeUnEstudianteNoIncluyeElDeOtro() {
        propios.add(intentoB);
        servicio.registrarEvento(intentoA, evento("espacio.visitado", "hospital"));
        servicio.registrarEvento(intentoB, evento("espacio.visitado", "comisaria"));
        assertEquals(List.of("hospital"), servicio.progreso(intentoA, 0).eventos().stream().map(RegistroEventosA1.Evento::referencia).toList());
        assertEquals(List.of("comisaria"), servicio.progreso(intentoB, 0).eventos().stream().map(RegistroEventosA1.Evento::referencia).toList());
        assertEquals(GUION_ID, servicio.progreso(intentoA, 0).idGuion());
    }

    /** Doble del futuro repositorio; aísla por intento como lo hará la clave foránea. */
    static final class Memoria implements InvestigacionA1.Repositorio {
        final Map<UUID, List<RegistroEventosA1.Evento>> eventos = new HashMap<>();
        final Map<UUID, Map<UUID, RegistroNotasA1.Nota>> notas = new HashMap<>();

        public List<RegistroEventosA1.Evento> eventosBloqueados(UUID i) { return List.copyOf(eventos.getOrDefault(i, List.of())); }
        public List<RegistroEventosA1.Evento> eventos(UUID i, long desde) {
            return eventos.getOrDefault(i, List.of()).stream().filter(e -> e.secuencia() > desde).toList();
        }
        public void insertar(UUID i, RegistroEventosA1.Evento e) { eventos.computeIfAbsent(i, k -> new ArrayList<>()).add(e); }
        public Optional<RegistroNotasA1.Nota> nota(UUID i, UUID n) { return Optional.ofNullable(notas.getOrDefault(i, Map.of()).get(n)); }
        public List<RegistroNotasA1.Nota> notas(UUID i) { return List.copyOf(notas.getOrDefault(i, Map.of()).values()); }
        public void guardar(UUID i, RegistroNotasA1.Nota n) { notas.computeIfAbsent(i, k -> new HashMap<>()).put(n.idNota(), n); }
    }
}
