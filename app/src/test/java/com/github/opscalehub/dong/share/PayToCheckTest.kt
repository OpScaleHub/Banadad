package com.github.opscalehub.dong.share

import com.github.opscalehub.dong.share.PayTo.Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Card / IBAN checks for «اطلاعات پرداخت روی قبض» (synthetic numbers, not real accounts). */
class PayToCheckTest {
    private val card = "6037991234567893"            // Luhn-valid, Bank Melli prefix
    private val iban = "IR050170000000123456789012"  // mod-97 valid

    @Test fun `a card and an IBAN in any common typing are accepted and normalised`() {
        assertEquals(Check.OK, PayTo.check(card))
        assertEquals(Check.OK, PayTo.check("۶۰۳۷ ۹۹۱۲ ۳۴۵۶ ۷۸۹۳"))
        assertEquals(card, PayTo.normalize("6037-9912-3456-7893"))
        assertEquals(Check.OK, PayTo.check(iban))
        assertEquals(iban, PayTo.normalize("050170000000123456789012"))   // 24 digits without IR
        assertEquals(iban, PayTo.normalize("ir05 0170 0000 0012 3456 7890 12"))
        assertEquals(Check.EMPTY, PayTo.check(""))
    }

    @Test fun `typos and wrong lengths are caught`() {
        assertEquals(Check.BAD_CARD, PayTo.check("6037991234567894"))
        assertEquals(Check.BAD_IBAN, PayTo.check("IR060170000000123456789012"))
        assertEquals(Check.WRONG_LENGTH, PayTo.check("603799123456"))
        assertEquals(Check.WRONG_LENGTH, PayTo.check("hello"))
    }

    @Test fun `a card typed with its groups reversed is flagged, though its checksum still passes`() {
        // What a right-to-left field invited: groups typed last-to-first so they looked right.
        val reversed = card.chunked(4).reversed().joinToString("")
        assertEquals(Check.UNKNOWN_BANK, PayTo.check(reversed))
    }

    @Test fun `a number in the name field is noticed`() {
        assertTrue(PayTo.looksLikeNumber("6062 9316 2910 5022"))
        assertFalse(PayTo.looksLikeNumber("مدیر ساختمان"))
    }
}
