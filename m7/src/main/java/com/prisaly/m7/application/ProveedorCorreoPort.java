package com.prisaly.m7.application;

import com.prisaly.m7.domain.ResultadoEnvioSmtp;

public interface ProveedorCorreoPort {

    ResultadoEnvioSmtp enviar(String remitente, String destinatario, String asunto, String contenido);
}
