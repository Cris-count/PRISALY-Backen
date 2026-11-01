package com.prisaly.m5.infrastructure.persistencia;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Columnas de {@code "Intento"} (BD-005.2, V001) que M5 lee o modifica.
 *
 * <p>M5 no crea intentos todavía (el inicio está bloqueado), por lo que
 * {@code idParticipacion}, {@code numeroIntento}, {@code inicio},
 * {@code limiteTemporal}, {@code idAutorizacion} y {@code estadoEvaluacion}
 * no se mapean o son de solo lectura. {@code estadoEvaluacion} pertenece a M6.
 */
@Entity
@Table(name = "Intento")
public class IntentoEntity {

    @Id
    @Column(name = "idIntento", nullable = false, updatable = false)
    private UUID idIntento;

    @Column(name = "idParticipacion", nullable = false, insertable = false, updatable = false)
    private UUID idParticipacion;

    @Column(name = "estado", nullable = false, length = 25)
    private String estado;

    @Column(name = "motivoFinalizacion", length = 40)
    private String motivoFinalizacion;

    @Column(name = "fin")
    private OffsetDateTime fin;

    protected IntentoEntity() {
    }

    public UUID getIdIntento() {
        return idIntento;
    }

    public UUID getIdParticipacion() {
        return idParticipacion;
    }

    public String getEstado() {
        return estado;
    }

    public String getMotivoFinalizacion() {
        return motivoFinalizacion;
    }

    public OffsetDateTime getFin() {
        return fin;
    }

    /**
     * Refleja el estado del agregado. La marca {@code fin} se fija una sola vez,
     * al pasar a Finalizado; V005/V007 ya impiden volver a EnCurso.
     */
    void reflejar(String nuevoEstado, String nuevoMotivo, OffsetDateTime ahora) {
        if (MapeoIntento.FINALIZADO.equals(nuevoEstado) && fin == null) {
            fin = ahora;
        }
        estado = nuevoEstado;
        motivoFinalizacion = nuevoMotivo;
    }
}
