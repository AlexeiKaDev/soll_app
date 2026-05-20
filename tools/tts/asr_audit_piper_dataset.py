#!/usr/bin/env python3
"""
ASR consistency audit for a Piper voice dataset.

The audit re-transcribes active WAV files, compares ASR text with metadata.csv,
and quarantines only suspicious rows when --apply is passed. Rejected audio is
never deleted permanently.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
import shutil
import subprocess
import wave
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path

DEFAULT_AI_MODEL_ROOT = Path(r"D:\AI\Models")


@dataclass
class AsrAuditRow:
    segment_id: str
    wav_path: str
    text: str
    asr_text: str
    duration: float
    chars_per_sec: float
    asr_chars_per_sec: float
    cer: float
    char_ratio: float
    word_overlap: float
    metadata_words: int
    asr_words: int
    reject: bool
    review: bool
    reasons: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, default=None)
    parser.add_argument("--model", default="medium")
    parser.add_argument("--language", default="ru")
    parser.add_argument("--device", default="auto", choices=("auto", "cuda", "cpu"))
    parser.add_argument("--compute-type", default=None)
    parser.add_argument("--beam-size", type=int, default=5)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--start-after", default=None)
    parser.add_argument("--save-every", type=int, default=100)
    parser.add_argument("--cache", type=Path, default=None)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--include-review", action="store_true")
    parser.add_argument("--force", action="store_true", help="Ignore existing ASR transcript cache.")
    parser.add_argument("--profile", choices=("light", "balanced", "strict"), default="balanced")
    return parser.parse_args()


def has_nvidia_smi() -> bool:
    try:
        subprocess.run(["nvidia-smi"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        return True
    except Exception:
        return False


def read_metadata(path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or "|" not in line:
                continue
            segment_id, text = line.split("|", 1)
            rows.append((segment_id.strip(), text.strip()))
    return rows


def write_metadata(path: Path, rows: list[tuple[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for segment_id, text in rows:
            f.write(f"{segment_id}|{text}\n")


def load_asr_cache(path: Path, force: bool) -> dict[str, str]:
    if force or not path.exists():
        return {}
    cache: dict[str, str] = {}
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            row = json.loads(line)
            segment_id = str(row.get("id") or "")
            if segment_id:
                cache[segment_id] = str(row.get("text") or "")
    return cache


def safe_model_name(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value).strip("_") or "model"


def shared_ai_model_root() -> Path:
    raw_roots = os.environ.get("SOLL_AI_MODEL_ROOTS") or os.environ.get("AI_MODEL_ROOTS") or str(DEFAULT_AI_MODEL_ROOT)
    for part in re.split(r"[;\n]+", raw_roots):
        value = part.strip().strip("\"'")
        if value:
            return Path(value)
    return DEFAULT_AI_MODEL_ROOT


def whisper_download_root() -> Path:
    return shared_ai_model_root() / "audio" / "whisper"


def resolve_whisper_model(model_name: str) -> tuple[str, Path]:
    download_root = whisper_download_root()
    name = model_name.strip() or "base"
    candidates: list[Path] = []
    if Path(name).is_absolute() or any(separator in name for separator in ("/", "\\")):
        candidates.append(Path(name))

    normalized = name.removeprefix("Systran/").removeprefix("Systran\\")
    aliases = [normalized]
    if not normalized.startswith("faster-whisper-"):
        aliases.append(f"faster-whisper-{normalized}")
    candidates.extend(download_root / alias for alias in aliases)

    for candidate in candidates:
        if (candidate / "model.bin").exists() and (candidate / "config.json").exists():
            return str(candidate), download_root

    return name, download_root


def append_asr_cache(path: Path, segment_id: str, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps({"id": segment_id, "text": text}, ensure_ascii=False) + "\n")


def normalize_asr_text(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace("\u00a0", " ").strip())


def normalize_compare_text(text: str) -> str:
    text = text.lower().replace("ё", "е")
    text = text.replace("—", " ").replace("–", " ")
    text = re.sub(r"[^а-яa-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def words(text: str) -> list[str]:
    return normalize_compare_text(text).split()


def levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    if len(a) < len(b):
        a, b = b, a
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, start=1):
        current = [i]
        for j, cb in enumerate(b, start=1):
            current.append(
                min(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + (ca != cb),
                )
            )
        previous = current
    return previous[-1]


def char_ratio(ref: str, hyp: str) -> float:
    ref_n = normalize_compare_text(ref).replace(" ", "")
    hyp_n = normalize_compare_text(hyp).replace(" ", "")
    denom = max(len(ref_n), len(hyp_n), 1)
    return max(0.0, 1.0 - levenshtein(ref_n, hyp_n) / denom)


def cer(ref: str, hyp: str) -> float:
    ref_n = normalize_compare_text(ref).replace(" ", "")
    hyp_n = normalize_compare_text(hyp).replace(" ", "")
    return levenshtein(ref_n, hyp_n) / max(len(ref_n), 1)


def word_overlap(ref: str, hyp: str) -> float:
    ref_words = Counter(words(ref))
    hyp_words = Counter(words(hyp))
    if not ref_words and not hyp_words:
        return 1.0
    if not ref_words or not hyp_words:
        return 0.0
    overlap = sum((ref_words & hyp_words).values())
    return overlap / max(sum(ref_words.values()), sum(hyp_words.values()), 1)


def duration_seconds(path: Path) -> float:
    with wave.open(str(path), "rb") as wav:
        return wav.getnframes() / float(wav.getframerate() or 1)


def threshold_values(profile: str) -> dict[str, float]:
    if profile == "light":
        return {
            "cer_reject": 0.46,
            "overlap_reject": 0.46,
            "ratio_reject": 0.56,
            "cer_review": 0.34,
            "overlap_review": 0.60,
            "ratio_review": 0.68,
        }
    if profile == "strict":
        return {
            "cer_reject": 0.31,
            "overlap_reject": 0.60,
            "ratio_reject": 0.70,
            "cer_review": 0.22,
            "overlap_review": 0.72,
            "ratio_review": 0.79,
        }
    return {
        "cer_reject": 0.38,
        "overlap_reject": 0.53,
        "ratio_reject": 0.64,
        "cer_review": 0.27,
        "overlap_review": 0.67,
        "ratio_review": 0.74,
    }


def classify(segment_id: str, text: str, asr_text: str, duration: float, profile: str) -> AsrAuditRow:
    thresholds = threshold_values(profile)
    reasons: list[str] = []
    review_reasons: list[str] = []
    text_n = normalize_compare_text(text)
    asr_n = normalize_compare_text(asr_text)
    metadata_words = len(text_n.split())
    asr_words = len(asr_n.split())
    c = cer(text, asr_text)
    ratio = char_ratio(text, asr_text)
    overlap = word_overlap(text, asr_text)
    chars_per_sec = len(text_n.replace(" ", "")) / max(duration, 0.001)
    asr_chars_per_sec = len(asr_n.replace(" ", "")) / max(duration, 0.001)
    lower_text = text.lower()
    lower_asr = asr_text.lower()

    if not asr_n or asr_words <= 1:
        reasons.append("empty_or_tiny_asr")
    if not text_n or metadata_words <= 1:
        reasons.append("empty_or_tiny_metadata")

    hard_markers = (
        "аудиокнига",
        "издательство",
        "читает сергей",
        "читает ",
        "текст читает",
        "слушайте",
        "субтитры",
        "создавал dimatorzok",
        "продолжение следует",
    )
    if any(marker in lower_text for marker in hard_markers) or any(marker in lower_asr for marker in hard_markers):
        reasons.append("service_or_credit_text")

    chapter_start = re.compile(r"^\s*(часть|глава|пролог|эпилог)\b", re.IGNORECASE)
    if chapter_start.search(lower_text) and metadata_words <= 9:
        reasons.append("chapter_marker_only")

    if text.rstrip().endswith(("...", "…")):
        reasons.append("truncated_metadata_ellipsis")

    latin_count = len(re.findall(r"[a-zA-Z]", text))
    if latin_count >= 3 and latin_count / max(len(text), 1) > 0.08:
        reasons.append("latin_noise")

    digits_symbols = len(re.findall(r"[0-9#@*_=/\\]", text))
    if digits_symbols >= 4 and digits_symbols / max(len(text), 1) > 0.08:
        reasons.append("symbol_noise")

    if chars_per_sec < 2.0 or chars_per_sec > 24.0:
        review_reasons.append("metadata_duration_mismatch")
    if asr_chars_per_sec < 2.0 or asr_chars_per_sec > 24.0:
        review_reasons.append("asr_duration_mismatch")

    if repeated_word_run(words(text)) >= 4:
        reasons.append("repeated_metadata_words")
    if repeated_word_run(words(asr_text)) >= 4:
        reasons.append("repeated_asr_words")

    if metadata_words >= 4 and asr_words >= 4:
        if c > thresholds["cer_reject"] and overlap < thresholds["overlap_reject"] and ratio < thresholds["ratio_reject"]:
            reasons.append("asr_text_mismatch")
        elif c > thresholds["cer_review"] or overlap < thresholds["overlap_review"] or ratio < thresholds["ratio_review"]:
            review_reasons.append("review_asr_mismatch")
    elif c > 0.55 and overlap < 0.45:
        review_reasons.append("review_short_mismatch")

    reject = bool(reasons)
    review = bool(review_reasons) and not reject
    return AsrAuditRow(
        segment_id=segment_id,
        wav_path=f"wav/{segment_id}.wav",
        text=text,
        asr_text=asr_text,
        duration=round(duration, 3),
        chars_per_sec=round(chars_per_sec, 3),
        asr_chars_per_sec=round(asr_chars_per_sec, 3),
        cer=round(c, 4),
        char_ratio=round(ratio, 4),
        word_overlap=round(overlap, 4),
        metadata_words=metadata_words,
        asr_words=asr_words,
        reject=reject,
        review=review,
        reasons=",".join(reasons + review_reasons),
    )


def repeated_word_run(items: list[str]) -> int:
    longest = 0
    current_word = None
    current = 0
    for item in items:
        if item == current_word:
            current += 1
        else:
            current_word = item
            current = 1
        longest = max(longest, current)
    return longest


def write_csv(path: Path, rows: list[AsrAuditRow]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(AsrAuditRow.__annotations__))
        writer.writeheader()
        for row in rows:
            writer.writerow(asdict(row))


def update_dataset_stats(dataset: Path, active_rows: list[tuple[str, str]]) -> None:
    durations: list[float] = []
    for segment_id, _text in active_rows:
        wav_path = dataset / "wav" / f"{segment_id}.wav"
        if wav_path.exists():
            durations.append(duration_seconds(wav_path))
    if not durations:
        return
    durations_sorted = sorted(durations)
    stats_path = dataset / "dataset_stats.json"
    previous = {}
    if stats_path.exists():
        try:
            previous = json.loads(stats_path.read_text(encoding="utf-8"))
        except Exception:
            previous = {}
    stats = {
        **previous,
        "accepted": len(active_rows),
        "accepted_hours": round(sum(durations) / 3600.0, 3),
        "accepted_duration_min": round(min(durations), 3),
        "accepted_duration_avg": round(sum(durations) / len(durations), 3),
        "accepted_duration_median": round(durations_sorted[len(durations_sorted) // 2], 3),
        "accepted_duration_max": round(max(durations), 3),
        "asr_cleaned": True,
        "asr_rejected": len(list((dataset / "rejected_asr_quality" / "wav").glob("*.wav"))),
        "asr_review_rejected": len(list((dataset / "rejected_asr_review_quality" / "wav").glob("*.wav"))),
        "metadata": str(dataset / "metadata.csv"),
        "asr_audit": str(dataset / "asr_quality_audit.csv"),
        "asr_summary": str(dataset / "asr_quality_summary.json"),
    }
    stats_path.write_text(json.dumps(stats, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    args = parse_args()
    dataset = args.dataset.resolve()
    metadata_path = (args.metadata or dataset / "metadata.csv").resolve()
    wav_dir = dataset / "wav"
    transcript_cache_path = args.cache or dataset / f"asr_quality_transcripts_{safe_model_name(args.model)}.jsonl"

    metadata_rows = read_metadata(metadata_path)
    if args.start_after:
        seen = False
        filtered = []
        for row in metadata_rows:
            if seen:
                filtered.append(row)
            elif row[0] == args.start_after:
                seen = True
        metadata_rows = filtered
    if args.limit:
        metadata_rows = metadata_rows[: args.limit]

    device = "cuda" if args.device == "auto" and has_nvidia_smi() else args.device
    if device == "auto":
        device = "cpu"
    compute_type = args.compute_type or ("float16" if device == "cuda" else "int8")

    cache = load_asr_cache(transcript_cache_path, force=args.force)
    missing = [(segment_id, text) for segment_id, text in metadata_rows if segment_id not in cache]
    print(
        json.dumps(
            {
                "dataset": str(dataset),
                "rows": len(metadata_rows),
                "cached": len(metadata_rows) - len(missing),
                "to_transcribe": len(missing),
                "model": args.model,
                "device": device,
                "compute_type": compute_type,
                "profile": args.profile,
            },
            ensure_ascii=False,
        )
    )

    if missing:
        try:
            from faster_whisper import WhisperModel
        except ImportError as exc:
            raise SystemExit("faster-whisper is not installed") from exc

        model_ref, download_root = resolve_whisper_model(args.model)
        download_root.mkdir(parents=True, exist_ok=True)
        model = WhisperModel(
            model_ref,
            device=device,
            compute_type=compute_type,
            download_root=str(download_root),
        )
        for index, (segment_id, _text) in enumerate(missing, start=1):
            wav_path = wav_dir / f"{segment_id}.wav"
            segments, _info = model.transcribe(
                str(wav_path),
                language=args.language,
                beam_size=args.beam_size,
                vad_filter=False,
            )
            asr_text = normalize_asr_text(" ".join(segment.text.strip() for segment in segments))
            cache[segment_id] = asr_text
            append_asr_cache(transcript_cache_path, segment_id, asr_text)
            if index == 1 or index % args.save_every == 0:
                print(f"transcribed {index}/{len(missing)} {segment_id}: {asr_text[:100]}")

    audit_rows: list[AsrAuditRow] = []
    for segment_id, text in metadata_rows:
        wav_path = wav_dir / f"{segment_id}.wav"
        duration = duration_seconds(wav_path) if wav_path.exists() else 0.0
        audit_rows.append(classify(segment_id, text, cache.get(segment_id, ""), duration, args.profile))

    reject_ids = {row.segment_id for row in audit_rows if row.reject}
    review_ids = {row.segment_id for row in audit_rows if row.review}
    apply_ids = set(reject_ids)
    if args.include_review:
        apply_ids |= review_ids

    accepted = [(segment_id, text) for segment_id, text in metadata_rows if segment_id not in apply_ids]
    rejected = [(segment_id, text) for segment_id, text in metadata_rows if segment_id in reject_ids]
    review = [(segment_id, text) for segment_id, text in metadata_rows if segment_id in review_ids]

    audit_path = dataset / "asr_quality_audit.csv"
    clean_metadata_path = dataset / "metadata.asr_cleaned.csv"
    rejected_metadata_path = dataset / "metadata.asr_rejected.csv"
    review_metadata_path = dataset / "metadata.asr_review.csv"
    summary_path = dataset / "asr_quality_summary.json"

    write_csv(audit_path, audit_rows)
    write_metadata(clean_metadata_path, accepted)
    write_metadata(rejected_metadata_path, rejected)
    write_metadata(review_metadata_path, review)

    reason_counts: dict[str, int] = {}
    for row in audit_rows:
        for reason in filter(None, row.reasons.split(",")):
            reason_counts[reason] = reason_counts.get(reason, 0) + 1

    summary = {
        "dataset": str(dataset),
        "model": args.model,
        "device": device,
        "compute_type": compute_type,
        "profile": args.profile,
        "apply": args.apply,
        "include_review": args.include_review,
        "input_rows": len(metadata_rows),
        "accepted": len(accepted),
        "rejected": len(rejected),
        "review_only": len(review_ids - reject_ids),
        "applied_remove_count": len(apply_ids) if args.apply else 0,
        "reason_counts": dict(sorted(reason_counts.items())),
        "audit": str(audit_path),
        "clean_metadata": str(clean_metadata_path),
        "rejected_metadata": str(rejected_metadata_path),
        "review_metadata": str(review_metadata_path),
        "transcript_cache": str(transcript_cache_path),
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.apply:
        backup = dataset / "metadata.before_asr_clean.csv"
        if not backup.exists():
            shutil.copy2(metadata_path, backup)
        reject_dir = dataset / "rejected_asr_quality" / "wav"
        review_reject_dir = dataset / "rejected_asr_review_quality" / "wav"
        reject_dir.mkdir(parents=True, exist_ok=True)
        review_reject_dir.mkdir(parents=True, exist_ok=True)
        for segment_id in sorted(apply_ids):
            src = wav_dir / f"{segment_id}.wav"
            dst_root = review_reject_dir if segment_id in review_ids and segment_id not in reject_ids else reject_dir
            dst = dst_root / src.name
            if src.exists():
                if dst.exists():
                    dst.unlink()
                shutil.move(str(src), str(dst))
        write_metadata(metadata_path, accepted)
        update_dataset_stats(dataset, accepted)

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
