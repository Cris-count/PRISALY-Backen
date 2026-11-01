package com.prisaly.m5.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;

/**
 * Ensambla el proxy de protección de M5: identidad de M1
 * ({@code IdentidadEstudianteDesdeM1}) y titularidad sobre M4
 * ({@code TitularidadIntentosDesdeM4}) delante del decorador transaccional.
 *
 * <p>La verificación ocurre antes de abrir la transacción del caso de uso. Es
 * seguro porque la cadena de titularidad es inmutable en BD-005.2 (triggers de
 * V004–V006 sobre Intento, Participacion y Convocatoria).
 *
 * <p>No publica endpoints. Sigue la regla de no exponer operaciones de
 * estudiante sin ARQ-008.3 ni DD-02.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionAccesoEstudiante {

    @Bean
    AccesoProtegidoParticipacion accesoProtegidoParticipacion(
            IdentidadEstudianteActual identidad,
            TitularidadIntentos titularidad,
            FachadaParticipacionTransaccional fachada) {
        return new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad, titularidad), fachada);
    }
}
