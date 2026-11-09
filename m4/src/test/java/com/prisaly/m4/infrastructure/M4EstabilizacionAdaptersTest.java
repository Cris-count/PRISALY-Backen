package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.CasoAcademicoBd0052Adapter;
import com.prisaly.m4.infrastructure.adapter.CasoAcademicoM3Adapter;
import com.prisaly.m4.infrastructure.adapter.SesionRepositoryAdapter;
import com.prisaly.m4.infrastructure.persistence.JpaSesionRepository;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class M4EstabilizacionAdaptersTest {
    @AfterEach
    void limpiarContextoTransaccionalDePrueba() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void casoPublicadoInactivoNoEsProgramablePorElAdaptadorJava() {
        UUID id = UUID.randomUUID();
        var m3 = mock(CasoRepositoryPort.class);
        when(m3.findById(id.toString()))
                .thenReturn(
                        Optional.of(
                                new Caso(
                                        id.toString(),
                                        "Caso",
                                        UUID.randomUUID().toString(),
                                        60,
                                        CasoEstado.PUBLICADO,
                                        List.of(),
                                        null,
                                        false,
                                        null,
                                        null,
                                        false,
                                        null,
                                        null,
                                        null)));
        assertFalse(new CasoAcademicoM3Adapter(m3).estaPublicado(id));
        verify(m3, never()).update(any());
    }

    @Test
    void falloDeHistorialNuncaEsFalseNiFiltraDetallesSql() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var causa = new DataAccessResourceFailureException("SELECT detalle_SQL_interno");
        var adapter =
                new SesionRepositoryAdapter(
                        mock(JpaSesionRepository.class),
                        id -> {
                            throw causa;
                        });
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> adapter.tieneParticipacionesVinculadas(UUID.randomUUID()));
        verificar503(error, causa);
    }

    @Test
    void historialRechazaAusenciaDeTransaccionYSoloLecturaAntesDelProveedor() {
        var proveedor = mock(ParticipacionesPort.class);
        var adapter = new SesionRepositoryAdapter(mock(JpaSesionRepository.class), proveedor);
        assertThrows(
                ReglaNegocioException.class,
                () -> adapter.tieneParticipacionesVinculadas(UUID.randomUUID()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(
                ReglaNegocioException.class,
                () -> adapter.tieneParticipacionesVinculadas(UUID.randomUUID()));
        verifyNoInteractions(proveedor);
    }

    @Test
    void historialDisponibleConservaTrueYFalseDelContrato() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UUID id = UUID.randomUUID();
        var proveedor = mock(ParticipacionesPort.class);
        when(proveedor.tieneParticipacionesOIntentos(id)).thenReturn(true, false);
        var adapter = new SesionRepositoryAdapter(mock(JpaSesionRepository.class), proveedor);
        assertTrue(adapter.tieneParticipacionesVinculadas(id));
        assertFalse(adapter.tieneParticipacionesVinculadas(id));
    }

    @Test
    void falloSqlDeCasoSeExponeComoIndisponibilidad() {
        var jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        var causa = new DataAccessResourceFailureException("SELECT detalle_SQL_interno");
        doThrow(causa)
                .when(jdbc)
                .query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Boolean>>any(), eq(id));
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> new CasoAcademicoBd0052Adapter(jdbc).estaPublicado(id));
        verificar503(error, causa);
    }

    @Test
    void confirmacionF303ExigeEscrituraYConservaFalloSqlComo503() {
        var jdbc = mock(JdbcTemplate.class);
        var adapter = new CasoAcademicoBd0052Adapter(jdbc);
        UUID id = UUID.randomUUID();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertEquals(
                "M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO",
                assertThrows(
                                ReglaNegocioException.class,
                                () -> adapter.solicitarBloqueoAcademico(id))
                        .getCodigo());
        verifyNoInteractions(jdbc);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        var causa = new DataAccessResourceFailureException("SELECT detalle_SQL_interno");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(id))).thenThrow(causa);
        verificar503(
                assertThrows(
                        ReglaNegocioException.class, () -> adapter.solicitarBloqueoAcademico(id)),
                causa);
    }

    private void verificar503(ReglaNegocioException error, Throwable causa) {
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
        assertSame(causa, error.getCause());
        var respuesta = new M4ExceptionHandler().handleReglaNegocio(error);
        assertEquals(503, respuesta.getStatusCode().value());
        assertFalse(respuesta.getBody().toString().contains("detalle_SQL_interno"));
    }
}
