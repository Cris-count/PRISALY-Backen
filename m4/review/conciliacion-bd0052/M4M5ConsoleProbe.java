import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.prisaly.m1.application.port.*;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m4.api.*;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.*;
import com.prisaly.m5.application.*;
import com.prisaly.m5.application.acceso.*;
import com.prisaly.m5.presentation.TraductorErroresM5;

class M4M5ConsoleProbe {
 static int checks;
 static void check(boolean condition, String message) {
  if (!condition) throw new AssertionError(message);
  checks++; System.out.println("CONTRATO OK " + message);
 }
 static <T extends Throwable> T expect(Class<T> type, Runnable action) {
  try { action.run(); } catch (Throwable ex) {
   if (type.isInstance(ex)) return type.cast(ex);
   throw new AssertionError("Excepcion inesperada: " + ex.getClass().getName(), ex);
  }
  throw new AssertionError("Se esperaba " + type.getName());
 }
 static TitularidadIntentos titularidad(JdbcTemplate jdbc, ProgramacionAutenticadaPort m4) {
  return referencia -> {
   UUID id;
   try { id = UUID.fromString(referencia.valor()); } catch (IllegalArgumentException ex) { return Optional.empty(); }
   var links = jdbc.query("SELECT p.\"idConvocatoria\" FROM \"Intento\" i JOIN \"Participacion\" p ON p.\"idParticipacion\"=i.\"idParticipacion\" WHERE i.\"idIntento\"=?",
    (rs,row) -> rs.getObject(1,UUID.class),id);
   if (links.isEmpty()) return Optional.empty();
   return m4.consultarConvocatoria(links.get(0)).map(c -> new IdentificadorEstudiante(c.estudianteId().toString()));
  };
 }
 public static void main(String[] args) throws Exception {
  try (var pg = new PostgreSQLContainer("postgres:16.15")) {
   pg.start();
   Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())
    .locations("filesystem:"+args[0]).load().migrate();
   var jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()));
   UUID docente=UUID.randomUUID(), usuario=UUID.randomUUID(), estudiante=UUID.randomUUID(), caso=UUID.randomUUID();
   UUID sesion=UUID.randomUUID(), convocatoria=UUID.randomUUID(), participacion=UUID.randomUUID(), intento=UUID.randomUUID();
   jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'Fixture','Contrato consola')");
   jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,1,'Docente','docente@example.invalid')",docente);
   jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,1,'Estudiante','estudiante@example.invalid')",usuario);
   jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)",estudiante,usuario);
   jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,'Caso',600,'Publicado')",caso,docente);
   jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\",\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\",\"estado\") VALUES (?,?,?,'Consola',now()+interval '1 day',now()+interval '2 days',1800,'EnCurso')",sesion,caso,docente);
   jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)",convocatoria,sesion,estudiante);
   jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)",participacion,convocatoria);
   jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\") VALUES (?,?,1,now(),now()+interval '30 minutes','EnCurso')",intento,participacion);
   var actual = new AtomicReference<AuthenticatedStudent>(new AuthenticatedStudent(usuario,estudiante));
   StudentAccessPort identidadFixture = () -> {
    if (actual.get()==null) throw new IdentityDomainException("UNAUTHENTICATED","Fixture sin identidad");
    return actual.get();
   };
   var m4 = new ProgramacionAutenticadaM1Adapter(identidadFixture,new ProgramacionParticipacionJdbcAdapter(jdbc));
   IdentidadEstudianteActual m5Identidad = () -> Optional.ofNullable(actual.get())
    .map(s -> new IdentificadorEstudiante(s.studentId().toString()));
   var control = new ControlAccesoIntentos(m5Identidad,titularidad(jdbc,m4));
   var referencia = new ReferenciaIntento(intento.toString());
   check(control.verificar(referencia).valor().equals(estudiante.toString()),"titular correcto: permitido por control de titularidad, no elegibilidad temporal");
   var ausente = expect(IntentoNoEncontradoException.class,()->control.verificar(new ReferenciaIntento(UUID.randomUUID().toString())));
   check(TraductorErroresM5.traducir(ausente).orElseThrow().estado()==404,"intento inexistente: 404");
   actual.set(new AuthenticatedStudent(UUID.randomUUID(),UUID.randomUUID()));
   var ajeno = expect(IntentoNoEncontradoException.class,()->control.verificar(referencia));
   check(TraductorErroresM5.traducir(ajeno).orElseThrow().estado()==404,"estudiante ajeno: vacio M4 produce 404, no el 403 provisional");
   actual.set(null);
   var sinIdentidad = expect(EstudianteNoIdentificadoException.class,()->control.verificar(referencia));
   check(TraductorErroresM5.traducir(sinIdentidad).orElseThrow().estado()==401,"sin identidad: 401 antes de consultar contexto");
   actual.set(new AuthenticatedStudent(usuario,estudiante));
   jdbc.execute("ALTER TABLE \"Sesion\" RENAME TO \"SesionOculta\"");
   try {
    var noDisponible=expect(ConsultaProgramacionNoDisponibleException.class,()->control.verificar(referencia));
    check(TraductorErroresM5.traducir(noDisponible).isEmpty(),"BRECHA CONFIRMADA: error tecnico M4 no esta traducido a 503 por M5");
   } finally { jdbc.execute("ALTER TABLE \"SesionOculta\" RENAME TO \"Sesion\""); }
   var dd02 = new ProgramacionAutenticadaM1Adapter(new DenyingStudentAccessService(),new ProgramacionParticipacionJdbcAdapter(jdbc));
   var denegado = expect(ReglaNegocioException.class,()->dd02.consultarSesion(sesion));
   check("M4-PROVEEDOR-NO-DISPONIBLE".equals(denegado.getCodigo()),"DD-02: proveedor productivo actual M1 mantiene denegacion segura");
   System.out.println("CONTRATO RESULTADO: "+checks+" comprobaciones; ensamblaje temporal, identidad fixture, sin iniciar/reanudar ni conectar fachadas productivas.");
  }
 }
}
