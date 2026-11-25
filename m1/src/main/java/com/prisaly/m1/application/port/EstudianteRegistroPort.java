package com.prisaly.m1.application.port;

import java.util.List;
import java.util.UUID;

/**
 * P-78 (DC-BE-D78-1-AUTORIZACION-CONTROLADA-79): the official {@code "Estudiante"} table (BD-005.2, V001) seen by the
 * administrative student registration. No schema change: only {@code idEstudiante}, {@code idUsuario} and
 * {@code fechaRegistro} are written; {@code apodo} and {@code avatarConfig} stay NULL.
 */
public interface EstudianteRegistroPort {

    record EstudianteRegistrado(UUID idUsuario, UUID idEstudiante, String nombre, String correo, String estado) { }

    /** Inserts the student row of an existing user inside the caller's write transaction. */
    void registrar(UUID idEstudiante, UUID idUsuario);

    /** Exact, case-insensitive lookup by email; only accounts that have a student row. */
    List<EstudianteRegistrado> buscarPorCorreo(String correo);
}
