package com.prisaly.m4.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException;
import com.prisaly.m4.domain.state.ProgramadaState;
import com.prisaly.m4.domain.state.SesionState;
import com.prisaly.m4.domain.state.SesionStateFactory;

/**
 * Agregado raíz del dominio M4: una sesión académica que vincula
 * un caso publicado (M3) con un grupo de estudiantes convocados.
 *
 * <p>El comportamiento varía según el estado gracias al patrón GoF State.</p>
 */
public class SesionAcademica {

    private UUID id;
    private UUID casoId;
    private UUID docenteId;
    private String titulo;
    private String descripcion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private EstadoSesion estado;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private int version;

    /** Estado transiente: no se persiste, se reconstruye vía {@link SesionStateFactory}. */
    private transient SesionState stateHandler;

    // ─── Constructores ────────────────────────────────────────

    /** Constructor para rehidratación desde persistencia. */
    public SesionAcademica() {
    }

    /**
     * Constructor de fábrica para la creación de una nueva sesión.
     * Aplica todas las validaciones de creación.
     */
    public static SesionAcademica crear(UUID casoId,
                                         UUID docenteId,
                                         String titulo,
                                         String descripcion,
                                         LocalDateTime fechaInicio,
                                         LocalDateTime fechaFin) {
        validarFechas(fechaInicio, fechaFin);
        Objects.requireNonNull(casoId, "casoId no puede ser nulo");
        Objects.requireNonNull(docenteId, "docenteId no puede ser nulo");
        Objects.requireNonNull(titulo, "titulo no puede ser nulo");

        var sesion = new SesionAcademica();
        sesion.id = UUID.randomUUID();
        sesion.casoId = casoId;
        sesion.docenteId = docenteId;
        sesion.titulo = titulo.strip();
        sesion.descripcion = descripcion;
        sesion.fechaInicio = fechaInicio;
        sesion.fechaFin = fechaFin;
        sesion.estado = EstadoSesion.PROGRAMADA;
        sesion.stateHandler = ProgramadaState.INSTANCE;
        sesion.creadoEn = LocalDateTime.now();
        sesion.actualizadoEn = sesion.creadoEn;
        return sesion;
    }

    // ─── Operaciones de estado (patrón State) ─────────────────

    public void abrir() {
        resolverState().abrir(this);
    }

    public void iniciar() {
        resolverState().iniciar(this);
    }

    public void cerrar() {
        resolverState().cerrar(this);
    }

    public void cancelar() {
        resolverState().cancelar(this);
    }

    /**
     * Invocado internamente por las clases State para efectuar el cambio.
     */
    public void cambiarEstado(SesionState nuevoState) {
        this.stateHandler = nuevoState;
        this.estado = nuevoState.getEstado();
        this.actualizadoEn = LocalDateTime.now();
    }

    // ─── Reglas de modificación ───────────────────────────────

    /**
     * Actualiza datos de programación si el estado lo permite.
     *
     * @throws SesionOperacionNoPermitidaException si el estado prohíbe la modificación.
     */
    public void actualizarProgramacion(String nuevoTitulo,
                                        String nuevaDescripcion,
                                        LocalDateTime nuevaFechaInicio,
                                        LocalDateTime nuevaFechaFin) {
        if (!resolverState().permiteModificacion()) {
            throw new SesionOperacionNoPermitidaException(estado.name(), "actualizarProgramacion");
        }
        validarFechas(nuevaFechaInicio, nuevaFechaFin);

        this.titulo = nuevoTitulo.strip();
        this.descripcion = nuevaDescripcion;
        this.fechaInicio = nuevaFechaInicio;
        this.fechaFin = nuevaFechaFin;
        this.actualizadoEn = LocalDateTime.now();
    }

    /**
     * Cambia el caso asociado si el estado lo permite.
     * El nuevo caso debe estar Publicado — pero esa validación la hace el servicio
     * consultando M3. Aquí solo protegemos que el estado actual permita cambios.
     */
    public void cambiarCaso(UUID nuevoCasoId) {
        Objects.requireNonNull(nuevoCasoId, "nuevoCasoId no puede ser nulo");
        if (!resolverState().permiteModificacion()) {
            throw new SesionOperacionNoPermitidaException(estado.name(), "cambiarCaso");
        }
        this.casoId = nuevoCasoId;
        this.actualizadoEn = LocalDateTime.now();
    }

    public boolean permiteConvocatoria() {
        return resolverState().permiteConvocatoria();
    }

    // ─── Validaciones de dominio ──────────────────────────────

    private static void validarFechas(LocalDateTime inicio, LocalDateTime fin) {
        Objects.requireNonNull(inicio, "fechaInicio no puede ser nula");
        Objects.requireNonNull(fin, "fechaFin no puede ser nula");
        if (!fin.isAfter(inicio)) {
            throw new ReglaNegocioException("M4-FECHA-INVALIDA",
                    "La fecha de fin debe ser posterior a la fecha de inicio");
        }
    }

    // ─── Resolución de estado ─────────────────────────────────

    private SesionState resolverState() {
        if (stateHandler == null) {
            stateHandler = SesionStateFactory.of(estado);
        }
        return stateHandler;
    }

    // ─── Accessors ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCasoId() { return casoId; }
    public void setCasoId(UUID casoId) { this.casoId = casoId; }

    public UUID getDocenteId() { return docenteId; }
    public void setDocenteId(UUID docenteId) { this.docenteId = docenteId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public EstadoSesion getEstado() { return estado; }
    public void setEstado(EstadoSesion estado) { this.estado = estado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
