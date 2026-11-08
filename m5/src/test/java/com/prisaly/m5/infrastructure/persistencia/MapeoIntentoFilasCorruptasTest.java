package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.FilaIntento;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.FilaRespuesta;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DC-M5M6-MACRO-02: corrupt rows are a persistence failure (500), not a client-caused domain rejection (400/409). */
class MapeoIntentoFilasCorruptasTest {
    private final UUID pregunta = UUID.randomUUID();

    @Test void dosFilasParaLaMismaPreguntaSonFalloDePersistencia() {
        assertThrows(PersistenciaIntentoException.class, () -> MapeoIntento.reconstruir(new FilaIntento("EnCurso", null),
                List.of(new FilaRespuesta(pregunta, UUID.randomUUID()), new FilaRespuesta(pregunta, UUID.randomUUID()))));
    }

    @Test void revisionNoPositivaEsFalloDePersistencia() {
        assertThrows(PersistenciaIntentoException.class, () -> MapeoIntento.reconstruir(new FilaIntento("EnCurso", null),
                List.of(new FilaRespuesta(pregunta, UUID.randomUUID(), 0))));
    }
}
