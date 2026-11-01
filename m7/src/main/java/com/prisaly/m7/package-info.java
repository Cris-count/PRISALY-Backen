/**
 * Notices and email queue (M7).
 *
 * <p>Fase 1: persistencia e idempotencia de {@code Aviso} y {@code EnvioCorreo}.
 * Encolar no implica entrega SMTP. Adaptador productivo {@code NotificacionPort} = Fase 2.
 */
package com.prisaly.m7;
