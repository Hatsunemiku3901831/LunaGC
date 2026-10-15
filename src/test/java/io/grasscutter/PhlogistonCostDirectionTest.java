package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A negative cost must not turn into a fuel reward when ratio mode is disabled. */
class PhlogistonCostDirectionTest {
    PhlogistonRawActionControlTest env;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonRawActionControlTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "value:-2",
                "value:-2,costType:'Cost'",
                "value:-2,costType:'Sub'",
                "value:-2,costType:'Cost',useRatio:false,JDIMELLBMFO:true",
                "CBOMLBFIPJM:-2",
                "CBOMLBFIPJM:-2,DHDDHBBIHEJ:'Cost'",
                "CBOMLBFIPJM:-2,DHDDHBBIHEJ:'Sub'",
                "ratio:-2,determineType:'Sub'",
                "ratio:-2,determineType:'Cost'",
                "value:['fuel',-1,'MUL'],costType:'Cost'",
                "value:-0.1,costType:'Cost',useRatio:true",
                "CBOMLBFIPJM:-0.1,DHDDHBBIHEJ:'Sub',JDIMELLBMFO:true"
            })
    void invalidCostCannotBecomeAnAward(String fields) {
        assertFalse(env.execute(env.config(fields)));
        assertEquals(50, env.fuel());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "value:2,costType:'Sub'",
                "CBOMLBFIPJM:2,DHDDHBBIHEJ:'Cost'",
                "ratio:2,determineType:'Sub'",
                "value:-2,costType:'Add'"
            })
    void validCostsAndSignedAbsoluteChangesKeepTheirMeaning(String fields) {
        assertTrue(env.execute(env.config(fields)));
        assertEquals(48, env.fuel());
    }
}
