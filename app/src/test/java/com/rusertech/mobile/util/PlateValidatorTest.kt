package com.rusertech.mobile.util

import org.junit.Assert.*
import org.junit.Test

class PlateValidatorTest {
    @Test fun `Mercosur AB123CD`() { assertTrue(PlateValidator.isValid("AB123CD")) }
    @Test fun `AR clásica ABC123`() { assertTrue(PlateValidator.isValid("ABC123")) }
    @Test fun `BR viejo ABC1234`() { assertTrue(PlateValidator.isValid("ABC1234")) }
    @Test fun `Chile ABCD12`() { assertTrue(PlateValidator.isValid("ABCD12")) }
    @Test fun `con guiones normaliza`() {
        assertTrue(PlateValidator.isValid("AB-123-CD"))
        assertEquals("AB123CD", PlateValidator.normalize("AB-123-CD"))
    }
    @Test fun `minúsculas se normalizan`() { assertEquals("AB123CD", PlateValidator.normalize("ab123cd")) }
    @Test fun `muy corto es inválido`() { assertFalse(PlateValidator.isValid("AB12")) }
    @Test fun `muy largo es inválido`() { assertFalse(PlateValidator.isValid("ABCDEFGHI")) }
}
