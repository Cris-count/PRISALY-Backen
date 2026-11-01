package com.prisaly.m5.application.acceso;

import java.util.Optional;

/**
 * Puerto PROVISIONAL de M5 (lado consumidor): el estudiante autenticado en
 * la operación en curso.
 *
 * <p>Su implementación prevista adapta el contrato publicado por M1,
 * {@code com.prisaly.m1.application.port.StudentAccessPort}
 * ({@code requireCurrentStudent()} → {@code AuthenticatedStudent(userId,
 * studentId)}). El contrato ya llega por develop (M5 depende de M4, que
 * depende de M1), pero el adaptador sigue como parche en {@code m5/docs/parches}
 * hasta que se autorice aplicarlo. Reglas: la identidad sale solo del servidor, se usa
 * {@code studentId}, y cualquier denegación de M1 devuelve vacío. Mientras
 * DD-02 siga abierta, M1 deniega siempre ({@code DenyingStudentAccessService}).
 */
public interface IdentidadEstudianteActual {

    /** Estudiante autenticado; vacío si no hay uno. */
    Optional<IdentificadorEstudiante> actual();
}
