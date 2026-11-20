package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * V1-c (DC-BE-M5-CIERRE-IMPLEMENTACION-45): al cerrar el docente una sesión, el proveedor de participaciones (M5)
 * cierra con {@code TIEMPO_VENCIDO} los intentos en curso de esa sesión cuyo límite temporal ya pasó según el reloj de
 * PostgreSQL. Los intentos vigentes no se tocan: ni su estado ni su límite persistido.
 *
 * <p>Mismo patrón que {@link ParticipacionesPort}: M4 declara el puerto y M5 lo implementa (sin dependencia
 * {@code m4 → m5}). Se invoca dentro de la transacción de escritura que ya bloqueó la fila de la sesión
 * ({@code FOR UPDATE}); si falla, la sesión tampoco se cierra. Un error técnico se propaga: nunca se ignora.
 */
public interface CierreParticipacionesPort {
    /** @return número de intentos cerrados por tiempo */
    int cerrarIntentosVencidos(UUID sesionId);
}
