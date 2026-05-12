package com.soll.domain.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.util.Locale

data class NfcTagSnapshot(
    val uid: String,
    val technologies: List<String>,
    val accessDiagnostics: NfcAccessDiagnostics,
    val ndefType: String?,
    val maxSizeBytes: Int?,
    val isWritable: Boolean,
    val supportsNdef: Boolean,
    val supportsFormat: Boolean,
    val records: List<NfcRecordSnapshot>,
)

data class NfcRecordSnapshot(
    val kind: String,
    val value: String,
)

data class NfcAccessDiagnostics(
    val frequencyBand: String,
    val detectedFamily: String,
    val phoneAsKeyVerdict: String,
    val officialMobilePath: String,
    val notes: List<String>,
)

data class NfcWriteRequest(
    val type: NfcWritePayloadType,
    val payload: String,
)

enum class NfcWritePayloadType {
    TEXT,
    URI,
}

object NfcTagTools {
    fun inspect(tag: Tag): NfcTagSnapshot {
        val ndef = Ndef.get(tag)
        val message = readNdefMessage(ndef)
        val technologies = tag.techList.map { it.substringAfterLast(".") }.sorted()
        return NfcTagSnapshot(
            uid = tag.id.toHex(),
            technologies = technologies,
            accessDiagnostics = buildAccessDiagnostics(technologies, ndef),
            ndefType = ndef?.type,
            maxSizeBytes = ndef?.maxSize,
            isWritable = ndef?.isWritable == true,
            supportsNdef = ndef != null,
            supportsFormat = NdefFormatable.get(tag) != null,
            records = message?.records?.map { it.toSnapshot() }.orEmpty(),
        )
    }

    private fun buildAccessDiagnostics(
        technologies: List<String>,
        ndef: Ndef?,
    ): NfcAccessDiagnostics {
        val hasIsoDep = "IsoDep" in technologies
        val hasMifareClassic = "MifareClassic" in technologies
        val hasMifareUltralight = "MifareUltralight" in technologies
        val hasNfcA = "NfcA" in technologies
        val hasNfcB = "NfcB" in technologies
        val hasNfcF = "NfcF" in technologies
        val hasNfcV = "NfcV" in technologies

        val family = when {
            hasIsoDep -> "ISO-DEP / ISO 14443-4"
            hasMifareClassic -> "MIFARE Classic / NFC-A"
            hasMifareUltralight -> "MIFARE Ultralight / NFC Forum Type 2"
            hasNfcV -> "NFC-V / ISO 15693"
            hasNfcF -> "NFC-F / FeliCa"
            hasNfcB -> "NFC-B / ISO 14443-B"
            hasNfcA -> "NFC-A / ISO 14443-A"
            else -> "Не определено"
        }

        val verdict = when {
            hasIsoDep -> "Не копия ключа. Возможен только официальный HCE/мобильный пропуск, если считыватель работает по APDU и администратор выдал учетные данные."
            hasMifareClassic -> "Нельзя надежно эмулировать как подъездной ключ из обычного Android-приложения: UID и защищенные сектора не подменяются."
            hasMifareUltralight || hasNfcA || hasNfcB || hasNfcF || hasNfcV -> "Телефон может читать такую NFC-метку, но это не означает, что он сможет заменить ее в системе доступа."
            else -> "Если ключ не читается телефоном, он часто бывает 125 кГц. Такой брелок Android NFC физически не видит."
        }

        val officialPath = if (hasIsoDep) {
            "Спросить УК/администратора СКУД про мобильный пропуск или подключить свой считыватель с поддержкой Android HCE."
        } else {
            "Нужен официальный мобильный доступ от производителя домофона/СКУД или замена считывателя на совместимый с мобильными пропусками."
        }

        val notes = buildList {
            add("Обнаруженная телефоном метка относится к 13,56 МГц NFC/HF.")
            add("125 кГц брелоки EM-Marine/HID Prox телефон обычно не обнаруживает.")
            add("Android HCE работает с APDU/AID-сценарием, а не с произвольным клонированием UID.")
            if (ndef != null) {
                add("NDEF доступен: это полезно для обычных NFC-меток, но не является признаком подъездного доступа.")
            }
        }

        return NfcAccessDiagnostics(
            frequencyBand = "13,56 МГц NFC/HF",
            detectedFamily = family,
            phoneAsKeyVerdict = verdict,
            officialMobilePath = officialPath,
            notes = notes,
        )
    }

    fun write(tag: Tag, request: NfcWriteRequest): NfcTagSnapshot {
        val message = request.toNdefMessage()
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                require(ndef.isWritable) { "Метка защищена от записи" }
                val payloadSize = message.toByteArray().size
                require(payloadSize <= ndef.maxSize) {
                    "Данные больше емкости метки: $payloadSize > ${ndef.maxSize} байт"
                }
                ndef.writeNdefMessage(message)
            } finally {
                runCatching { ndef.close() }
            }
            return inspect(tag)
        }

        val formatable = NdefFormatable.get(tag)
            ?: error("Эта метка не поддерживает NDEF-запись")
        formatable.connect()
        try {
            formatable.format(message)
        } finally {
            runCatching { formatable.close() }
        }
        return inspect(tag)
    }

    private fun readNdefMessage(ndef: Ndef?): NdefMessage? {
        if (ndef == null) return null
        return runCatching {
            ndef.connect()
            ndef.ndefMessage ?: ndef.cachedNdefMessage
        }.getOrNull().also {
            runCatching { ndef.close() }
        }
    }

    private fun NfcWriteRequest.toNdefMessage(): NdefMessage {
        val cleanPayload = payload.trim()
        require(cleanPayload.isNotBlank()) { "Введите данные для записи" }
        val record = when (type) {
            NfcWritePayloadType.TEXT -> NdefRecord.createTextRecord("ru", cleanPayload)
            NfcWritePayloadType.URI -> NdefRecord.createUri(cleanPayload)
        }
        return NdefMessage(arrayOf(record))
    }

    private fun NdefRecord.toSnapshot(): NfcRecordSnapshot =
        when {
            tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) ->
                NfcRecordSnapshot(kind = "Текст", value = decodeTextPayload(payload))
            tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI) ->
                NfcRecordSnapshot(kind = "URL", value = decodeUriPayload(payload))
            tnf == NdefRecord.TNF_MIME_MEDIA ->
                NfcRecordSnapshot(kind = "MIME ${type.decodeAscii()}", value = payload.preview())
            tnf == NdefRecord.TNF_EXTERNAL_TYPE ->
                NfcRecordSnapshot(kind = "External ${type.decodeAscii()}", value = payload.preview())
            tnf == NdefRecord.TNF_EMPTY ->
                NfcRecordSnapshot(kind = "Пусто", value = "")
            else ->
                NfcRecordSnapshot(kind = "TNF $tnf", value = payload.preview())
        }

    private fun decodeTextPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val languageLength = status and 0x3F
        val charset = if ((status and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
        return payload.drop(1 + languageLength).toByteArray().toString(charset)
    }

    private fun decodeUriPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val prefix = URI_PREFIXES.getOrElse(payload[0].toInt() and 0xFF) { "" }
        return prefix + payload.drop(1).toByteArray().toString(Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02X".format(Locale.US, byte) }

    private fun ByteArray.decodeAscii(): String =
        toString(Charsets.US_ASCII).ifBlank { "unknown" }

    private fun ByteArray.preview(): String {
        if (isEmpty()) return ""
        val text = runCatching { toString(Charsets.UTF_8) }.getOrDefault("")
        val printable = text.count { !it.isISOControl() || it == '\n' || it == '\t' }
        return if (text.isNotBlank() && printable >= text.length * 0.8) {
            text.take(240)
        } else {
            "0x${take(80).toByteArray().toHex()}"
        }
    }

    private val URI_PREFIXES = listOf(
        "",
        "http://www.",
        "https://www.",
        "http://",
        "https://",
        "tel:",
        "mailto:",
        "ftp://anonymous:anonymous@",
        "ftp://ftp.",
        "ftps://",
        "sftp://",
        "smb://",
        "nfs://",
        "ftp://",
        "dav://",
        "news:",
        "telnet://",
        "imap:",
        "rtsp://",
        "urn:",
        "pop:",
        "sip:",
        "sips:",
        "tftp:",
        "btspp://",
        "btl2cap://",
        "btgoep://",
        "tcpobex://",
        "irdaobex://",
        "file://",
        "urn:epc:id:",
        "urn:epc:tag:",
        "urn:epc:pat:",
        "urn:epc:raw:",
        "urn:epc:",
        "urn:nfc:",
    )
}
