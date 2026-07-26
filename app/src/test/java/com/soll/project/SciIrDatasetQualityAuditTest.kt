package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SciIrDatasetQualityAuditTest {
    @Test
    fun `SciIR snapshot receipt and quality findings are complete and safely scoped`() {
        val audit = JSONObject(
            projectFile("docs/knowledge/sciir-82k-quality-audit-v1.json").readText(),
        )
        val knowledge = projectFile(
            "docs/knowledge/sciir-82k-quality-and-soll-applicability.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-43cda08a6d8897ca-verification.md",
        ).readText()
        val normalizedVerification = verification.replace(Regex("\\s+"), " ")

        assertEquals(1, audit.getInt("schema_version"))
        assertEquals("85529c3c86464d0c9be2d6bb774de4b1", audit.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/43cda08a6d8897ca",
            audit.getString("source_ref"),
        )
        assertEquals("untrusted_external_content", audit.getString("source_trust"))
        assertEquals("MAIR-Lab-HUST/SciIR-82k", audit.getString("dataset"))
        assertEquals(
            "51f7e778c690c9f738051bb9141cb80da488fffc",
            audit.getString("revision"),
        )

        val repository = audit.getJSONObject("repository_files")
        assertTrue(repository.getBoolean("checked"))
        assertEquals(89, repository.getInt("expected_file_count"))
        assertEquals(89, repository.getInt("observed_file_count"))
        assertEquals(24_961_674_409L, repository.getLong("expected_bytes"))
        assertEquals(24_961_674_409L, repository.getLong("observed_bytes"))
        assertEquals(0, repository.getJSONArray("missing_files").length())
        assertEquals(0, repository.getJSONArray("unexpected_files").length())
        assertEquals(0, repository.getJSONArray("integrity_mismatches").length())
        val receipts = repository.getJSONArray("receipts")
        assertEquals(89, receipts.length())
        repeat(receipts.length()) { index ->
            assertTrue(receipts.getJSONObject(index).getBoolean("matches_upstream"))
        }

        val captions = audit.getJSONObject("captions")
        assertEquals(82_189, captions.getInt("records"))
        assertEquals(82_189, captions.getInt("unique_filenames"))
        assertEquals(47_709, captions.getInt("unique_base_image_ids"))
        assertEquals(0, captions.getInt("duplicate_filenames"))
        assertEquals(0, captions.getInt("invalid_filename_patterns"))
        assertEquals(0, captions.getInt("blank_science_abstract_prompts"))
        assertEquals(0, captions.getInt("blank_sci_rcot"))
        assertEquals(0, captions.getInt("exact_duplicate_prompt_rows"))
        assertEquals(0, captions.getInt("exact_duplicate_sci_rcot_rows"))
        assertEquals(454, captions.getInt("records_without_nonempty_reasoning_dimensions"))
        val dimensions = captions.getJSONObject("reasoning_dimensions")
        assertEquals(
            3_637,
            dimensions.getJSONObject("ScientificLaw")
                .getInt("term_visualization_length_mismatches"),
        )
        assertEquals(
            4_934,
            dimensions.getJSONObject("EntityStructure")
                .getInt("term_visualization_length_mismatches"),
        )
        assertEquals(
            1_633,
            dimensions.getJSONObject("ScientificProcess")
                .getInt("term_visualization_length_mismatches"),
        )

        val metadata = audit.getJSONObject("metadata")
        assertEquals(47_709, metadata.getInt("records"))
        assertEquals(47_709, metadata.getInt("unique_image_ids"))
        assertEquals(0, metadata.getInt("duplicate_image_ids"))
        assertEquals(82_189, metadata.getInt("segments"))
        assertEquals(82_189, metadata.getInt("unique_segment_filenames"))
        assertEquals(2_967, metadata.getJSONObject("required_field_missing_records")
            .getInt("figure_caption"))
        assertEquals(1_175, metadata.getInt("noncanonical_subject_records"))
        assertEquals(5, metadata.getInt("verbose_subject_records_over_80_characters"))
        val subjectAnomalies = metadata.getJSONObject("noncanonical_subject_breakdown")
        assertEquals(1_164, subjectAnomalies.getInt("empty_or_missing"))
        assertEquals(6, subjectAnomalies.getInt("literal_none"))
        assertEquals(5, subjectAnomalies.getInt("verbose_over_80_characters"))
        assertEquals(0, subjectAnomalies.getInt("other"))
        assertEquals(47_709, metadata.getJSONObject("licenses").getInt("CC BY 4.0"))
        listOf(
            "caption_base_ids_without_metadata",
            "metadata_ids_without_captions",
            "caption_filenames_without_segments",
            "segment_filenames_without_captions",
        ).forEach { key -> assertEquals("Unexpected metadata link gap: $key", 0, metadata.getInt(key)) }

        val images = audit.getJSONObject("images")
        assertEquals(83, images.getInt("shards"))
        assertEquals(82_189, images.getInt("images"))
        assertEquals(0, images.getInt("duplicate_member_names"))
        assertEquals(0, images.getInt("unsafe_member_names"))
        assertEquals(0, images.getInt("invalid_png_count"))
        assertEquals(3_412, images.getInt("exact_duplicate_image_rows"))
        assertEquals(78_777, images.getInt("unique_image_content_hashes"))
        assertEquals(1_024, images.getJSONObject("width_pixels").getInt("min"))
        assertEquals(1_024, images.getJSONObject("width_pixels").getInt("max"))
        assertEquals(1_024, images.getJSONObject("height_pixels").getInt("min"))
        assertEquals(1_024, images.getJSONObject("height_pixels").getInt("max"))
        assertEquals(0, images.getJSONArray("shard_size_mismatches").length())
        assertEquals(0, images.getJSONArray("shard_sample_mismatches").length())
        assertEquals(0, images.getJSONArray("shard_boundary_mismatches").length())
        listOf(
            "caption_filenames_without_images",
            "images_without_captions",
            "metadata_segment_filenames_without_images",
            "images_without_metadata_segments",
        ).forEach { key -> assertEquals("Unexpected image link gap: $key", 0, images.getInt(key)) }

        listOf(
            "downloaded_and_audited_adoption_deferred_no_current_soll_workload",
            "89/89",
            "24,961,674,409",
            "terms[i]",
            "split by source article",
            "CameraX + ML Kit barcode/QR capture",
            "do not mix image-generation labels into the agent proxy suite",
            "No dataset-derived implementation should be promoted until all gates pass",
        ).forEach { decision ->
            assertTrue("Missing SciIR decision: $decision", knowledge.contains(decision))
        }
        assertFalse("Unresolved audit placeholder", knowledge.contains("{{"))

        listOf(
            "source_processing_result: downloaded_and_audited_adoption_deferred_no_current_soll_workload",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-43cda08a6d8897ca-verification.md",
            "source_value: full_pinned_dataset_receipt_plus_quality_baseline_and_soll_promotion_gate",
            "Dataset downloaded and preliminary quality analysis",
            "raw/monitored\\hugging-face-daily-papers\\20260702-190417-sciir-a-large-scale-training-dataset-and-benchma-00b72caa.md",
            "`0` Android production/dependency changes",
            "SciIrDatasetQualityAuditTest",
        ).forEach { evidence ->
            assertTrue(
                "Missing SciIR verification evidence: $evidence",
                normalizedVerification.contains(evidence),
            )
        }
        assertFalse("Unresolved verification placeholder", verification.contains("{{"))

        val productionInputs = buildList {
            add(projectFile("app/build.gradle.kts"))
            add(projectFile("settings.gradle.kts"))
            addAll(
                projectFile("app/src/main").walkTopDown()
                    .filter(File::isFile)
                    .filter { it.extension in setOf("kt", "xml", "json", "properties") }
                    .toList(),
            )
        }
        productionInputs.forEach { file ->
            val text = file.readText()
            assertFalse("SciIR was wired into production: ${file.path}", text.contains("SciIR-82k"))
            assertFalse(
                "SciIR model was wired into production: ${file.path}",
                text.contains("Qwen-Image-SciIR"),
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
