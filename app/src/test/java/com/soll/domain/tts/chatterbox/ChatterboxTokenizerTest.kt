package com.soll.domain.tts.chatterbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ChatterboxTokenizerTest {

    @Test
    fun loadFromPack_readsSpecialIdsAndEncodesRussianPayload() {
        val packDir = createTempDirectory(prefix = "chatterbox-tokenizer-").toFile()
        try {
            File(packDir, "tokenizer.json").writeText(
                """
                {
                  "model": {
                    "vocab": {
                      "[UNK]": 1,
                      "[START]": 255,
                      "[STOP]": 0,
                      "<EXAGGERATION>": 6563,
                      "<START_SPEECH>": 6561,
                      "[STOP_SPEECH]": 6562,
                      "[ru]": 10,
                      "[SPACE]": 11,
                      "п": 12,
                      "р": 13,
                      "и": 14,
                      "в": 15,
                      "е": 16,
                      "т": 17,
                      "м": 18
                    },
                    "merges": []
                  },
                  "post_processor": {
                    "special_tokens": {
                      "BOS": { "ids": [255] },
                      "EOS": { "ids": [0] },
                      "EXAGGERATION": { "ids": [6563] },
                      "START_SPEECH": { "ids": [6561] }
                    }
                  }
                }
                """.trimIndent(),
            )

            val profile = ChatterboxTokenizer.loadFromPack(packDir)
            assertNotNull(profile)
            profile ?: return

            assertEquals(6563, profile.exaggerationTokenId)
            assertEquals(6561, profile.startSpeechTokenId)
            assertEquals(6562, profile.stopSpeechTokenId)

            val ids = ChatterboxTokenizer.encodeText("привет мир", profile, languageId = "ru")
            assertArrayEquals(
                longArrayOf(
                    6563,
                    255,
                    10,
                    12,
                    13,
                    14,
                    15,
                    16,
                    17,
                    11,
                    18,
                    14,
                    13,
                    0,
                    6561,
                    6561,
                ),
                ids,
            )
        } finally {
            packDir.deleteRecursively()
        }
    }
}
