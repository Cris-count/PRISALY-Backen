package com.prisaly.m4.infrastructure.adapter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prisaly.m4.application.port.CasoAcademicoPort;

/**
 * Stub temporal de M3 – Casos Académicos.
 *
 * <p>BLOQUEO DE INTEGRACIÓN: M3 aún no tiene contratos implementados.
 * Este stub reporta todos los casos como Publicados para permitir
 * el desarrollo de M4. Debe ser reemplazado cuando M3 exponga
 * su contrato real.</p>
 */
@Component
public class CasoAcademicoStubAdapter implements CasoAcademicoPort {

    private static final Logger log = LoggerFactory.getLogger(CasoAcademicoStubAdapter.class);

    private final java.util.Map<UUID, Boolean> estados = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> bloqueosSolicitados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void registrarEstado(UUID casoId, boolean publicado) {
        estados.put(casoId, publicado);
    }

    public boolean fueBloqueoSolicitado(UUID casoId) {
        return bloqueosSolicitados.contains(casoId);
    }

    public void limpiar() {
        estados.clear();
        bloqueosSolicitados.clear();
    }

    @Override
    public boolean estaPublicado(UUID casoId) {
        boolean publicado = estados.getOrDefault(casoId, true);
        log.info("[M3-STUB] Verificación de publicación para caso {}: {}", casoId, publicado);
        return publicado;
    }

    @Override
    public void solicitarBloqueoAcademico(UUID casoId) {
        bloqueosSolicitados.add(casoId);
        log.info("[M3-STUB] Solicitud de bloqueo F3-03 para caso {} registrada", casoId);
    }
}
