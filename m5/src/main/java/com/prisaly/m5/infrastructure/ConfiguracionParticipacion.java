package com.prisaly.m5.infrastructure;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ConvocatoriasSesionPort;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.application.port.CierreParticipacionesPort;
import com.prisaly.m5.infrastructure.programacion.CierreIntentosVencidosDesdeM5;
import com.prisaly.m5.infrastructure.programacion.ElegibilidadInicioDesdeM4;
import com.prisaly.m5.infrastructure.programacion.LimiteTemporalDesdeM4;
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
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.IniciarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.infrastructure.programacion.CoordinacionInicioDesdeM4;

import org.springframework.beans.factory.ObjectProvider;
import com.prisaly.m5.application.port.PortInvestigacion;
import com.prisaly.m5.application.servicio.InvestigacionServicio;
import com.prisaly.m5.infrastructure.persistencia.RepositorioInvestigacionJpa;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * de la transacción de inicio y reserva Sesion y Convocatoria antes de Participacion/Intento.
 *
 * <p><b>Primer inicio (DC-BE-M5-CIERRE-IMPLEMENTACION-45):</b> las políticas ratificadas por Dirección Central
 * se ensamblan solo aquí y no son beans, así que no pueden sustituirse desde fuera:
 * {@link ElegibilidadInicioDesdeM4} (E1) y {@link LimiteTemporalDesdeM4} (L1 y RF-22), ambas sobre el contrato
 * {@link ProgramacionAutenticadaPort} de M4. Sin ese contrato, el primer inicio falla cerrado (503). La reanudación
 * no consulta ninguna de las dos y nunca amplía el límite. La repetición (DD-14-a) la sigue garantizando PostgreSQL
 * (V003–V007): un intento por estudiante y caso. La publicación de los POST sigue tras su interruptor.
 *
 * <p><b>V1-c:</b> {@link #cierreIntentosVencidosM5} provee a M4 el cierre por tiempo de los intentos vencidos al
 * cerrar el docente la sesión, con la misma fachada (y el mismo punto D-SIM-4) que la finalización.
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

    public static final String PROPIEDAD_INVESTIGACION_HTTP = "prisaly.m5.investigacion.http.enabled";

    @Bean
    @ConditionalOnProperty(name = PROPIEDAD_INVESTIGACION_HTTP, havingValue = "true")
    PortInvestigacion portInvestigacion(JdbcTemplate jdbc) {
        return new RepositorioInvestigacionJpa(jdbc);
    }

    @Bean
    @ConditionalOnProperty(name = PROPIEDAD_INVESTIGACION_HTTP, havingValue = "true")
    InvestigacionServicio investigacionServicio(AccesoProtegidoParticipacion acceso, PortInvestigacion port) {
        return new InvestigacionServicio(acceso, port);
    }

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
            ObjectProvider<ProgramacionAutenticadaPort> autenticada,
            @Value("${" + PROPIEDAD_FASES + ":false}") boolean fasesHabilitadas) {
        return ensamblar(
                repositorio,
                participaciones,
                transacciones,
                identidad,
                titularidad,
                elegibilidad(autenticada.getIfAvailable()),
                limiteTemporal(autenticada.getIfAvailable()),
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
     * V1-c (DC-BE-M5-CIERRE-IMPLEMENTACION-45): proveedor del puerto de M4 para el cierre docente de sesiones. Usa una
     * fachada con las mismas políticas y fases que {@link #accesoProtegidoParticipacion}, sin proxy estudiantil: lo
     * invoca M4 dentro de su transacción, después de autorizar al docente propietario de la sesión. Sin el contrato de
     * convocatorias de M4 o sin JDBC, el cierre de sesión falla cerrado.
     */
    @Bean
    CierreParticipacionesPort cierreIntentosVencidosM5(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            IdentidadEstudianteActual identidad,
            ObjectProvider<ConvocatoriasSesionPort> convocatorias,
            ObjectProvider<ProgramacionAutenticadaPort> autenticada,
            ObjectProvider<ProgramacionParticipacionPort> programacion,
            ObjectProvider<GuionNarrativoEstudiantilPort> guiones,
            ObjectProvider<JdbcTemplate> jdbc,
            @Value("${" + PROPIEDAD_FASES + ":false}") boolean fasesHabilitadas) {
        ConvocatoriasSesionPort deLaSesion = convocatorias.getIfAvailable();
        JdbcTemplate plantilla = jdbc.getIfAvailable();
        if (deLaSesion == null || plantilla == null) {
            return sesionId -> {
                throw new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE",
                        "El cierre de intentos de M5 no está disponible");
            };
        }
        ParticipacionFacade fachada = fachada(repositorio, participaciones,
                elegibilidad(autenticada.getIfAvailable()), limiteTemporal(autenticada.getIfAvailable()),
                fases(fasesHabilitadas, identidad, programacion.getIfAvailable(), guiones.getIfAvailable(), plantilla));
        return new CierreIntentosVencidosDesdeM5(deLaSesion, plantilla, fachada::cerrarSiVencido);
    }

    /** E1 ratificada; sin el contrato de M4 falla cerrado (503), nunca autoriza. */
    static ElegibilidadInicio elegibilidad(ProgramacionAutenticadaPort autenticada) {
        if (autenticada == null) {
            return convocatoria -> {
                throw new ConsultaProgramacionNoDisponibleException();
            };
        }
        return new ElegibilidadInicioDesdeM4(autenticada);
    }

    /** L1 y RF-22 ratificados; sin el contrato de M4 falla cerrado (503), nunca crea un intento. */
    static LimiteTemporalInicio limiteTemporal(ProgramacionAutenticadaPort autenticada) {
        if (autenticada == null) {
            return (convocatoria, inicio) -> {
                throw new ConsultaProgramacionNoDisponibleException();
            };
        }
        return new LimiteTemporalDesdeM4(autenticada);
    }

    static ParticipacionFacade fachada(RepositorioIntentos repositorio, RepositorioParticipaciones participaciones,
            ElegibilidadInicio elegibilidad, LimiteTemporalInicio limiteTemporal, Optional<FasesIntento> fases) {
        return new ParticipacionFacade(
                new ConsultarIntentoServicio(repositorio),
                new RegistrarRespuestaServicio(repositorio),
                new ActualizarRespuestaServicio(repositorio),
                new FinalizarIntentoServicio(repositorio),
                new IniciarIntentoServicio(participaciones, repositorio, elegibilidad, limiteTemporal),
                fases);
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
        ParticipacionFacade fachada = fachada(repositorio, participaciones, elegibilidad, limiteTemporal, fases);
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
