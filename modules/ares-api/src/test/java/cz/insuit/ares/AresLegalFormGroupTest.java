package cz.insuit.ares;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("legal form groups")
class AresLegalFormGroupTest {

    @Test
    @DisplayName("a code falls into its group, and an unlisted one is a legal entity")
    void groups() {
        assertEquals(AresLegalFormGroup.SOLE_TRADER, AresLegalFormGroup.of("101"));
        assertEquals(AresLegalFormGroup.NON_PROFIT, AresLegalFormGroup.of("706"));
        assertEquals(AresLegalFormGroup.LEGAL_ENTITY, AresLegalFormGroup.of("112"));
        assertEquals(AresLegalFormGroup.LEGAL_ENTITY, AresLegalFormGroup.of("801"));
    }

    @Test
    @DisplayName("no code and the register's own \"not determined\" have no group")
    void unknown() {
        assertNull(AresLegalFormGroup.of(null));
        assertNull(AresLegalFormGroup.of(""));
        assertNull(AresLegalFormGroup.of("000"));
    }
}
