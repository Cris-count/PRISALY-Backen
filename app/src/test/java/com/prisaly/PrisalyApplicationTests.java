package com.prisaly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class PrisalyApplicationTests {

    @Test
    void junitAndMockitoAreAvailable() {
        Probe probe = mock(Probe.class);
        when(probe.value()).thenReturn("ok");
        assertEquals("ok", probe.value());
    }

    interface Probe {
        String value();
    }
}
