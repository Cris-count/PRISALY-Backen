package com.prisaly.m5.application.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.intento.MotivoFinalizacionRequeridoException;
import com.prisaly.m5.domain.intento.TransicionIntentoInvalidaException;

@DisplayName("FinalizarIntentoServicio")
class FinalizarIntentoServicioTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");

    private RepositorioIntentosEnMemoria repositorio;
    private FinalizarIntentoServicio servicio;
    private Intento intento;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioIntentosEnMemoria();
        servicio = new FinalizarIntentoServicio(repositorio);
        intento = repositorio.existente(REF, Intento.iniciar());
    }

    @Test
    @DisplayName("finaliza con el motivo indicado y guarda una vez")
    void finalizaIntento() {
        ResumenIntento resumen = servicio.finalizar(REF, MotivoFinalizacion.TIEMPO_VENCIDO);

        assertEquals(EstadoIntento.FINALIZADO, resumen.estado());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), resumen.motivoFinalizacion());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("rechaza una segunda finalización con otro motivo sin guardar ni cambiar el motivo")
    void rechazaSegundaFinalizacionConOtroMotivo() {
        servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(TransicionIntentoInvalidaException.class,
                () -> servicio.finalizar(REF, MotivoFinalizacion.TIEMPO_VENCIDO));

        assertEquals(1, repositorio.guardados());
        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("D13: un reintento con el mismo motivo devuelve el resumen existente sin guardar")
    void reintentoConElMismoMotivoEsIdempotente() {
        ResumenIntento primero = servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        ResumenIntento repetido = servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(primero, repetido);
        assertEquals(1, repositorio.guardados());
        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("un motivo nulo sobre un intento finalizado sigue siendo un dato inválido")
    void motivoNuloSobreIntentoFinalizado() {
        servicio.finalizar(REF, MotivoFinalizacion.TIEMPO_VENCIDO);

        assertThrows(MotivoFinalizacionRequeridoException.class, () -> servicio.finalizar(REF, null));

        assertEquals(1, repositorio.guardados());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("propaga el motivo obligatorio sin guardar")
    void rechazaMotivoNulo() {
        assertThrows(MotivoFinalizacionRequeridoException.class, () -> servicio.finalizar(REF, null));

        assertEquals(0, repositorio.guardados());
        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
    }

    private static final Instant LIMITE = Instant.parse("2026-11-17T11:00:00Z");

    @Test
    @DisplayName("V1-c: entrega antes del límite = ENTREGA_ESTUDIANTE, grabada con el instante que decidió")
    void v1cEntregaAntesDelLimite() {
        Instant ahora = LIMITE.minusSeconds(1);
        repositorio.reloj(ahora, LIMITE);

        ResumenIntento resumen = servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE), resumen.motivoFinalizacion());
        assertEquals(ahora, repositorio.ultimoInstante(), "fin = mismo instante de PostgreSQL que decidió");
        assertEquals(1, repositorio.lecturasDeReloj());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("V1-c: una entrega después del límite se registra como TIEMPO_VENCIDO con ese mismo instante")
    void v1cEntregaDespuesDelLimite() {
        Instant ahora = LIMITE.plusNanos(1_000);
        repositorio.reloj(ahora, LIMITE);

        ResumenIntento resumen = servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(EstadoIntento.FINALIZADO, resumen.estado());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), resumen.motivoFinalizacion());
        assertEquals(ahora, repositorio.ultimoInstante());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("V1-c: en el instante exacto del límite todavía no está vencido (mismo criterio que la regla A1)")
    void v1cFronteraExactaDelLimite() {
        repositorio.reloj(LIMITE, LIMITE);

        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE),
                servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE).motivoFinalizacion());
    }

    @Test
    @DisplayName("V1-c: repetir la entrega sobre un intento cerrado por tiempo devuelve ese cierre sin guardar")
    void v1cReintentoSobreCierrePorTiempo() {
        repositorio.reloj(LIMITE.plusSeconds(5), LIMITE);
        ResumenIntento primero = servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        ResumenIntento repetido = servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(primero, repetido);
        assertEquals(1, repositorio.guardados());
        assertEquals(2, repositorio.lecturasDeReloj(), "el reintento comprueba con el reloj que el límite ya pasó");
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("D13 se conserva: una entrega antes del límite sobre un intento cerrado por tiempo es otro motivo (409)")
    void d13EntregaAntesDelLimiteSobreCierrePorTiempo() {
        repositorio.reloj(LIMITE.minusSeconds(30), LIMITE);
        servicio.finalizar(REF, MotivoFinalizacion.TIEMPO_VENCIDO); // cierre por tiempo pedido por el invocador

        assertThrows(TransicionIntentoInvalidaException.class,
                () -> servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE));
        assertEquals(1, repositorio.guardados());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("V1-c: el cierre docente cierra por tiempo un intento vencido")
    void v1cCierreDocenteDeIntentoVencido() {
        Instant ahora = LIMITE.plusSeconds(60);
        repositorio.reloj(ahora, LIMITE);

        assertEquals(true, servicio.cerrarSiVencido(REF));

        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
        assertEquals(ahora, repositorio.ultimoInstante());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("V1-c: el cierre docente no toca un intento vigente (cierre anticipado de la sesión)")
    void v1cCierreDocenteNoTocaIntentoVigente() {
        repositorio.reloj(LIMITE.minusSeconds(60), LIMITE);

        assertEquals(false, servicio.cerrarSiVencido(REF));

        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
        assertEquals(Optional.empty(), intento.motivoFinalizacion());
        assertEquals(0, repositorio.guardados());
    }

    @Test
    @DisplayName("V1-c: el cierre docente ignora un intento ya finalizado (p. ej. entrega concurrente) sin fallar")
    void v1cCierreDocenteIgnoraIntentoFinalizado() {
        servicio.finalizar(REF, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        repositorio.reloj(LIMITE.plusSeconds(60), LIMITE);

        assertEquals(false, servicio.cerrarSiVencido(REF));

        assertEquals(Optional.of(MotivoFinalizacion.ENTREGA_ESTUDIANTE), intento.motivoFinalizacion());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("V1-c: un motivo nulo se rechaza antes de leer el reloj")
    void v1cMotivoNuloNoLeeElReloj() {
        assertThrows(MotivoFinalizacionRequeridoException.class, () -> servicio.finalizar(REF, null));
        assertEquals(0, repositorio.lecturasDeReloj());
    }

    @Test
    @DisplayName("informa un intento inexistente sin guardar")
    void rechazaIntentoInexistente() {
        assertThrows(IntentoNoEncontradoException.class,
                () -> servicio.finalizar(new ReferenciaIntento("I-X"), MotivoFinalizacion.TIEMPO_VENCIDO));

        assertEquals(0, repositorio.guardados());
    }
}
