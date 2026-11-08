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
    @Test void huellaEstableSinConfundirLimitesDeFilas() {
        assertEquals(M4SchemaContract.huella(List.of("b", "a\r\nc")),
                M4SchemaContract.huella(List.of("a\nc", "b")));
        assertNotEquals(M4SchemaContract.huella(List.of("a\nb", "c")),
                M4SchemaContract.huella(List.of("a", "b\nc")));
    }
}
