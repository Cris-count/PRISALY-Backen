package com.prisaly.m1.application.port;

import java.util.Optional;

/** Token opaco de la petición actual. Vacío si no hay cookie de estudiante. */
public interface TokenSesionEstudianteActual {

    String ATRIBUTO = "com.prisaly.m1.tokenSesionEstudiante";

    Optional<String> actual();
}
