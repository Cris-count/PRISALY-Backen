package com.prisaly.m1.domain.rule;

import com.prisaly.m1.api.PermisosCaso;
import java.util.Set;

/** PD-01: qué permisos están controlados y qué rol puede asignarlos o revocarlos. */
public final class PoliticaPermisos {
    public static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    public static final String CODE_USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String CODE_PERMISSION_NOT_FOUND = "PERMISSION_NOT_FOUND";
    public static final String CODE_DUPLICATE_ASSIGNMENT = "DUPLICATE_ASSIGNMENT";
    public static final String CODE_ACTOR_REQUIRED = "PERMISSION_ACTOR_REQUIRED";
    public static final String CODE_ACTOR_INVALID = "PERMISSION_ACTOR_INVALID";
    public static final String CODE_ACTOR_NOT_ADMIN = "PERMISSION_ACTOR_NOT_ADMIN";
    public static final String CODE_STUDENT_NOT_ALLOWED = "PERMISSION_NOT_ALLOWED_FOR_STUDENT";
    public static final String CODE_SELF_ASSIGNMENT = "PERMISSION_SELF_ASSIGNMENT_NOT_ALLOWED";

    private static final Set<String> CONTROLADOS = Set.of(PermisosCaso.CONSULTAR_CASO, PermisosCaso.MODIFICAR_CASO,
            PermisosCaso.PUBLICAR_CASO, PermisosCaso.ARCHIVAR_CASO);
    private static final Set<String> OPERATIVOS = Set.of(PermisosCaso.PUBLICAR_CASO, PermisosCaso.ARCHIVAR_CASO);

    private PoliticaPermisos() {
    }

    public static boolean esControlado(String codigo) {
        return CONTROLADOS.contains(codigo);
    }

    /** Asignar y ejecutar se mantienen separados: un administrador no se asigna estos permisos. */
    public static boolean exigeSeparacionDeFunciones(String codigo) {
        return OPERATIVOS.contains(codigo);
    }
}
