package com.soll.domain.securitylab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivePayloadRedactorTest {
    @Test
    fun `redacts json token password and secret values`() {
        val redacted = SensitivePayloadRedactor.redactSecrets(
            """{"host":"192.168.1.10","token":"abc","nested":{"wifiPassword":"pass","clientSecret":"s3cr3t"}}""",
        ).orEmpty()

        assertTrue(redacted.contains(""""token":"[REDACTED]""""))
        assertTrue(redacted.contains(""""wifiPassword":"[REDACTED]""""))
        assertTrue(redacted.contains(""""clientSecret":"[REDACTED]""""))
        assertFalse(redacted.contains("abc"))
        assertFalse(redacted.contains("pass"))
        assertFalse(redacted.contains("s3cr3t"))
    }

    @Test
    fun `redacts uri and key value secrets`() {
        val redacted = SensitivePayloadRedactor.redactSecrets(
            "soll-device://pair?host=192.168.1.10&token=abc;password=p4ss,apiSecret=s3cr3t",
        ).orEmpty()

        assertTrue(redacted.contains("token=[REDACTED]"))
        assertTrue(redacted.contains("password=[REDACTED]"))
        assertTrue(redacted.contains("apiSecret=[REDACTED]"))
        assertFalse(redacted.contains("abc"))
        assertFalse(redacted.contains("p4ss"))
        assertFalse(redacted.contains("s3cr3t"))
    }
}
