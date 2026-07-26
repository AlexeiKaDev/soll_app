package com.soll.domain.soll

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchContentPipelineTest {
    @Test
    fun `ResearchStudio patterns form a proposal only Soll content pipeline`() {
        val proposal = ResearchContentPipelinePlanner.propose(sourceItem())

        assertEquals("researchstudio-reel", proposal.sourceItemId)
        assertEquals(
            "https://huggingface.co/papers/2607.04438",
            proposal.sourceUrl,
        )
        assertEquals(ResearchContentExecutionBoundary.SERVER_PROPOSAL_ONLY, proposal.executionBoundary)
        assertTrue(proposal.requiresApproval)
        assertFalse(proposal.executableOnAndroid)
        assertEquals(
            listOf(
                ResearchContentModule.SHARED_EVIDENCE_BUNDLE,
                ResearchContentModule.DIGEST_DRAFT,
                ResearchContentModule.ARTICLE_CARD_DRAFT,
                ResearchContentModule.SECTION_ALIGNMENT,
                ResearchContentModule.HARD_RELEASE_GATE,
            ),
            proposal.stages.map { it.module },
        )
        assertEquals(
            listOf("shared_evidence_bundle"),
            proposal.stages.single { it.id == "digest_draft" }.dependsOn,
        )
        assertEquals(
            listOf("shared_evidence_bundle"),
            proposal.stages.single { it.id == "article_card_draft" }.dependsOn,
        )
        assertEquals(DeferredResearchContentModule.entries.toSet(), proposal.deferredModules)
        assertTrue(proposal.sourceEvidenceRefs.any { it.contains("raw/monitored") })
    }

    @Test
    fun `hard gate accepts one grounded bundle for human review but never publication`() {
        val proposal = ResearchContentPipelinePlanner.propose(sourceItem())
        val decision = proposal.review(passingResults(proposal, bundleId = "bundle-sha256-abc"))

        assertTrue(decision.readyForHumanReview)
        assertTrue(decision.reasons.isEmpty())
        assertFalse(decision.publicationAllowed)
    }

    @Test
    fun `hard gate rejects failed ungrounded and cross bundle outputs`() {
        val proposal = ResearchContentPipelinePlanner.propose(sourceItem())
        val results = passingResults(proposal, bundleId = "bundle-a").map { result ->
            when (result.stageId) {
                "digest_draft" -> result.copy(passed = false)
                "article_card_draft" -> result.copy(evidenceRefs = emptyList())
                "section_alignment" -> result.copy(bundleId = "bundle-b")
                else -> result
            }
        }

        val decision = proposal.review(results)

        assertFalse(decision.readyForHumanReview)
        assertFalse(decision.publicationAllowed)
        assertTrue(decision.reasons.contains("failed_stage:digest_draft"))
        assertTrue(decision.reasons.contains("missing_evidence:article_card_draft"))
        assertTrue(decision.reasons.contains("bundle_mismatch"))
    }

    @Test
    fun `hard gate rejects missing duplicate and unknown receipts`() {
        val proposal = ResearchContentPipelinePlanner.propose(sourceItem())
        val complete = passingResults(proposal, bundleId = "bundle-a")
        val malformed = complete
            .filterNot { it.stageId == "section_alignment" } +
            complete.first() +
            ResearchContentStageResult(
                stageId = "poster_renderer",
                passed = true,
                bundleId = "bundle-a",
                evidenceRefs = listOf("artifact:unexpected"),
            )

        val decision = proposal.review(malformed)

        assertFalse(decision.readyForHumanReview)
        assertTrue(decision.reasons.contains("missing_result:section_alignment"))
        assertTrue(decision.reasons.contains("duplicate_result:shared_evidence_bundle"))
        assertTrue(decision.reasons.contains("unknown_result:poster_renderer"))
    }

    @Test
    fun `full paper audit and value receipt stay attached to the pipeline`() {
        val knowledge = projectFile(
            "docs/knowledge/researchstudio-reel-soll-content-pipeline.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-605d37143f879378-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "arxiv:2607.04438v2",
            "32,898,632 bytes",
            "af5ccf02150a5b8a4845349fd142bfb3d91ed50fa048d2d4aaf7679ba918ac4e",
            "32,191,795 bytes",
            "b751c92857c5350e61f40c6f1a79d3c28903cc4e46e87991e257eb91f4d73571",
            "298ca64ae5e3f242d58278601db34bfa6daa53b8",
            "171 tracked files",
            "89.2 минуты",
            "## Критическая оценка evidence",
            "## Интегрированный Soll pipeline contract",
            "SHARED_EVIDENCE_BUNDLE",
            "HARD_RELEASE_GATE",
            "publicationAllowed` всегда `false",
            "## Что сознательно не импортировано",
        ).forEach { evidence ->
            assertTrue("Missing ResearchStudio audit evidence: $evidence", knowledge.contains(evidence))
        }

        listOf(
            "source_processing_result: " +
                "full_paper_downloaded_implementation_audited_content_contract_integrated",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-605d37143f879378-verification.md",
            "source_value:",
            "5 safe proposal/gate modules integrated",
            "4 heavy renderers deferred",
            "ResearchContentPipelineTest",
        ).forEach { evidence ->
            assertTrue("Missing ResearchStudio verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun passingResults(
        proposal: ResearchContentPipelineProposal,
        bundleId: String,
    ): List<ResearchContentStageResult> = proposal.stages.dropLast(1).map { stage ->
        ResearchContentStageResult(
            stageId = stage.id,
            passed = true,
            bundleId = bundleId,
            evidenceRefs = listOf("artifact:${stage.id}.json"),
        )
    }

    private fun sourceItem(): SollSourceItem = SollSourceItem(
        itemId = "researchstudio-reel",
        title = "ResearchStudio-Reel",
        sourceUrl = "https://huggingface.co/papers/2607.04438",
        contentPreview = "Paper to poster, video, and blog",
        summary = "Shared extraction and hard quality gates",
        usefulness = "Reusable content-pipeline patterns",
        reasoning = "Fits the source digest and article-card flow",
        evidenceLevel = "primary_source",
        projectFit = "partial",
        actionability = "proposal",
        dualUseRisk = "low",
        dualUseAction = "allow",
        safeNextStep = "Review the proposal",
        needsDeepDive = true,
        rawFile = "raw/monitored/hugging-face-daily-papers/researchstudio-reel.md",
        notifiedAt = "",
        lastStatus = "task_created",
        auditRef = "arxiv:2607.04438v2",
        evidenceRef = "source-item/9011e13c06d6/605d37143f879378",
        verificationArtifact = "Soll/outputs/source-processing/" +
            "source-item-9011e13c06d6-605d37143f879378-verification.md",
        statusReason = "",
        deliveryStatus = "chat_only",
    )

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
