package com.prisaly.m5.infrastructure.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;

/**
 * Adaptador M1 → M5 con dobles de {@link StudentAccessPort} (solo en pruebas)
 * y con el adaptador real de M1, {@link DenyingStudentAccessService}. No es
 * una integración real M1–M5: DD-02 sigue abierto y la titularidad (M4) se
 * simula aquí.
 */
@DisplayName("IdentidadEstudianteDesdeM1")
class IdentidadEstudianteDesdeM1Test {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID ESTUDIANTE = UUID.randomUUID();
    private static final UUID OTRO_ESTUDIANTE = UUID.randomUUID();
    private static final ReferenciaIntento INTENTO = new ReferenciaIntento(UUID.randomUUID().toString());

    /** Titularidad simulada solo para la prueba: el intento pertenece a ESTUDIANTE. */
    private final TitularidadIntentos titularidad = (intento, estudiante) -> !INTENTO.equals(intento)
            ? Titularidad.INEXISTENTE
            : new IdentificadorEstudiante(ESTUDIANTE.toString()).equals(estudiante)
                    ? Titularidad.PROPIO
                    : Titularidad.AJENO;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static ControlAccesoIntentos control(StudentAccessPort puerto, TitularidadIntentos titularidad) {
        return new ControlAccesoIntentos(new IdentidadEstudianteDesdeM1(puerto), titularidad);
    }

    @Test
    @DisplayName("1. estudiante autenticado y titular: autorizado, con studentId y no con userId")
    void titularAutorizado() {
        StudentAccessPort puerto = () -> new AuthenticatedStudent(USUARIO, ESTUDIANTE);

        IdentificadorEstudiante verificado = control(puerto, titularidad).verificar(INTENTO);

        assertEquals(ESTUDIANTE.toString(), verificado.valor());
    }

    @Test
    @DisplayName("2. estudiante autenticado pero no titular: denegado")
    void noTitularDenegado() {
        StudentAccessPort puerto = () -> new AuthenticatedStudent(USUARIO, OTRO_ESTUDIANTE);

        assertThrows(AccesoIntentoDenegadoException.class, () -> control(puerto, titularidad).verificar(INTENTO));
    }

    @Test
    @DisplayName("3. sin identidad (M1 deniega con cualquier código): denegado")
    void sinIdentidadDenegado() {
        StudentAccessPort puerto = () -> {
            throw new IdentityDomainException("UNAUTHENTICATED", "No authenticated user in request context");
        };

        assertEquals(Optional.empty(), new IdentidadEstudianteDesdeM1(puerto).actual());
        assertThrows(EstudianteNoIdentificadoException.class, () -> control(puerto, titularidad).verificar(INTENTO));
    }

    @Test
    @DisplayName("4. DD-02 abierta: el adaptador real de M1 nunca entrega identidad")
    void dd02Denegado() {
        StudentAccessPort produccionActual = new DenyingStudentAccessService();

        assertEquals(Optional.empty(), new IdentidadEstudianteDesdeM1(produccionActual).actual());
        assertThrows(EstudianteNoIdentificadoException.class,
                () -> control(produccionActual, titularidad).verificar(INTENTO));
    }

    @Test
    @DisplayName("5. un token de personal autenticado nunca produce identidad estudiantil")
    void tokenDePersonalNoEsEstudiante() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USUARIO, "token-personal", List.of(new SimpleGrantedAuthority("ROLE_DOCENTE"))));

        assertEquals(Optional.empty(), new IdentidadEstudianteDesdeM1(new DenyingStudentAccessService()).actual());
    }

    @Test
    @DisplayName("6. un fallo del proveedor de identidad se propaga y nunca concede acceso")
    void falloDelProveedor() {
        StudentAccessPort puerto = () -> {
            throw new IllegalStateException("proveedor de identidad no disponible");
        };

        assertThrows(IllegalStateException.class, () -> control(puerto, titularidad).verificar(INTENTO));
    }

    @Test
    @DisplayName("6b. respuestas incompletas de M1 se tratan como ausencia de identidad")
    void respuestaIncompleta() {
        assertEquals(Optional.empty(), new IdentidadEstudianteDesdeM1(() -> null).actual());
        assertEquals(Optional.empty(),
                new IdentidadEstudianteDesdeM1(() -> new AuthenticatedStudent(USUARIO, null)).actual());
    }

    @Test
    @DisplayName("7. ningún dato del cliente sustituye la identidad: la API no la recibe como parámetro")
    void identidadSoloDelServidor() {
        List<Method> publicos = Arrays.stream(IdentidadEstudianteDesdeM1.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).toList();
        assertEquals(List.of("actual"), publicos.stream().map(Method::getName).toList());
        assertEquals(0, publicos.get(0).getParameterCount());

        Method verificar = Arrays.stream(ControlAccesoIntentos.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("verificar")).findFirst().orElseThrow();
        assertEquals(List.of(ReferenciaIntento.class), List.of(verificar.getParameterTypes()));
    }

    @Test
    @DisplayName("un intento desconocido sigue tratándose como inexistente para el estudiante autenticado")
    void intentoDesconocido() {
        StudentAccessPort puerto = () -> new AuthenticatedStudent(USUARIO, ESTUDIANTE);

        assertThrows(IntentoNoEncontradoException.class, () -> control(puerto, titularidad)
                .verificar(new ReferenciaIntento(UUID.randomUUID().toString())));
    }
}
