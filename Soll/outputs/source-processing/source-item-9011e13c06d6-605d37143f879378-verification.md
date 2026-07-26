---
task_id: 58f2b64172fd4bb7b00045fa4e858190
source_ref: source-item/9011e13c06d6/605d37143f879378
source_processing_result: full_paper_downloaded_implementation_audited_content_contract_integrated
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-605d37143f879378-verification.md
source_value: "arXiv v2 PDF and TeX source verified; 171 upstream files audited; 5 safe proposal/gate modules integrated; 4 heavy renderers deferred; 5/5 focused tests passed"
---

# ResearchStudio-Reel source-processing verification

## Scope

Source item: `ResearchStudio-Reel: Automate the Last Mile of Research from
Paper to Poster, Video, and Blog`.

The task-provided raw path is absent from this isolated worktree. The review
uses canonical Hugging Face, arXiv v2, and Microsoft ResearchStudio sources and
does not claim that local raw ingestion was observed.

## Full-paper receipt

| Check | Result |
| --- | --- |
| canonical version | `arxiv:2607.04438v2`, revised 2026-07-19 |
| PDF | 32,898,632 bytes; `%PDF-1.7` |
| PDF SHA-256 | `af5ccf02150a5b8a4845349fd142bfb3d91ed50fa048d2d4aaf7679ba918ac4e` |
| TeX source | 32,191,795 bytes; 49 entries |
| TeX SHA-256 | `b751c92857c5350e61f40c6f1a79d3c28903cc4e46e87991e257eb91f4d73571` |
| archive safety | 0 absolute/path-traversal entries before extraction |
| paper license | CC BY 4.0 |

The binary/source cache is intentionally ignored under
`build/source-processing/researchstudio-reel-2607.04438/`; the durable receipt
is this version/size/hash record.

## Implementation audit receipt

Official repository:
`https://github.com/microsoft/ResearchStudio/tree/298ca64ae5e3f242d58278601db34bfa6daa53b8/ResearchStudio-Reel`

| Check | Result |
| --- | --- |
| upstream commit | `298ca64ae5e3f242d58278601db34bfa6daa53b8` |
| code license | MIT |
| tracked scope | 171 files; 8,643,584 bytes |
| implementation | 64 Python files; 5 top-level skills plus nested `html2pptx` |
| regression tests | 2 test files; 41 test functions, concentrated in poster/html2pptx |
| external runtime | Poppler, LibreOffice, FFmpeg, Chromium, Edge TTS, LLM host |
| paper pipeline cost | mean 89.2 min, 2.568M fresh/cache-write input tokens and 276K output tokens per paper over 5 papers |

The paper quantitatively evaluates the poster on 100 papers with two VLM
judges. Video and blog are capability-audited, not benchmarked for quality or
human editing effort. This limits the evidence for wholesale adoption.

## Integrated slice

Changed production contract:
`app/src/main/java/com/soll/domain/soll/ResearchContentPipeline.kt`.

Five dependency-free modules now adapt the safe part of the design:

1. `SHARED_EVIDENCE_BUNDLE`;
2. `DIGEST_DRAFT`;
3. `ARTICLE_CARD_DRAFT`;
4. `SECTION_ALIGNMENT`;
5. `HARD_RELEASE_GATE`.

The hard gate rejects missing, failed, ungrounded, duplicated, unknown, and
cross-bundle receipts. A clean package becomes ready for human review only;
publication remains forbidden and Android execution remains disabled.

Four heavy upstream renderers are explicit deferrals: poster, video, DOCX blog,
and interactive reel. No Python/media dependency, upstream script, provider
credential, network action, task-board write, publish action, or generated
artifact was added.

## Focused verification

`ResearchContentPipelineTest` passed `5/5` focused tests through
`:app:testDebugUnitTest`:

- proposal stays server-only, approval-gated and non-executable on Android;
- stage ordering and the shared-bundle fan-out are fixed;
- a complete grounded single-bundle package becomes human-review ready but
  never publication-ready;
- failed, missing-evidence and cross-bundle receipts fail closed;
- missing, duplicate and unknown receipts fail closed;
- full-paper/code receipts and the three required value metric keys remain
  attached to durable artifacts.

## Value metric

- `source_processing_result`:
  `full_paper_downloaded_implementation_audited_content_contract_integrated`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-605d37143f879378-verification.md`
- `source_value`: full arXiv v2 PDF + TeX source verified with two SHA-256
  receipts; 171 upstream files / 64 Python files / 41 tests audited; 5 safe
  proposal/gate modules integrated; 4 desktop renderers deferred; 5/5 focused
  tests passed; runtime generation and publication value remain 0 until an
  approved server pilot.
