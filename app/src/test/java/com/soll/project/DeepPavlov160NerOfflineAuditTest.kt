package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepPavlov160NerOfflineAuditTest {
    @Test
    fun `DeepPavlov 160 audit preserves taxonomy uncertainty and offline boundary`() {
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-a9914bc93c8e-a2d0a76e2800c653-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 581a85c9bfdf4d04b70186440346b819",
            "project: soll_app",
            "source_ref: source-item/a9914bc93c8e/a2d0a76e2800c653",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "source_processing_result: release_and_pr_verified_offline_ner_runner_added_model_execution_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-a9914bc93c8e-a2d0a76e2800c653-verification.md",
            "6e1036dbfcde1c293b50c742f0736a3965dd1e0d",
            "2e2e994d220ff73e5f0c7aafb9aa17efc4955580",
            "`ner_bert_base`",
            "`bert-base-multilingual-cased`",
            "`1,393,701,190` bytes",
            "`2,128,078,848`-byte `model.pth.tar`",
            "PR #1682 does not enumerate or test the 37 entity names",
            "the later official paper says `32` entity types",
            "the current official demo UI enumerates `35` labels",
            "must not be substituted for a verified 1.6.0 37-type vocabulary",
            "`download=False` and `install=False`",
            "`HF_HUB_OFFLINE=1`",
            "`TRANSFORMERS_OFFLINE=1`",
            "`socket.connect` and `socket.getaddrinfo`",
            "two fixed synthetic Soll-shaped notes",
            "No user note is accepted from a file, argument, stdin or environment variable",
            "0 model/dependency downloads, external inference calls, user-note transfers, Android/runtime changes",
        ).forEach { evidence ->
            assertTrue("Missing DeepPavlov 1.6.0 audit evidence: $evidence", verification.contains(evidence))
        }

        assertFalse(
            "The task-supplied raw snapshot should be explicitly absent in this worktree",
            projectPath(
                "raw/monitored/deeppavlov-releases/" +
                    "20260709-234334-release-1-6-0-2a7d642f.md",
            ).exists(),
        )

        val runner = projectFile(
            "tools/source_processing/run_deeppavlov_160_ner_offline.py",
        ).readText()
        listOf(
            "CONFIG_NAME = \"ner_bert_base\"",
            "TEST_NOTES = (",
            "\"HF_HUB_OFFLINE\": \"1\"",
            "\"TRANSFORMERS_OFFLINE\": \"1\"",
            "\"PIP_NO_INDEX\": \"1\"",
            "event in {\"socket.connect\", \"socket.getaddrinfo\"}",
            "sys.addaudithook(block_outbound_network)",
            "download=False",
            "install=False",
            "def decode_bio(",
            "\"--self-check\"",
        ).forEach { evidence ->
            assertTrue("Missing offline runner evidence: $evidence", runner.contains(evidence))
        }
        listOf("http://", "https://", "requests.", "urllib.", "input(").forEach { remoteOrDynamicInput ->
            assertFalse(
                "Offline synthetic runner must not contain $remoteOrDynamicInput",
                runner.contains(remoteOrDynamicInput),
            )
        }

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
            projectFile("gradle/libs.versions.toml"),
        ).joinToString("\n") { it.readText() }
        listOf("deeppavlov", "transformers", "torch").forEach { dependency ->
            assertFalse(
                "Audit task must not add Android runtime dependency $dependency",
                gradleInputs.contains(dependency, ignoreCase = true),
            )
        }
    }

    private fun projectFile(path: String): File =
        projectPath(path).also { file ->
            assertTrue("Project file not found: $path", file.isFile)
        }

    private fun projectPath(path: String): File = File(projectRoot(), path)

    private fun projectRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            if (
                File(current, "settings.gradle.kts").isFile &&
                File(current, "app").isDirectory
            ) {
                return current
            }
            current = current.parentFile ?: current
        }
        error("Project root not found from ${System.getProperty("user.dir")}")
    }

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
