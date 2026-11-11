package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class M4SchemaContractTest {
    @Test void contratoDesconocidoNoConsultaNiAceptaListaArbitraria() {
        var ex = assertThrows(IllegalStateException.class,
                () -> M4SchemaContract.verificar(new JdbcTemplate(), "aceptar-todo"));
        assertTrue(ex.getMessage().contains("contrato desconocido"));
    }
    // P5 (DC-P5-FASE-B-01): the four contracts ship a valid reference; the canonical one is V013.
    @Test void contratosConocidosTienenReferenciaValidaYElVigenteEsV013() throws Exception {
        assertEquals(M4SchemaContract.V013, M4SchemaContract.VIGENTE);
        for (String c : List.of(M4SchemaContract.HISTORICO, M4SchemaContract.DD02, M4SchemaContract.V013,
                M4SchemaContract.V013_DD02)) {
            try (var in = M4SchemaContract.class.getResourceAsStream("/m4-schema/" + c + ".properties")) {
                assertNotNull(in, c);
                var p = new java.util.Properties(); p.load(in);
                assertTrue(p.getProperty("structure-sha256", "").matches("[a-f0-9]{64}"), c);
                assertTrue(p.getProperty("history-sha256", "").matches("[a-f0-9]{64}"), c);
            }
        }
    }
    @Test void huellaEstableSinConfundirLimitesDeFilas() {
        assertEquals(M4SchemaContract.huella(List.of("b", "a\r\nc")),
                M4SchemaContract.huella(List.of("a\nc", "b")));
        assertNotEquals(M4SchemaContract.huella(List.of("a\nb", "c")),
                M4SchemaContract.huella(List.of("a", "b\nc")));
    }
}
