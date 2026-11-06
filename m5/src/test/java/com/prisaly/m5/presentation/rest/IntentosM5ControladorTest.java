package com.prisaly.m5.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ReferenciaSesion;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.ResumenIntento.TiemposPersistidos;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Comprobación técnica del adaptador REST preparatorio, sin servidor ni registro MVC. No sustituye
 * las pruebas funcionales de Postman exigidas para el entregable.
 */
@DisplayName("IntentosM5Controlador (POST sin registro) e IntentosEstudianteM5Controlador (GET/PUT registrados)")
class IntentosM5ControladorTest {

    private static final UUID SESION = UUID.randomUUID();
    private static final UUID INTENTO = UUID.randomUUID();
    private static final UUID PREGUNTA = UUID.randomUUID();
    private static final UUID OPCION = UUID.randomUUID();
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante("E-ana");
    private static final ReferenciaConvocatoria CONVOCATORIA = new ReferenciaConvocatoria("C-ana");
    private static final Instant INICIO = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant LIMITE = Instant.parse("2026-11-04T15:30:00Z");
    private static final Instant AHORA = Instant.parse("2026-11-04T15:05:00Z");

    private boolean reanudado;
    private final List<String> llamadas = new ArrayList<>();

    private final TitularidadIntentos titularidad = new TitularidadIntentos() {
        @Override
        public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
            return Titularidad.PROPIO;
        }

        @Override
        public Titularidad deConvocatoria(ReferenciaConvocatoria convocatoria, IdentificadorEstudiante estudiante) {
            return CONVOCATORIA.equals(convocatoria) ? Titularidad.PROPIO : Titularidad.AJENO;
        }

        @Override
        public Optional<ReferenciaConvocatoria> convocatoriaEnSesion(ReferenciaSesion sesion, IdentificadorEstudiante estudiante) {
            return SESION.toString().equals(sesion.valor()) ? Optional.of(CONVOCATORIA) : Optional.empty();
        }
    };

    private final AccesoProtegidoParticipacion proxy = new AccesoProtegidoParticipacion(
            new ControlAccesoIntentos(() -> Optional.of(ANA), titularidad), new Operaciones());
    private final IntentosM5Controlador controlador = new IntentosM5Controlador(proxy);
    private final IntentosEstudianteM5Controlador registrado = new IntentosEstudianteM5Controlador(proxy);

    @Test
    @DisplayName("DC-DD02-01: solo GET y PUT quedan registrados; los POST no")
    void soloGetYPutRegistrados() {
        assertTrue(IntentosEstudianteM5Controlador.class.isAnnotationPresent(
                org.springframework.web.bind.annotation.RestController.class));
        assertArrayEquals(new String[] {"/v1/estudiantes"},
                IntentosEstudianteM5Controlador.class.getAnnotation(RequestMapping.class).value());
        List<String> metodos = Stream.of(IntentosEstudianteM5Controlador.class.getDeclaredMethods())
                .flatMap(m -> Stream.of(m.getAnnotations()))
                .map(Annotation::annotationType).map(Class::getSimpleName)
                .filter(n -> n.endsWith("Mapping")).sorted().toList();
        assertEquals(List.of("GetMapping", "PutMapping"), metodos);
        List<String> post = Stream.of(IntentosM5Controlador.class.getDeclaredMethods())
                .flatMap(m -> Stream.of(m.getAnnotations()))
                .map(Annotation::annotationType).map(Class::getSimpleName)
                .filter(n -> n.endsWith("Mapping")).toList();
        assertEquals(List.of("PostMapping", "PostMapping"), post);
    }

    @Test
    @DisplayName("no se registra en MVC: sin @RestController, @Controller ni @Component")
    void sinRegistroMvc() {
        List<String> anotaciones = Stream.of(IntentosM5Controlador.class.getAnnotations())
                .map(Annotation::annotationType).map(Class::getSimpleName).toList();
        assertFalse(anotaciones.contains("RestController"));
        assertFalse(anotaciones.contains("Controller"));
        assertFalse(anotaciones.contains("Component"));
        assertArrayEquals(new String[] {"/v1/estudiantes"},
                IntentosM5Controlador.class.getAnnotation(RequestMapping.class).value());
    }

    @Test
    @DisplayName("D04: crear responde 201 y reanudar 200, con los tiempos persistidos")
    void creaYReanuda() {
        var creado = controlador.iniciar(SESION);
        assertEquals(201, creado.getStatusCode().value());
        assertFalse(creado.getBody().reanudado());
        assertEquals(INICIO, creado.getBody().intento().inicio());
        assertEquals(LIMITE, creado.getBody().intento().limiteTemporal());

        reanudado = true;
        var reanudacion = controlador.iniciar(SESION);
        assertEquals(200, reanudacion.getStatusCode().value());
        assertTrue(reanudacion.getBody().reanudado());
        assertEquals(INICIO, reanudacion.getBody().intento().inicio());
        assertEquals(List.of("iniciar C-ana", "iniciar C-ana"), llamadas);
    }

    @Test
    @DisplayName("D05: la consulta devuelve estado, tiempos, revisión y hora del servidor")
    void consulta() {
        var antes = Instant.now();
        var vista = registrado.consultar(INTENTO);
        assertEquals("EnCurso", vista.estado());
        assertFalse(vista.ahoraServidor().isBefore(antes));
        assertFalse(vista.ahoraServidor().isAfter(Instant.now()));
        assertEquals(1, vista.respuestas().size());
        assertEquals(3, vista.respuestas().get(0).revision());
    }

    @Test
    @DisplayName("D06: el guardado envía la revisión esperada y devuelve la confirmada")
    void guardado() {
        var guardada = registrado.guardar(INTENTO, PREGUNTA, new IntentosM5Dto.Guardar(OPCION, 2));
        assertEquals(3, guardada.revision());
        assertEquals(List.of("guardar " + PREGUNTA + " rev 2"), llamadas);
    }

    @Test
    @DisplayName("D08: el cierre voluntario usa el motivo asignado por el servidor")
    void cierreVoluntario() {
        var cierre = controlador.finalizar(INTENTO);
        assertEquals("ENTREGA_ESTUDIANTE", cierre.motivoFinalizacion());
        assertEquals("Finalizado", cierre.estado());
        assertEquals(List.of("finalizar ENTREGA_ESTUDIANTE"), llamadas);
    }

    @Test
    @DisplayName("exige el acceso protegido")
    void exigeProxy() {
        assertThrows(IllegalArgumentException.class, () -> new IntentosM5Controlador(null));
        assertThrows(IllegalArgumentException.class, () -> new IntentosEstudianteM5Controlador(null));
    }

    private ResumenIntento resumen(EstadoIntento estado, Optional<MotivoFinalizacion> motivo, Instant fin) {
        return new ResumenIntento(new ReferenciaIntento(INTENTO.toString()), estado, motivo,
                List.of(new Respuesta(new ReferenciaPregunta(PREGUNTA.toString()), new ValorRespuesta(OPCION.toString()), 3)),
                Optional.of(new TiemposPersistidos(INICIO, LIMITE, fin)));
    }

    private final class Operaciones implements OperacionesParticipacion {
        @Override
        public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
            llamadas.add("iniciar " + convocatoria.valor());
            return new ResultadoInicio(resumen(EstadoIntento.EN_CURSO, Optional.empty(), null), INICIO, LIMITE, reanudado);
        }

        @Override
        public ResumenIntento consultarIntento(ReferenciaIntento intento) {
            return resumen(EstadoIntento.EN_CURSO, Optional.empty(), null);
        }

        @Override
        public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
            throw new AssertionError("el adaptador REST usa el guardado con revisión");
        }

        @Override
        public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
            throw new AssertionError("el adaptador REST usa el guardado con revisión");
        }

        @Override
        public Respuesta guardarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor,
                int revisionEsperada) {
            llamadas.add("guardar " + pregunta.valor() + " rev " + revisionEsperada);
            return new Respuesta(pregunta, valor, revisionEsperada + 1);
        }

        @Override
        public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
            llamadas.add("finalizar " + motivo.name());
            return resumen(EstadoIntento.FINALIZADO, Optional.of(motivo), AHORA);
        }
    }
}
