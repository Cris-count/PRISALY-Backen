package com.prisaly.m7.application;

import com.prisaly.m7.domain.ResultadoEnvioSmtp;

/**
 * Puerto hexagonal de salida. La aplicación pide enviar un correo sin saber si detrás
 * está JavaMail u otra implementación. El resultado dice si el SMTP aceptó el mensaje;
 * no prueba que el buzón lo haya recibido.
 */
public interface ProveedorCorreoPort {

    ResultadoEnvioSmtp enviar(String remitente, String destinatario, String asunto, String contenido);
}
