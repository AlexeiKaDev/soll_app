#!/usr/bin/env python3
"""
Audit and quarantine noisy Piper dataset utterances.

The script is intentionally conservative: it removes only hard failures from the
active metadata/wav set and keeps every rejected file under rejected_audio_quality
for manual review or restore.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import shutil
from concurrent.futures import ProcessPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score


@dataclass
class AudioAuditRow:
    segment_id: str
    wav_path: str
    text: str
    duration: float
    rms_db: float
    peak_db: float
    noise_floor_db: float
    snr_db: float
    active_ratio: float
    flatness: float
    zcr: float
    centroid_hz: float
    bandwidth_hz: float
    onset_rate: float
    mfcc_silhouette: float
    mfcc_balance: float
    reject: bool
    review: bool
    reasons: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, default=None)
    parser.add_argument("--workers", type=int, default=max(1, min((os.cpu_count() or 4) - 1, 12)))
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--apply", action="store_true", help="Move rejected WAVs and rewrite metadata.csv.")
    parser.add_argument(
        "--include-review",
        action="store_true",
        help="Also quarantine borderline review rows. Use only after inspecting audit output.",
    )
    parser.add_argument(
        "--profile",
        choices=("light", "balanced", "strict"),
        default="balanced",
        help="Threshold profile for hard rejects.",
    )
    return parser.parse_args()


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


def db(value: float) -> float:
    return 20.0 * math.log10(max(float(value), 1e-9))


def median_or_zero(values: np.ndarray) -> float:
    return float(np.median(values)) if values.size else 0.0


def mean_or_zero(values: np.ndarray) -> float:
    return float(np.mean(values)) if values.size else 0.0


def profile_thresholds(profile: str) -> dict[str, float]:
    if profile == "light":
        return {
            "snr_hard": 8.0,
            "flatness_hard": 0.34,
            "zcr_hard": 0.22,
            "onset_hard": 6.5,
            "silhouette_hard": 0.58,
            "silhouette_review": 0.48,
        }
    if profile == "strict":
        return {
            "snr_hard": 13.0,
            "flatness_hard": 0.24,
            "zcr_hard": 0.17,
            "onset_hard": 4.2,
            "silhouette_hard": 0.48,
            "silhouette_review": 0.39,
        }
    return {
        "snr_hard": 10.5,
        "flatness_hard": 0.29,
        "zcr_hard": 0.19,
        "onset_hard": 5.2,
        "silhouette_hard": 0.53,
        "silhouette_review": 0.43,
    }


def audit_one(item: tuple[str, str, str, str]) -> AudioAuditRow:
    segment_id, text, wav_path, profile = item
    thresholds = profile_thresholds(profile)
    reasons: list[str] = []
    review_reasons: list[str] = []

    try:
        y, sr = sf.read(wav_path, always_2d=False)
        if y.ndim > 1:
            y = np.mean(y, axis=1)
        y = y.astype(np.float32, copy=False)
    except Exception as exc:  # noqa: BLE001
        return AudioAuditRow(
            segment_id=segment_id,
            wav_path=wav_path,
            text=text,
            duration=0.0,
            rms_db=-180.0,
            peak_db=-180.0,
            noise_floor_db=-180.0,
            snr_db=0.0,
            active_ratio=0.0,
            flatness=0.0,
            zcr=0.0,
            centroid_hz=0.0,
            bandwidth_hz=0.0,
            onset_rate=0.0,
            mfcc_silhouette=0.0,
            mfcc_balance=0.0,
            reject=True,
            review=False,
            reasons=f"read_error:{type(exc).__name__}",
        )

    duration = len(y) / float(sr or 1)
    if len(y) == 0 or duration <= 0.0:
        reasons.append("empty_audio")

    peak = float(np.max(np.abs(y))) if y.size else 0.0
    rms = float(np.sqrt(np.mean(np.square(y)))) if y.size else 0.0
    peak_db = db(peak)
    rms_db = db(rms)
    clip_ratio = float(np.mean(np.abs(y) >= 0.995)) if y.size else 0.0

    if duration < 2.0:
        reasons.append("too_short_audio")
    elif duration > 12.5:
        reasons.append("too_long_audio")
    if rms_db < -36.0:
        reasons.append("very_quiet")
    if clip_ratio > 0.001:
        reasons.append("clipping")

    frame_length = 2048
    hop_length = 512
    try:
        frame_rms = librosa.feature.rms(y=y, frame_length=frame_length, hop_length=hop_length)[0]
        frame_db = 20.0 * np.log10(np.maximum(frame_rms, 1e-9))
        noise_floor_db = float(np.percentile(frame_db, 12)) if frame_db.size else -180.0
        speech_level_db = float(np.percentile(frame_db, 82)) if frame_db.size else -180.0
        snr_db = speech_level_db - noise_floor_db
        active_threshold = max(noise_floor_db + 8.0, -42.0)
        active_ratio = float(np.mean(frame_db > active_threshold)) if frame_db.size else 0.0

        flatness = median_or_zero(librosa.feature.spectral_flatness(y=y, n_fft=frame_length, hop_length=hop_length)[0])
        zcr = mean_or_zero(librosa.feature.zero_crossing_rate(y, frame_length=frame_length, hop_length=hop_length)[0])
        centroid_hz = median_or_zero(
            librosa.feature.spectral_centroid(y=y, sr=sr, n_fft=frame_length, hop_length=hop_length)[0]
        )
        bandwidth_hz = median_or_zero(
            librosa.feature.spectral_bandwidth(y=y, sr=sr, n_fft=frame_length, hop_length=hop_length)[0]
        )
        onset_env = librosa.onset.onset_strength(y=y, sr=sr, hop_length=hop_length)
        onset_peaks = librosa.util.peak_pick(onset_env, pre_max=3, post_max=3, pre_avg=8, post_avg=8, delta=0.25, wait=4)
        onset_rate = float(len(onset_peaks) / max(duration, 0.001))

        mfcc_silhouette, mfcc_balance = mfcc_cluster_score(y, sr, frame_rms, frame_length, hop_length)
    except Exception as exc:  # noqa: BLE001
        noise_floor_db = -180.0
        snr_db = 0.0
        active_ratio = 0.0
        flatness = 0.0
        zcr = 0.0
        centroid_hz = 0.0
        bandwidth_hz = 0.0
        onset_rate = 0.0
        mfcc_silhouette = 0.0
        mfcc_balance = 0.0
        reasons.append(f"feature_error:{type(exc).__name__}")

    if active_ratio < 0.38:
        reasons.append("too_much_silence_or_music_bed")
    if snr_db < thresholds["snr_hard"] and noise_floor_db > -45.0:
        reasons.append("low_snr_noise")
    if flatness > thresholds["flatness_hard"] and snr_db < 18.0:
        reasons.append("broadband_noise")
    if zcr > thresholds["zcr_hard"] and centroid_hz > 2600.0:
        reasons.append("hiss_or_high_noise")
    if onset_rate > thresholds["onset_hard"] and active_ratio > 0.72:
        reasons.append("music_or_rhythmic_noise")
    if mfcc_silhouette > thresholds["silhouette_hard"] and 0.25 <= mfcc_balance <= 0.75 and duration >= 4.0:
        reasons.append("possible_multi_voice_or_music")
    elif mfcc_silhouette > thresholds["silhouette_review"] and 0.20 <= mfcc_balance <= 0.80 and duration >= 4.0:
        review_reasons.append("review_voice_change")

    lower_text = text.lower()
    if any(marker in lower_text for marker in ("читает сергей", "аудиокнига", "слушайте", "издательство")):
        review_reasons.append("review_intro_or_credit_text")

    reject = bool(reasons)
    review = bool(review_reasons) and not reject
    all_reasons = reasons + review_reasons

    return AudioAuditRow(
        segment_id=segment_id,
        wav_path=wav_path,
        text=text,
        duration=round(duration, 3),
        rms_db=round(rms_db, 2),
        peak_db=round(peak_db, 2),
        noise_floor_db=round(noise_floor_db, 2),
        snr_db=round(snr_db, 2),
        active_ratio=round(active_ratio, 4),
        flatness=round(float(flatness), 5),
        zcr=round(float(zcr), 5),
        centroid_hz=round(float(centroid_hz), 1),
        bandwidth_hz=round(float(bandwidth_hz), 1),
        onset_rate=round(float(onset_rate), 3),
        mfcc_silhouette=round(float(mfcc_silhouette), 4),
        mfcc_balance=round(float(mfcc_balance), 4),
        reject=reject,
        review=review,
        reasons=",".join(all_reasons),
    )


def mfcc_cluster_score(
    y: np.ndarray,
    sr: int,
    frame_rms: np.ndarray,
    frame_length: int,
    hop_length: int,
) -> tuple[float, float]:
    if y.size < sr * 3 or frame_rms.size < 16:
        return 0.0, 0.0
    threshold = float(np.percentile(frame_rms, 45))
    active = frame_rms > threshold
    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13, n_fft=frame_length, hop_length=hop_length)
    frames = mfcc[:, : active.size].T
    frames = frames[active[: frames.shape[0]]]
    if frames.shape[0] < 16:
        return 0.0, 0.0
    frames = frames[:160]
    std = np.std(frames, axis=0)
    std[std < 1e-6] = 1.0
    frames = (frames - np.mean(frames, axis=0)) / std
    labels = KMeans(n_clusters=2, n_init=5, random_state=17).fit_predict(frames)
    counts = np.bincount(labels, minlength=2).astype(np.float32)
    balance = float(np.min(counts) / np.sum(counts))
    if balance < 0.08:
        return 0.0, balance
    score = float(silhouette_score(frames, labels))
    return score, balance


def write_csv(path: Path, rows: list[AudioAuditRow]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(asdict(rows[0]).keys()) if rows else list(AudioAuditRow.__annotations__))
        writer.writeheader()
        for row in rows:
            writer.writerow(asdict(row))


def write_metadata(path: Path, rows: list[tuple[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for segment_id, text in rows:
            f.write(f"{segment_id}|{text}\n")


def main() -> int:
    args = parse_args()
    dataset = args.dataset.resolve()
    metadata_path = (args.metadata or dataset / "metadata.csv").resolve()
    wav_dir = dataset / "wav"

    metadata_rows = read_metadata(metadata_path)
    if args.limit:
        metadata_rows = metadata_rows[: args.limit]

    tasks = [
        (segment_id, text, str(wav_dir / f"{segment_id}.wav"), args.profile)
        for segment_id, text in metadata_rows
    ]

    audit_rows: list[AudioAuditRow] = []
    with ProcessPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(audit_one, task) for task in tasks]
        for i, future in enumerate(as_completed(futures), start=1):
            audit_rows.append(future.result())
            if i % 500 == 0:
                print(f"audited {i}/{len(futures)}")

    audit_rows.sort(key=lambda row: row.segment_id)
    reject_ids = {row.segment_id for row in audit_rows if row.reject or (args.include_review and row.review)}
    review_ids = {row.segment_id for row in audit_rows if row.review}
    accepted = [(segment_id, text) for segment_id, text in metadata_rows if segment_id not in reject_ids]
    rejected = [(segment_id, text) for segment_id, text in metadata_rows if segment_id in reject_ids]

    audit_path = dataset / "audio_quality_audit.csv"
    clean_metadata_path = dataset / "metadata.audio_cleaned.csv"
    rejected_metadata_path = dataset / "metadata.audio_rejected.csv"
    summary_path = dataset / "audio_quality_summary.json"

    write_csv(audit_path, audit_rows)
    write_metadata(clean_metadata_path, accepted)
    write_metadata(rejected_metadata_path, rejected)

    reason_counts: dict[str, int] = {}
    for row in audit_rows:
        for reason in filter(None, row.reasons.split(",")):
            reason_counts[reason] = reason_counts.get(reason, 0) + 1

    summary = {
        "dataset": str(dataset),
        "profile": args.profile,
        "apply": args.apply,
        "include_review": args.include_review,
        "input_rows": len(metadata_rows),
        "accepted": len(accepted),
        "rejected": len(rejected),
        "review_only": len(review_ids - reject_ids),
        "reason_counts": dict(sorted(reason_counts.items())),
        "audit": str(audit_path),
        "clean_metadata": str(clean_metadata_path),
        "rejected_metadata": str(rejected_metadata_path),
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.apply:
        backup = dataset / "metadata.before_audio_clean.csv"
        if not backup.exists():
            shutil.copy2(metadata_path, backup)
        rejected_wav_dir = dataset / "rejected_audio_quality" / "wav"
        rejected_wav_dir.mkdir(parents=True, exist_ok=True)
        for segment_id in sorted(reject_ids):
            src = wav_dir / f"{segment_id}.wav"
            dst = rejected_wav_dir / src.name
            if src.exists():
                if dst.exists():
                    dst.unlink()
                shutil.move(str(src), str(dst))
        write_metadata(metadata_path, accepted)

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
