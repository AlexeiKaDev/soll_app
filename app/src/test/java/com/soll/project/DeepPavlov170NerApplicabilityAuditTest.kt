package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepPavlov170NerApplicabilityAuditTest {
    @Test
    fun `DeepPavlov NER audit stays complete and server only`() {
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-a9914bc93c8e-c11a5d98a6b364d9-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: d9e6c8fcd2cd469e8ece838c973f508c",
            "project: soll_app",
            "source_ref: source-item/a9914bc93c8e/c11a5d98a6b364d9",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "source_processing_result: deeppavlov_deberta_ner_audited_production_adoption_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-a9914bc93c8e-c11a5d98a6b364d9-verification.md",
            "aff27489a3c87644eeb8f6009e4a824e83c66c05",
            "ab737eecb9ebbdf6ccc1a616560f17d1982460f6",
            "`ner_conll2003_deberta_crf`",
            "`ner_ontonotes_deberta_crf`",
            "English-only DeBERTa-v3-base + CRF models",
            "CoNLL-2003: `PER`, `ORG`, `LOC`, `MISC`",
            "OntoNotes English NER: 18 documented types",
            "`1,326,708,465` bytes",
            "`1,421,717,858` bytes",
            "ETags are not accepted as cryptographic integrity proofs",
            "DeepPavlov 1.7.0 source code and configs declare Apache-2.0",
            "`microsoft/deberta-v3-base` declares MIT",
            "fine-tuned archive URLs have no dedicated model card or explicit checkpoint license",
            "CoNLL-2003 is marked `other`",
            "OntoNotes 5.0 is distributed under an LDC User Agreement",
            "`torch>=1.6.0,<1.14.0`",
            "`transformers==4.30.0`",
            "`sentencepiece==0.2.0`",
            "`protobuf<=3.20`",
            "`pytorch-crf==0.7.*`",
            "`POST /model` with `{\"x\":[\"bounded text\"]}`",
            "There is no dedicated hosted inference endpoint or SLA",
            "unsafe_side_effect_count",
            "at least 200 representative English articles and 500 manually labeled entities",
            "overall exact-span F1 is at least `0.85`",
            "precision for every enabled action-visible label is at least `0.90`",
            "Android remains an approval and observability client",
            "7 safe-pilot gates defined",
            "1/1 focused contract test passed",
            "0 model downloads, inference calls, dependencies, Android contracts or runtime files changed",
            "--rerun-tasks",
            "all `33/33` Gradle tasks executed",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing DeepPavlov NER audit evidence: $evidence", verification.contains(evidence))
        }

        assertFalse(
            "The task-supplied raw snapshot should be explicitly absent in this worktree",
            projectPath(
                "raw/monitored/deeppavlov-releases/" +
                    "20260709-234334-release-1-7-0-be517aab.md",
            ).exists(),
        )

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
            projectFile("gradle/libs.versions.toml"),
        ).joinToString("\n") { it.readText() }
        listOf("deeppavlov", "deberta", "pytorch-crf", "sentencepiece").forEach { dependency ->
            assertFalse(
                "Audit-only task must not add runtime dependency $dependency",
                gradleInputs.contains(dependency, ignoreCase = true),
            )
        }

        val sourceContract = projectFile(
            "app/src/main/java/com/soll/domain/soll/SollGateway.kt",
        ).readText()
        assertTrue(sourceContract.contains("data class SollSourceItem("))
        listOf("nerEntities", "DeepPavlov", "DeBERTa").forEach { productionSurface ->
            assertFalse(
                "Audit-only task must not add Android NER surface $productionSurface",
                sourceContract.contains(productionSurface, ignoreCase = true),
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
