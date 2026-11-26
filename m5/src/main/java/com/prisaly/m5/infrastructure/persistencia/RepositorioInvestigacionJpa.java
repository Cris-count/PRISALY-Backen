package com.prisaly.m5.infrastructure.persistencia;

import com.prisaly.m5.application.port.PortInvestigacion;
import com.prisaly.m5.domain.investigacion.Edificio;
import com.prisaly.m5.domain.investigacion.Nota;
import com.prisaly.m5.domain.investigacion.Pista;
import com.prisaly.m5.domain.investigacion.Zona;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Adaptador JdbcTemplate de {@link PortInvestigacion} (tablas candidatas sin modificar Intento). */
public class RepositorioInvestigacionJpa implements PortInvestigacion {

    private final JdbcTemplate jdbc;

    public RepositorioInvestigacionJpa(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<Nota> notasDeIntento(UUID idIntento) {
        RowMapper<NotaIntentoEntity> m = (rs, n) -> new NotaIntentoEntity(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant());
        return jdbc.query(
                "SELECT \"idNota\", \"idIntento\", \"titulo\", \"texto\", \"fechaCreacion\" FROM public.\"NotaIntento\" WHERE \"idIntento\" = ? ORDER BY \"fechaCreacion\", \"idNota\"",
                new Object[]{idIntento}, m)
                .stream().map(e -> new Nota(e.id(), e.idIntento(), e.titulo(), e.texto(), e.fecha())).toList();
    }

    @Override
    public List<Pista> pistasDeIntento(UUID idIntento) {
        RowMapper<PistaIntentoEntity> m = (rs, n) -> new PistaIntentoEntity(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getTimestamp(4).toInstant());
        return jdbc.query("SELECT \"idPista\", \"idIntento\", \"descripcion\", \"fechaCreacion\" FROM public.\"PistaIntento\" WHERE \"idIntento\" = ? ORDER BY \"fechaCreacion\", \"idPista\"",
                new Object[]{idIntento}, m)
                .stream().map(e -> new Pista(e.id(), e.idIntento(), e.descripcion(), e.fecha())).toList();
    }

    @Override
    public List<Edificio> edificiosDeIntento(UUID idIntento) {
        RowMapper<EdificioIntentoEntity> m = (rs, n) -> new EdificioIntentoEntity(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant());
        return jdbc.query("SELECT \"idEdificio\", \"idIntento\", \"nombre\", \"ubicacion\", \"fechaCreacion\" FROM public.\"EdificioIntento\" WHERE \"idIntento\" = ? ORDER BY \"fechaCreacion\", \"idEdificio\"",
                new Object[]{idIntento}, m).stream().map(e -> new Edificio(e.id(), e.idIntento(), e.nombre(), e.ubicacion(), e.fecha())).toList();
    }

    @Override
    public List<Zona> zonasDeIntento(UUID idIntento) {
        RowMapper<ZonaIntentoEntity> m = (rs, n) -> new ZonaIntentoEntity(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant());
        return jdbc.query("SELECT \"idZona\", \"idIntento\", \"nombre\", \"descripcion\", \"fechaCreacion\" FROM public.\"ZonaIntento\" WHERE \"idIntento\" = ? ORDER BY \"fechaCreacion\", \"idZona\"",
                new Object[]{idIntento}, m).stream().map(e -> new Zona(e.id(), e.idIntento(), e.nombre(), e.descripcion(), e.fecha())).toList();
    }

    @Override
    public void insertar(Nota nota) {
        jdbc.update("INSERT INTO public.\"NotaIntento\" (\"idNota\", \"idIntento\", \"titulo\", \"texto\", \"fechaCreacion\") VALUES (?, ?, ?, ?, ?)",
                nota.idNota(), nota.idIntento(), nota.titulo(), nota.texto(), Timestamp.from(nota.fechaCreacion()));
    }

    @Override
    public void insertar(Pista pista) {
        jdbc.update("INSERT INTO public.\"PistaIntento\" (\"idPista\", \"idIntento\", \"descripcion\", \"fechaCreacion\") VALUES (?, ?, ?, ?)",
                pista.idPista(), pista.idIntento(), pista.descripcion(), Timestamp.from(pista.fechaCreacion()));
    }

    @Override
    public void insertar(Edificio edificio) {
        jdbc.update("INSERT INTO public.\"EdificioIntento\" (\"idEdificio\", \"idIntento\", \"nombre\", \"ubicacion\", \"fechaCreacion\") VALUES (?, ?, ?, ?, ?)",
                edificio.idEdificio(), edificio.idIntento(), edificio.nombre(), edificio.ubicacion(), Timestamp.from(edificio.fechaCreacion()));
    }

    @Override
    public void insertar(Zona zona) {
        jdbc.update("INSERT INTO public.\"ZonaIntento\" (\"idZona\", \"idIntento\", \"nombre\", \"descripcion\", \"fechaCreacion\") VALUES (?, ?, ?, ?, ?)",
                zona.idZona(), zona.idIntento(), zona.nombre(), zona.descripcion(), Timestamp.from(zona.fechaCreacion()));
    }
}
