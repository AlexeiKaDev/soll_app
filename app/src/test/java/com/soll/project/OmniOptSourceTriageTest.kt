package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniOptSourceTriageTest {
    @Test
    fun `full paper analysis is source traced complete and workload gated`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val analysis = projectFile(
            "docs/knowledge/omniopt-modern-optimizers-soll-applicability.md",
        ).readText()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-7b2f1963bbb1c876-verification.md",
        ).readText()
        val appBuild = projectFile("app/build.gradle.kts").readText()

        listOf(
            "omniopt-taxonomy-geometry-and-benchmarking-of-mo-2f762a3f",
            "desktop/server KB and evaluation cookbook",
            "AdamW as the reference",
            "2-3 alternatives by the binding quality/runtime/memory/stability constraint",
            "identical model, data, initialization/seeds, schedule and tuning budget",
            "verify code/license and target-architecture support",
            "must not choose or run training optimizers",
        ).forEach { decision ->
            assertTrue("Missing OmniOpt triage decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "0b6e52fa00ba4cd7849623a666d6795f",
            "source-item/9011e13c06d6/7b2f1963bbb1c876",
            "arxiv:2607.04033v1",
            "91-page survey and",
            "5,010,419 bytes",
            "62afd6af2d5463057172ec575d129d257447803b16b0a7d39bbe872351318a00",
            "4,187,716 bytes",
            "ed19007257f8fd0481ce44440ce3df9de59f9b87a45a1ff2327e279be1cf621d",
            "108 оптимизаторов",
            "15 subclasses",
            "S1 Routing",
            "S2 Transform",
            "S3 State evolution",
            "S4 Reconstruction",
            "S5 Finalization",
            "Axis I — update domain",
            "Axis II — state estimator",
            "Axis III — geometry/preconditioner",
            "Axis IV — finalization",
            "T1 element-wise",
            "T2 matrix structure",
            "T3 discretized direction",
            "T4 state compression",
            "T5 curvature/geometry",
            "O1 convergence",
            "O2 step cost",
            "O3 memory",
            "O4 stability",
            "O5 hyperparameter robustness",
            "O6 generalization",
            "24 optimizers",
            "FineWeb-Edu, sequence length 32k",
            "APOLLO ухудшился `13.53 -> 35.40`",
            "Выбрать не более 2–3 alternatives",
            "Android ONNX/Sherpa TTS",
            "0 optimizer packages",
            "0 training/inference runs",
            "0 production changes",
        ).forEach { evidence ->
            assertTrue("Missing OmniOpt full-paper evidence: $evidence", analysis.contains(evidence))
        }

        listOf(
            "full_paper_downloaded_methods_benchmarks_analyzed_soll_value_scoped",
            "source-item-9011e13c06d6-7b2f1963bbb1c876-verification.md",
            "Complete-download receipt",
            "Focused method and benchmark audit",
            "Four contours were evaluated",
            "1/1 focused contract test passed",
            "optimizer imports, training/inference runs and production changes: `0`",
        ).forEach { evidence ->
            assertTrue("Missing OmniOpt verification evidence: $evidence", artifact.contains(evidence))
        }

        assertTrue("Current Android ML path must remain inference-only", appBuild.contains("onnxruntime-android"))
        listOf("org.pytorch", "pytorch_android", "ai.djl.pytorch").forEach { trainingDependency ->
            assertFalse(
                "Unexpected Android training dependency: $trainingDependency",
                appBuild.contains(trainingDependency, ignoreCase = true),
            )
        }
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }
}
