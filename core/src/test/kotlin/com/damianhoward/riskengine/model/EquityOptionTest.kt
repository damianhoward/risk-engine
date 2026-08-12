package com.damianhoward.riskengine.model

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EquityOptionTest {
    @Test
    fun rejectsNonPositiveStrike() {
        assertThrows(IllegalArgumentException::class.java) {
            EquityOption(strike = Money.of("0"), type = OptionType.CALL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EquityOption(strike = Money.of("-40"), type = OptionType.PUT)
        }
    }
}
