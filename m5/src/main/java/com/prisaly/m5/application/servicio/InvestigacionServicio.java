package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.InvestigacionProtegida;
import com.prisaly.m5.application.port.PortInvestigacion;
import com.prisaly.m5.domain.investigacion.Edificio;
import com.prisaly.m5.domain.investigacion.Nota;
import com.prisaly.m5.domain.investigacion.Pista;
import com.prisaly.m5.domain.investigacion.Zona;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Investigación del intento: notas, pistas, edificios y zonas, UUID idempotente del dominio. */
public class InvestigacionServicio implements InvestigacionProtegida {
    private final AccesoProtegidoParticipacion acceso;
    private final PortInvestigacion port;

    public InvestigacionServicio(AccesoProtegidoParticipacion acceso, PortInvestigacion port) {
        this.acceso = Objects.requireNonNull(acceso, "acceso");
        this.port = Objects.requireNonNull(port, "port");
    }

    @Transactional(readOnly = true)
    @Override
    public List<Nota> notas(UUID idIntento) {
        return port.notasDeIntento(requerirIntento(idIntento));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Pista> pistas(UUID idIntento) {
        return port.pistasDeIntento(requerirIntento(idIntento));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Edificio> edificios(UUID idIntento) {
        return port.edificiosDeIntento(requerirIntento(idIntento));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Zona> zonas(UUID idIntento) {
        return port.zonasDeIntento(requerirIntento(idIntento));
    }

    @Override
    public Nota guardarNota(UUID idIntento, String titulo, String texto) {
        var nota = new Nota(UUID.randomUUID(), requerirIntento(idIntento), titulo.trim(), texto.trim(), Instant.now());
        port.insertar(nota);
        return nota;
    }

    @Override
    public Pista guardarPista(UUID idIntento, String descripcion) {
        var pista = new Pista(UUID.randomUUID(), requerirIntento(idIntento), descripcion.trim(), Instant.now());
        port.insertar(pista);
        return pista;
    }

    @Override
    public Edificio guardarEdificio(UUID idIntento, String nombre, String ubicacion) {
        var edificio = new Edificio(UUID.randomUUID(), requerirIntento(idIntento), nombre.trim(), ubicacion, Instant.now());
        port.insertar(edificio);
        return edificio;
    }

    @Override
    public Zona guardarZona(UUID idIntento, String nombre, String descripcion) {
        var zona = new Zona(UUID.randomUUID(), requerirIntento(idIntento), nombre.trim(), descripcion, Instant.now());
        port.insertar(zona);
        return zona;
    }

    private UUID requerirIntento(UUID idIntento) {
        if (idIntento == null) throw new DatoAplicacionInvalidoException("El idIntento es obligatorio");
        acceso.consultarIntento(new ReferenciaIntento(idIntento.toString()));
        return idIntento;
    }
}
