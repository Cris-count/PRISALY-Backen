package com.prisaly.m1.application.support;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.RevocacionCodigoAccesoPort;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import com.prisaly.m1.domain.student.PoliticaSesionEstudiante;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test double of the DD-02 B persistence port. Not production evidence: PostgreSQL behaviour is in the ITs. */
public class AccesoEstudianteMemoria implements AccesoEstudiantePort, RevocacionCodigoAccesoPort {

    public final Map<UUID, VinculoEstudiante> vinculos = new ConcurrentHashMap<>();
    public final Map<UUID, Credencial> credenciales = new ConcurrentHashMap<>();
    public final List<Sesion> sesiones = new CopyOnWriteArrayList<>();
    public final List<String> auditoria = new CopyOnWriteArrayList<>();
    public volatile boolean fallar;

    public static final class Credencial {
        public final CredencialNueva datos;
        public volatile boolean consumida;
        public volatile boolean revocada;
        public volatile int intentos;

        Credencial(CredencialNueva datos) {
            this.datos = datos;
        }
    }

    public record Sesion(SesionNueva datos, boolean[] revocada, OffsetDateTime[] actividad) {
    }

    public UUID alta(String correo, boolean activa) {
        UUID usuario = UUID.randomUUID();
        vinculos.put(usuario, new VinculoEstudiante(usuario, UUID.randomUUID(), correo, activa));
        return usuario;
    }

    private void exigir() {
        if (fallar) {
            throw new IllegalStateException("fallo técnico simulado");
        }
    }

    @Override
    public Optional<VinculoEstudiante> vinculoPorCorreo(String correoNormalizado) {
        exigir();
        return vinculos.values().stream().filter(v -> v.correo().equalsIgnoreCase(correoNormalizado)).findFirst();
    }

    @Override
    public Optional<VinculoEstudiante> vinculo(UUID idUsuario) {
        exigir();
        return Optional.ofNullable(vinculos.get(idUsuario));
    }

    @Override
    public void revocarCredencialesAbiertas(UUID idUsuario, OffsetDateTime cuando) {
        credenciales.values().stream().filter(c -> c.datos.userId().equals(idUsuario) && !c.consumida)
                .forEach(c -> c.revocada = true);
    }

    @Override
    public void guardarCredencial(CredencialNueva credencial) {
        exigir();
        credenciales.put(credencial.challengeId(), new Credencial(credencial));
    }

    @Override
    public synchronized Optional<CredencialGuardada> bloquearCredencial(UUID challengeId) {
        Credencial c = credenciales.get(challengeId);
        if (c == null) {
            return Optional.empty();
        }
        return Optional.of(new CredencialGuardada(challengeId, c.datos.userId(), c.datos.studentId(),
                c.datos.codigoHmac(), c.datos.kidCodigo(), c.datos.expira(), c.consumida, c.revocada, c.intentos));
    }

    @Override
    public void registrarIntentoFallido(UUID challengeId) {
        Credencial c = credenciales.get(challengeId);
        if (c != null && c.intentos < PoliticaCodigoAcceso.INTENTOS_MAXIMOS) {
            c.intentos++;
        }
    }

    @Override
    public synchronized boolean consumir(UUID challengeId, OffsetDateTime ahora) {
        Credencial c = credenciales.get(challengeId);
        if (c == null || c.consumida || c.revocada || !c.datos.expira().isAfter(ahora)
                || c.intentos >= PoliticaCodigoAcceso.INTENTOS_MAXIMOS) {
            return false;
        }
        c.consumida = true;
        return true;
    }

    @Override
    public void abrirSesion(SesionNueva sesion) {
        exigir();
        sesiones.add(new Sesion(sesion, new boolean[] {false}, new OffsetDateTime[] {sesion.creada()}));
    }

    @Override
    public Optional<SesionGuardada> sesionVigente(List<byte[]> candidatos, OffsetDateTime ahora) {
        exigir();
        for (Sesion s : sesiones) {
            boolean coincide = candidatos.stream().anyMatch(h -> Arrays.equals(h, s.datos().tokenHmac()));
            if (coincide && !s.revocada()[0] && s.datos().expira().isAfter(ahora)
                    && s.actividad()[0].plus(PoliticaSesionEstudiante.INACTIVIDAD_MAXIMA).isAfter(ahora)) {
                s.actividad()[0] = ahora;
                return Optional.of(new SesionGuardada(s.datos().userId(), s.datos().studentId()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<UUID> revocarSesion(List<byte[]> candidatos, OffsetDateTime cuando) {
        Optional<UUID> revocada = Optional.empty();
        for (Sesion s : sesiones) {
            if (!s.revocada()[0] && candidatos.stream().anyMatch(h -> Arrays.equals(h, s.datos().tokenHmac()))) {
                s.revocada()[0] = true;
                revocada = Optional.of(s.datos().studentId());
            }
        }
        return revocada;
    }

    @Override
    public void auditar(String evento, UUID idEstudiante, String resultado, String origen, OffsetDateTime cuando) {
        auditoria.add(evento + ":" + resultado);
    }

    @Override
    public Purga purgar(OffsetDateTime ahora, int diasAuditoria) {
        return new Purga(0, 0, 0);
    }

    @Override
    public void revocarPorFalloDeEntrega(String challengeId) {
        Credencial c = credenciales.get(UUID.fromString(challengeId));
        if (c != null && !c.consumida) {
            c.revocada = true;
        }
    }

    public List<String> auditoriaComoLista() {
        return new ArrayList<>(auditoria);
    }
}
