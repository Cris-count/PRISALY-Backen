package com.prisaly.m1.infrastructure.persistence;
import com.prisaly.m1.application.port.ConsultaUsuariosPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class ConsultaUsuariosJdbcAdapter implements ConsultaUsuariosPort {
    private final JdbcTemplate jdbc;
    public ConsultaUsuariosJdbcAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override public List<UsuarioResumen> buscar(String texto,String rol,int limite,int offset) {
        // V013 también funciona: la ausencia de V020 nunca se presenta como invitación emitida.
        boolean invitaciones=Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.\"ActivacionDocente\"') IS NOT NULL",Boolean.class));
        String estado=invitaciones?"CASE WHEN r.\"nombre\"<>'DOCENTE' THEN 'NO_APLICA' WHEN a.\"idUsuario\" IS NULL THEN 'NO_EMITIDA' WHEN a.\"consumidoEn\" IS NOT NULL THEN 'CONSUMIDA' WHEN a.\"revocadoEn\" IS NOT NULL THEN 'REVOCADA' WHEN a.\"expiraEn\"<=clock_timestamp() THEN 'VENCIDA' ELSE 'VIGENTE' END":"'NO_DISPONIBLE'";
        String join=invitaciones?" LEFT JOIN LATERAL (SELECT \"idUsuario\",\"consumidoEn\",\"revocadoEn\",\"expiraEn\" FROM \"ActivacionDocente\" WHERE \"idUsuario\"=u.\"idUsuario\" ORDER BY \"emitidoEn\" DESC LIMIT 1) a ON true ":" ";
        return jdbc.query("SELECT u.\"idUsuario\",e.\"idEstudiante\",u.\"nombre\",u.\"correo\",r.\"nombre\" AS rol,u.\"estado\",(u.\"passwordHash\" IS NOT NULL) AS credencial,"+estado+" AS invitacion FROM \"Usuario\" u JOIN \"Rol\" r ON r.\"idRol\"=u.\"idRol\" LEFT JOIN \"Estudiante\" e ON e.\"idUsuario\"=u.\"idUsuario\" "+join+" WHERE POSITION(lower(?) IN lower(u.\"nombre\" || ' ' || u.\"correo\"))>0 AND (CAST(? AS text) IS NULL OR r.\"nombre\"=?) ORDER BY lower(u.\"nombre\"),u.\"idUsuario\" LIMIT ? OFFSET ?",
            (rs,n)->new UsuarioResumen(rs.getObject("idUsuario",UUID.class),rs.getObject("idEstudiante",UUID.class),rs.getString("nombre"),rs.getString("correo"),rs.getString("rol"),rs.getString("estado"),rs.getBoolean("credencial"),rs.getString("invitacion")),texto,rol,rol,limite,offset);
    }
}
