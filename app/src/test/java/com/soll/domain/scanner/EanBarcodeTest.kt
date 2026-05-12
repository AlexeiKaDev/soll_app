package com.soll.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EanBarcodeTest {
    @Test
    fun `validates ean13 checksum`() {
        assertTrue(EanBarcode.isValidEan13("4006381333931"))
        assertFalse(EanBarcode.isValidEan13("4006381333932"))
    }

    @Test
    fun `validates ean8 checksum`() {
        assertTrue(EanBarcode.isValidEan8("96385074"))
        assertFalse(EanBarcode.isValidEan8("96385075"))
    }

    @Test
    fun `normalizes separators and detects format`() {
        assertEquals("4006381333931", EanBarcode.normalize("400-6381333931"))
        assertEquals(BarcodeFormat.EAN_13.name, EanBarcode.detectFormat("400-6381333931"))
        assertEquals(BarcodeFormat.NUMERIC.name, EanBarcode.detectFormat("12345"))
    }

    @Test
    fun `confirmation gate requires repeated frame and suppresses cooldown duplicates`() {
        val gate = ScanConfirmationGate(requiredMatches = 2, windowMs = 1000, cooldownMs = 2000)

        val first = gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 0)
        val second = gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 300)
        val third = gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 600)
        val fourth = gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 2600)

        assertFalse(first.confirmed)
        assertEquals(1, first.matchCount)
        assertTrue(second.confirmed)
        assertTrue(third.ignoredByCooldown)
        assertFalse(fourth.confirmed)
        assertEquals(1, fourth.matchCount)
    }

    @Test
    fun `confirmation gate resets when value changes`() {
        val gate = ScanConfirmationGate(requiredMatches = 2, windowMs = 1000, cooldownMs = 2000)

        val first = gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 0)
        val changed = gate.observe("96385074", BarcodeFormat.EAN_8.name, nowMs = 200)
        val repeated = gate.observe("96385074", BarcodeFormat.EAN_8.name, nowMs = 400)

        assertFalse(first.confirmed)
        assertFalse(changed.confirmed)
        assertEquals(1, changed.matchCount)
        assertTrue(repeated.confirmed)
    }

    @Test
    fun `confirmation gate reset clears cooldown`() {
        val gate = ScanConfirmationGate(requiredMatches = 2, windowMs = 1000, cooldownMs = 2000)

        assertFalse(gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 0).confirmed)
        assertTrue(gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 200).confirmed)
        gate.reset()

        val afterReset = gate.observe("4006381333931", BarcodeFormat.EAN_13.name, nowMs = 300)

        assertFalse(afterReset.confirmed)
        assertFalse(afterReset.ignoredByCooldown)
        assertEquals(1, afterReset.matchCount)
    }

    @Test
    fun `scanner duplicate policy parses persisted value safely`() {
        assertEquals(
            ScannerDuplicatePolicy.IGNORE_EXISTING,
            ScannerDuplicatePolicy.fromStorage("ignore_existing"),
        )
        assertEquals(
            ScannerDuplicatePolicy.COUNT_REPEATS,
            ScannerDuplicatePolicy.fromStorage("missing"),
        )
    }

    @Test
    fun `device pairing parser reads websocket qr`() {
        val payload = ScannerDevicePairingParser.parse("ws://192.168.1.44:82/api/ws?token=secret")

        assertEquals("192.168.1.44", payload?.host)
        assertEquals(82, payload?.port)
        assertEquals("api/ws", payload?.path)
        assertEquals("secret", payload?.token)
        assertEquals("aquik-v2", payload?.profileId)
    }

    @Test
    fun `device pairing parser reads generic gadget profile`() {
        val payload = ScannerDevicePairingParser.parse(
            """{"profileId":"generic-esp-websocket","host":"192.168.1.45","port":81,"path":"ws"}"""
        )

        assertEquals("192.168.1.45", payload?.host)
        assertEquals("generic-esp-websocket", payload?.profileId)
    }

    @Test
    fun `device pairing parser ignores plain numeric barcode`() {
        assertEquals(null, ScannerDevicePairingParser.parse("4006381333931"))
    }
}
