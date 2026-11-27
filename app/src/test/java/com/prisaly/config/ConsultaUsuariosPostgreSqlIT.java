package com.prisaly.config;
import com.prisaly.m1.infrastructure.persistence.ConsultaUsuariosJdbcAdapter;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ConsultaUsuariosPostgreSqlIT {
 @Test void proyeccionRealCompatibleConV013YV020YBusquedaLiteralAcotada(){
  try(var pg=new PostgreSQLContainer("postgres:16.15")){
   pg.start();var url=pg.getJdbcUrl();var user=pg.getUsername();var pw=pg.getPassword();
   Flyway.configure().dataSource(url,user,pw).locations("classpath:db/migration").load().migrate();
   var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,user,pw));
   Integer rol=jdbc.queryForObject("INSERT INTO \"Rol\" (\"nombre\",\"descripcion\") VALUES ('DOCENTE','QA') RETURNING \"idRol\"",Integer.class);
   var id=UUID.randomUUID();jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",id,rol,"Prueba Consulta","consulta@prisaly.test");
   var adapter=new ConsultaUsuariosJdbcAdapter(jdbc);var rows=adapter.buscar("CONSULTA","DOCENTE",1,0);
   assertEquals(1,rows.size());assertEquals(id,rows.getFirst().idUsuario());assertFalse(rows.getFirst().credencialPersonalConfigurada());assertEquals("NO_DISPONIBLE",rows.getFirst().estadoInvitacion());
   assertTrue(adapter.buscar("%",null,25,0).isEmpty());assertTrue(adapter.buscar("' OR 1=1 --",null,25,0).isEmpty());assertTrue(adapter.buscar("","ESTUDIANTE",25,0).isEmpty());assertTrue(adapter.buscar("",null,25,1).isEmpty());
   Flyway.configure().dataSource(url,user,pw).locations("classpath:db/migration","classpath:db/d05-propuesta").load().migrate();
   assertEquals("NO_EMITIDA",adapter.buscar("",null,25,0).getFirst().estadoInvitacion());
   jdbc.update("INSERT INTO \"ActivacionDocente\" (\"hashToken\",\"idUsuario\",\"idEmisor\",\"emitidoEn\",\"expiraEn\") VALUES (?,?,?,clock_timestamp(),clock_timestamp()+interval '1 hour')","a".repeat(64),id,id);
   assertEquals("VIGENTE",adapter.buscar("",null,25,0).getFirst().estadoInvitacion());
   jdbc.update("UPDATE \"ActivacionDocente\" SET \"revocadoEn\"=clock_timestamp() WHERE \"idUsuario\"=?",id);
   assertEquals("REVOCADA",adapter.buscar("",null,25,0).getFirst().estadoInvitacion());
  }
 }
}
