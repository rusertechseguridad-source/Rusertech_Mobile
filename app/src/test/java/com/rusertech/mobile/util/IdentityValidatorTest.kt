package com.rusertech.mobile.util

import org.junit.Assert.*
import org.junit.Test

class IdentityValidatorTest {
    @Test fun `AR DNI 8 dígitos es válido`() { assertTrue(IdentityValidator.isValid("30456789")) }
    @Test fun `AR DNI 7 dígitos es válido`() { assertTrue(IdentityValidator.isValid("5456789")) }
    @Test fun `BR CPF con separadores normaliza y es válido`() {
        assertTrue(IdentityValidator.isValid("123.456.789-00"))
        assertEquals("12345678900", IdentityValidator.normalize("123.456.789-00"))
    }
    @Test fun `CL RUT con K es válido`() {
        assertTrue(IdentityValidator.isValid("12345678-K"))
        assertEquals("12345678K", IdentityValidator.normalize("12345678-K"))
    }
    @Test fun `MX CURP 18 chars es válido`() { assertTrue(IdentityValidator.isValid("ABCD123456HABCDE01")) }
    @Test fun `muy corto es inválido`() { assertFalse(IdentityValidator.isValid("123")) }
    @Test fun `vacío es inválido`() { assertFalse(IdentityValidator.isValid("")) }
    @Test fun `más de 20 chars es inválido`() { assertFalse(IdentityValidator.isValid("A".repeat(21))) }
}
