package com.soll.domain.soll

/**
 * Side-effect-free content-generation proposal derived from a monitored source item.
 *
 * The Android app owns review and approval. Extraction and generation remain server-side and
 * require a separate approved execution path; this model never downloads, renders, publishes,
 * or invokes a tool.
 */
enum class ResearchContentExecutionBoundary {
    SERVER_PROPOSAL_ONLY,
}

enum class ResearchContentModule {
    SHARED_EVIDENCE_BUNDLE,
    DIGEST_DRAFT,
    ARTICLE_CARD_DRAFT,
    SECTION_ALIGNMENT,
    HARD_RELEASE_GATE,
}

enum class DeferredResearchContentModule {
    POSTER_RENDERER,
    VIDEO_RENDERER,
    DOCX_BLOG_RENDERER,
    INTERACTIVE_REEL,
}

data class ResearchContentStage(
    val id: String,
    val module: ResearchContentModule,
    val dependsOn: List<String>,
    val outputType: String,
)

data class ResearchContentStageResult(
    val stageId: String,
    val passed: Boolean,
    val bundleId: String,
    val evidenceRefs: List<String>,
)

data class ResearchContentReleaseDecision(
    val readyForHumanReview: Boolean,
    val reasons: List<String>,
) {
    /** Publication is intentionally outside this proposal-only Android contract. */
    val publicationAllowed: Boolean = false
}

data class ResearchContentPipelineProposal(
    val sourceItemId: String,
    val sourceUrl: String,
    val rawFile: String,
    val sourceEvidenceRefs: List<String>,
    val executionBoundary: ResearchContentExecutionBoundary,
    val requiresApproval: Boolean,
    val stages: List<ResearchContentStage>,
    val deferredModules: Set<DeferredResearchContentModule>,
) {
    init {
        require(sourceItemId.isNotBlank()) { "A content proposal requires a source item id" }
        require(sourceUrl.isNotBlank()) { "A content proposal requires a canonical source URL" }
        require(requiresApproval) { "Content generation must stay approval-gated" }
        require(stages.isNotEmpty()) { "A content proposal requires at least one stage" }
        require(stages.map { it.id }.distinct().size == stages.size) {
            "Content stage ids must be unique"
        }
        require(stages.last().module == ResearchContentModule.HARD_RELEASE_GATE) {
            "The hard release gate must be the terminal content stage"
        }

        val declared = mutableSetOf<String>()
        stages.forEach { stage ->
            require(stage.id.isNotBlank() && stage.outputType.isNotBlank()) {
                "Content stages require stable ids and output types"
            }
            require(stage.dependsOn.all(declared::contains)) {
                "Stage ${stage.id} must depend only on earlier stages"
            }
            declared += stage.id
        }
    }

    val executableOnAndroid: Boolean = false

    /**
     * Applies the hard gate to server-produced stage receipts without executing any stage.
     * Every non-gate stage must pass, cite durable evidence, and reuse the same bundle id.
     */
    fun review(results: List<ResearchContentStageResult>): ResearchContentReleaseDecision {
        val reasons = mutableListOf<String>()
        val expectedStages = stages.dropLast(1)
        val expectedIds = expectedStages.mapTo(linkedSetOf()) { it.id }
        val groupedResults = results.groupBy { it.stageId }

        groupedResults.filterValues { it.size > 1 }.keys.sorted().forEach { stageId ->
            reasons += "duplicate_result:$stageId"
        }
        groupedResults.keys.filterNot(expectedIds::contains).sorted().forEach { stageId ->
            reasons += "unknown_result:$stageId"
        }

        expectedStages.forEach { stage ->
            val result = groupedResults[stage.id]?.singleOrNull()
            when {
                result == null -> reasons += "missing_result:${stage.id}"
                !result.passed -> reasons += "failed_stage:${stage.id}"
                result.bundleId.isBlank() -> reasons += "missing_bundle:${stage.id}"
                result.evidenceRefs.none { it.isNotBlank() } -> reasons += "missing_evidence:${stage.id}"
            }
        }

        val bundleIds = results
            .filter { it.stageId in expectedIds }
            .map { it.bundleId.trim() }
            .filter(String::isNotBlank)
            .distinct()
        if (bundleIds.size > 1) reasons += "bundle_mismatch"

        return ResearchContentReleaseDecision(
            readyForHumanReview = reasons.isEmpty(),
            reasons = reasons.distinct(),
        )
    }
}

/**
 * Adapts the reusable ResearchStudio-Reel ideas to Soll's existing digest + article-card flow.
 * Heavy renderers stay explicitly deferred because this repository is the Android review client.
 */
object ResearchContentPipelinePlanner {
    fun propose(sourceItem: SollSourceItem): ResearchContentPipelineProposal =
        ResearchContentPipelineProposal(
            sourceItemId = sourceItem.itemId.trim(),
            sourceUrl = sourceItem.sourceUrl.trim(),
            rawFile = sourceItem.rawFile.trim(),
            sourceEvidenceRefs = listOf(
                sourceItem.rawFile,
                sourceItem.auditRef,
                sourceItem.evidenceRef,
                sourceItem.verificationArtifact,
            ).map(String::trim).filter(String::isNotBlank).distinct(),
            executionBoundary = ResearchContentExecutionBoundary.SERVER_PROPOSAL_ONLY,
            requiresApproval = true,
            stages = listOf(
                ResearchContentStage(
                    id = "shared_evidence_bundle",
                    module = ResearchContentModule.SHARED_EVIDENCE_BUNDLE,
                    dependsOn = emptyList(),
                    outputType = "grounded_source_bundle",
                ),
                ResearchContentStage(
                    id = "digest_draft",
                    module = ResearchContentModule.DIGEST_DRAFT,
                    dependsOn = listOf("shared_evidence_bundle"),
                    outputType = "content_draft",
                ),
                ResearchContentStage(
                    id = "article_card_draft",
                    module = ResearchContentModule.ARTICLE_CARD_DRAFT,
                    dependsOn = listOf("shared_evidence_bundle"),
                    outputType = "content_draft",
                ),
                ResearchContentStage(
                    id = "section_alignment",
                    module = ResearchContentModule.SECTION_ALIGNMENT,
                    dependsOn = listOf(
                        "shared_evidence_bundle",
                        "digest_draft",
                        "article_card_draft",
                    ),
                    outputType = "section_alignment",
                ),
                ResearchContentStage(
                    id = "hard_release_gate",
                    module = ResearchContentModule.HARD_RELEASE_GATE,
                    dependsOn = listOf(
                        "shared_evidence_bundle",
                        "digest_draft",
                        "article_card_draft",
                        "section_alignment",
                    ),
                    outputType = "review_package",
                ),
            ),
            deferredModules = DeferredResearchContentModule.entries.toSet(),
        )
}
