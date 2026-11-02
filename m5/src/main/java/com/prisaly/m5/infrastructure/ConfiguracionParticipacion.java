package com.prisaly.m5.infrastructure;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
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

    @Bean
    AccesoProtegidoParticipacion accesoProtegidoParticipacion(
            RepositorioIntentos repositorio,
            RepositorioParticipaciones participaciones,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad,
            ObjectProvider<ProgramacionInicioCoordinadoPort> coordinacion) {
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
                        identidad));
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
        ParticipacionFacade fachada =
                new ParticipacionFacade(
                        new ConsultarIntentoServicio(repositorio),
                        new RegistrarRespuestaServicio(repositorio),
                        new ActualizarRespuestaServicio(repositorio),
                        new FinalizarIntentoServicio(repositorio),
                        new IniciarIntentoServicio(
                                participaciones, repositorio, elegibilidad, limiteTemporal));
        return new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(identidad, titularidad),
                new FachadaParticipacionTransaccional(fachada, transacciones, coordinacion));
    }
}
