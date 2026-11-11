package com.prisaly.m1.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.domain.rule.PoliticaContrasena;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** DM1-08 (DC-M1-04). */
class PoliticaContrasenaTest {
    private static final Predicate<String> LISTA = Set.of("password1234", "qwerty123456")::contains;
    private static final String CORREO = "ana.docente@prisaly.test";

    private static String codigo(String contrasena) {
        return assertThrows(IdentityDomainException.class,
                () -> PoliticaContrasena.validar(contrasena, CORREO, LISTA)).code();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta", "onceCaract1"})
    void menosDeDoceCaracteresSeRechaza(String contrasena) {
        assertEquals(PoliticaContrasena.CODE_TOO_SHORT, codigo(contrasena));
    }

    @Test
    void nulaSeRechaza() {
        assertEquals(PoliticaContrasena.CODE_TOO_SHORT, codigo(null));
    }

    @Test
    void doceCaracteresExactosSeAceptan() {
        assertDoesNotThrow(() -> PoliticaContrasena.validar("frase segura", CORREO, LISTA));
    }

    @Test
    void laLongitudSeCuentaEnCaracteresNoEnBytes() {
        // 12 characters, 24 UTF-8 bytes: accepted.
        assertDoesNotThrow(() -> PoliticaContrasena.validar("ñññññññññññá", CORREO, LISTA));
    }

    @Test
    void setentaYDosBytesSeAceptanYSetentaYTresSeRechazan() {
        assertDoesNotThrow(() -> PoliticaContrasena.validar("a".repeat(72), CORREO, LISTA));
        assertEquals(PoliticaContrasena.CODE_TOO_LONG, codigo("a".repeat(73)));
        // 37 two-byte characters = 74 bytes: too long although only 37 characters.
        assertEquals(PoliticaContrasena.CODE_TOO_LONG, codigo("ñ".repeat(37)));
    }

    @Test
    void sinReglasDeComposicion() {
        assertDoesNotThrow(() -> PoliticaContrasena.validar("solo minusculas largas", CORREO, LISTA));
        assertDoesNotThrow(() -> PoliticaContrasena.validar("987654320987", CORREO, LISTA));
    }

    @Test
    void listaDeBloqueoSinDistinguirMayusculas() {
        assertEquals(PoliticaContrasena.CODE_COMMON, codigo("password1234"));
        assertEquals(PoliticaContrasena.CODE_COMMON, codigo("PassWord1234"));
    }

    @Test
    void contenerElCorreoSeRechazaSinDistinguirMayusculas() {
        assertEquals(PoliticaContrasena.CODE_CONTAINS_EMAIL, codigo("x" + CORREO.toUpperCase() + "!"));
    }
}
