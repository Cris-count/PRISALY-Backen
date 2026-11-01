package com.prisaly.m5.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;

/**
 * Ensambla los casos de uso de M5 detrás de su proxy de protección.
 *
 * <p>El único bean de operaciones que M5 publica es
 * {@link AccesoProtegidoParticipacion}. La fachada de aplicación y su
 * decorador transaccional se construyen aquí dentro y <b>no</b> se registran
 * como beans. Así ningún componente puede inyectar una variante sin control
 * de acceso: no hay un bean ambiguo que elegir por error.
 *
 * <p>Falla de forma cerrada en el arranque: sin un bean de
 * {@link IdentidadEstudianteActual} (M1) o de {@link TitularidadIntentos} (M4),
 * el contexto no se crea.
 *
 * <p>La verificación ocurre antes de abrir la transacción del caso de uso. La
 * cadena de titularidad es inmutable en BD-005.2 (triggers de V004–V006 sobre
 * Intento, Participacion y Convocatoria), así que no cambia entre verificar y
 * operar. La elegibilidad académica sí puede cambiar, pero hoy no se evalúa
 * (ver m5/docs).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionParticipacion {

    @Bean
    AccesoProtegidoParticipacion accesoProtegidoParticipacion(
            RepositorioIntentos repositorio,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad) {
        return ensamblar(repositorio, transacciones, identidad, titularidad);
    }

    static AccesoProtegidoParticipacion ensamblar(
            RepositorioIntentos repositorio,
            PlatformTransactionManager transacciones,
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad) {
        ParticipacionFacade fachada = new ParticipacionFacade(
                new ConsultarIntentoServicio(repositorio),
                new RegistrarRespuestaServicio(repositorio),
                new ActualizarRespuestaServicio(repositorio),
                new FinalizarIntentoServicio(repositorio));
        return new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(identidad, titularidad),
                new FachadaParticipacionTransaccional(fachada, transacciones));
    }
}
