package com.prisaly.m5.infrastructure;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m3.api.GuionNarrativoEstudiantilPort;
import com.prisaly.m5.application.fase.FasesIntento;
import com.prisaly.m5.application.fase.PoliticasFaseAprobadas;
import com.prisaly.m5.infrastructure.contenido.GuionesDelIntentoDesdeM3;
import com.prisaly.m5.infrastructure.persistencia.RepositorioFasesJdbc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import com.prisaly.m5.application.ContenidoIntentoNoDisponibleException;
import com.prisaly.m5.application.port.ConsultaContenidoIntento;
import com.prisaly.m5.infrastructure.contenido.ConsultaContenidoIntentoTransaccional;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.inicio.ElegibilidadNoRatificada;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;
import com.prisaly.m5.application.inicio.LimiteTemporalNoRatificado;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.IniciarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.infrastructure.programacion.CoordinacionInicioDesdeM4;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

/**
 * Ensambla los casos de uso de M5 detrás de su proxy de protección.
 *
 * <p>El único bean de operaciones que M5 publica es {@link AccesoProtegidoParticipacion}. La
 * fachada de aplicación y su decorador transaccional se construyen aquí dentro y <b>no</b> se
 * registran como beans. Así ningún componente puede inyectar una variante sin control de acceso: no
 * hay un bean ambiguo que elegir por error.
 *
 * <p>Falla de forma cerrada en el arranque: sin un bean de {@link IdentidadEstudianteActual} (M1) o
 * de {@link TitularidadIntentos} (M4), el contexto no se crea.
 *
 * <p>El proxy verifica identidad/titularidad antes de delegar. D-06 revalida la convocatoria dentro
 * de la transacción de inicio y reserva Sesion y Convocatoria antes de Participacion/Intento. Las
 * políticas académicas no ratificadas siguen denegando el primer inicio.
 *
 * <p><b>Primer inicio:</b> la elegibilidad y el límite temporal siguen pendientes de Dirección
 * Técnica. Este ensamblado usa {@link ElegibilidadNoRatificada} y {@link
 * LimiteTemporalNoRatificado}, que no son beans y no pueden sustituirse desde fuera: el primer
 * inicio falla de forma segura sin escribir, y la reanudación de un intento en curso sí funciona.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionParticipacion {

    /**
     * DC-BE-SIM-B1-B2A-24 (Incremento B, candidato): fases académicas del intento. Desactivado por defecto: con
     * {@code false} no se construye nada de las fases y M5 no ejecuta ninguna sentencia sobre {@code "FaseIntento"}.
     * Activarlo exige el esquema QA {@code bd0052-v013-sim-ab} o {@code bd0052-v013-dd02b-sim-ab} y el guion narrativo de
     * M3 ({@code prisaly.m3.narrativa.publicacion.enabled=true}); no publica ninguna ruta ni ratifica ninguna decisión.
     */
    public static final String PROPIEDAD_FASES = "prisaly.m5.exploracion.fases.enabled";
    static final String CODIGO_FASES = "M5-FASES-REQUIEREN-NARRATIVA";

    @Bean
    AccesoProtegidoParticipacion accesoProtegidoParticipacion(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad,
            ObjectProvider<ProgramacionInicioCoordinadoPort> coordinacion,
            ObjectProvider<ProgramacionParticipacionPort> programacion,
            ObjectProvider<ContenidoCasoEstudiantilPort> contenido,
            ObjectProvider<GuionNarrativoEstudiantilPort> guiones,
            ObjectProvider<JdbcTemplate> jdbc,
            @Value("${" + PROPIEDAD_FASES + ":false}") boolean fasesHabilitadas) {
        return ensamblar(
                repositorio,
                participaciones,
                transacciones,
                identidad,
                titularidad,
                new ElegibilidadNoRatificada(),
                new LimiteTemporalNoRatificado(),
                new CoordinacionInicioDesdeM4(
                        coordinacion.getIfAvailable(
                                () ->
                                        (convocatoria, estudiante) -> {
                                            throw new ConsultaProgramacionNoDisponibleException();
                                        }),
                        identidad),
                consultaContenido(repositorio, programacion, contenido),
                fases(fasesHabilitadas, identidad, programacion.getIfAvailable(), guiones.getIfAvailable(),
                        jdbc.getIfAvailable()));
    }

    /**
     * Fases académicas candidatas. Deshabilitadas: vacío (M5 exactamente como antes). Habilitadas sin el guion de M3,
     * sin la consulta de M4 o sin JDBC: el arranque falla cerrado. Las decisiones D-SIM-2, D-SIM-3, D-SIM-4 y D-SIM-10
     * son las resueltas por Dirección Central ({@link PoliticasFaseAprobadas}, DC-BE-SIM-B2B-B3-32), en este único lugar.
     */
    static Optional<FasesIntento> fases(boolean habilitadas, IdentidadEstudianteActual identidad,
            ProgramacionParticipacionPort programacion, GuionNarrativoEstudiantilPort guiones, JdbcTemplate jdbc) {
        if (!habilitadas) {
            return Optional.empty();
        }
        if (guiones == null || programacion == null || jdbc == null) {
            throw new IllegalStateException(CODIGO_FASES + ": " + PROPIEDAD_FASES + "=true exige el guion narrativo de M3"
                    + " (prisaly.m3.narrativa.publicacion.enabled=true), la consulta de programación de M4 y JDBC");
        }
        return Optional.of(new FasesIntento(new RepositorioFasesJdbc(jdbc),
                new GuionesDelIntentoDesdeM3(guiones, programacion, identidad), new PoliticasFaseAprobadas()));
    }

    private static ConsultaContenidoIntento consultaContenido(RepositorioIntentos repositorio,
            ObjectProvider<ProgramacionParticipacionPort> programacion,
            ObjectProvider<ContenidoCasoEstudiantilPort> contenido) {
        var m4 = programacion.getIfAvailable();
        if (m4 == null) return (intento, estudiante) -> { throw new ConsultaProgramacionNoDisponibleException(); };
        var m3 = contenido.getIfAvailable();
        if (m3 == null) return (intento, estudiante) -> { throw new ContenidoIntentoNoDisponibleException(); };
        return new ConsultaContenidoIntentoTransaccional(repositorio, m4, m3);
    }

    static AccesoProtegidoParticipacion ensamblar(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad,
            ElegibilidadInicio elegibilidad,
            LimiteTemporalInicio limiteTemporal,
            Consumer<ReferenciaConvocatoria> coordinacion) {
        return ensamblar(repositorio, participaciones, transacciones, identidad, titularidad,
                elegibilidad, limiteTemporal, coordinacion,
                (intento, estudiante) -> { throw new ContenidoIntentoNoDisponibleException(); });
    }

    static AccesoProtegidoParticipacion ensamblar(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad,
            ElegibilidadInicio elegibilidad,
            LimiteTemporalInicio limiteTemporal,
            Consumer<ReferenciaConvocatoria> coordinacion,
            ConsultaContenidoIntento contenido) {
        return ensamblar(repositorio, participaciones, transacciones, identidad, titularidad,
                elegibilidad, limiteTemporal, coordinacion, contenido, Optional.empty());
    }

    /** DC-BE-SIM-B1-B2A-24: el mismo ensamblado con fases académicas candidatas (vacío = sin fases). */
    static AccesoProtegidoParticipacion ensamblar(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad,
            ElegibilidadInicio elegibilidad,
            LimiteTemporalInicio limiteTemporal,
            Consumer<ReferenciaConvocatoria> coordinacion,
            ConsultaContenidoIntento contenido,
            Optional<FasesIntento> fases) {
        ParticipacionFacade fachada =
                new ParticipacionFacade(
                        new ConsultarIntentoServicio(repositorio),
                        new RegistrarRespuestaServicio(repositorio),
                        new ActualizarRespuestaServicio(repositorio),
                        new FinalizarIntentoServicio(repositorio),
                        new IniciarIntentoServicio(
                                participaciones, repositorio, elegibilidad, limiteTemporal),
                        fases);
        // D-SIM-2 (DC-BE-SIM-B2B-B3-32): con fases, toda lectura de preguntas (GET /contenido y la completitud P4) pasa
        // antes por la fase del intento, en la misma instantánea y después de la titularidad. Sin fases, sin cambios.
        ConsultaContenidoIntento preguntas = fases.<ConsultaContenidoIntento>map(f -> (intento, estudiante) -> {
            f.exigirPreguntasVisibles(intento);
            return contenido.consultar(intento, estudiante);
        }).orElse(contenido);
        TransactionTemplate lecturaContenido = new TransactionTemplate(transacciones);
        lecturaContenido.setReadOnly(true);
        lecturaContenido.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        lecturaContenido.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(identidad, titularidad),
                new FachadaParticipacionTransaccional(fachada, transacciones, coordinacion),
                preguntas,
                accion -> java.util.Objects.requireNonNull(lecturaContenido.execute(estado -> accion.get())),
                fases.isPresent()); // QA-B25-01: P1–P4 solo existen si se construyeron las fases
    }
}
