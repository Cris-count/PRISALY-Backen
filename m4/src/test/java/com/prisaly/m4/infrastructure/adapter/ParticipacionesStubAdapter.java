package com.prisaly.m4.infrastructure.adapter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.prisaly.m4.application.port.ParticipacionesPort;

/** Doble explícito: nunca se empaqueta en la aplicación. */
public class ParticipacionesStubAdapter implements ParticipacionesPort {
    private final Set<UUID> conHistorial = ConcurrentHashMap.newKeySet();

    public void registrarHistorial(UUID sesionId) {
        conHistorial.add(sesionId);
    }

    public void limpiar() {
        conHistorial.clear();
    }

    @Override
    public boolean tieneParticipacionesOIntentos(UUID sesionId) {
        return conHistorial.contains(sesionId);
    }
}
