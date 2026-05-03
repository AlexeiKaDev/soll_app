#!/usr/bin/env python3
"""
Prepare Android-ready ONNX TTS pack metadata (optionally download from Hugging Face).

Usage examples:
  python tools/tts/prepare_onnx_pack.py --model moss_nano_100m --precision fp32 --russian-only
  python tools/tts/prepare_onnx_pack.py --model chatterbox_multilingual --precision int4 --download
"""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

# Реальные style bins голосов Kokoro ~500 KiB; меньше — обычно Git LFS pointer.
MIN_KOKORO_VOICE_BYTES = 8192


@dataclass(frozen=True)
class ModelSpec:
    model_id: str
    repo_id: str
    languages: List[str]
    license_name: str
    est_size_mb_fp32: int
    est_size_mb_fp16: Optional[int]
    est_size_mb_int4: Optional[int]
    include_patterns: List[str]


MODEL_SPECS = {
    "chatterbox_multilingual": ModelSpec(
        model_id="chatterbox_multilingual",
        repo_id="onnx-community/chatterbox-multilingual-ONNX",
        languages=["ru", "en", "multi"],
        license_name="MIT",
        est_size_mb_fp32=4980,
        est_size_mb_fp16=1040,
        est_size_mb_int4=350,
        include_patterns=["*.onnx", "*.onnx_data", "*.json", "*.txt", "*.md"],
    ),
    "chatterbox_turbo": ModelSpec(
        model_id="chatterbox_turbo",
        repo_id="ResembleAI/chatterbox-turbo-ONNX",
        languages=["en"],
        license_name="MIT",
        est_size_mb_fp32=7390,
        est_size_mb_fp16=1660,
        est_size_mb_int4=720,
        include_patterns=["*.onnx", "*.onnx_data", "*.json", "*.txt", "*.md"],
    ),
    "kokoro_82m": ModelSpec(
        model_id="kokoro_82m",
        repo_id="onnx-community/Kokoro-82M-v1.0-ONNX",
        languages=["en"],
        license_name="Apache-2.0",
        est_size_mb_fp32=326,
        est_size_mb_fp16=163,
        est_size_mb_int4=92,
        include_patterns=["*.onnx", "*.onnx_data", "*.json", "*.txt", "*.md"],
    ),
    "moss_nano_100m": ModelSpec(
        model_id="moss_nano_100m",
        repo_id="OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX",
        languages=["ru", "en", "multi"],
        license_name="Apache-2.0",
        est_size_mb_fp32=673,
        est_size_mb_fp16=None,
        est_size_mb_int4=None,
        include_patterns=["*.onnx", "*.onnx_data", "*.json", "*.txt", "*.md"],
    ),
    "supertonic_tts_2": ModelSpec(
        model_id="supertonic_tts_2",
        repo_id="onnx-community/Supertonic-TTS-2-ONNX",
        languages=["en", "ko", "es", "pt", "fr"],
        license_name="OpenRAIL",
        est_size_mb_fp32=263,
        est_size_mb_fp16=None,
        est_size_mb_int4=None,
        include_patterns=["*.onnx", "*.onnx_data", "*.json", "*.txt", "*.md"],
    ),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, choices=MODEL_SPECS.keys())
    parser.add_argument("--precision", default="fp32", choices=["fp32", "fp16", "int4"])
    parser.add_argument("--output-dir", default="external_models/tts")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--russian-only", action="store_true")
    parser.add_argument(
        "--legacy-broad-download",
        action="store_true",
        help="Скачать все *.onnx в репо (FP32+FP16+INT4 сразу) — очень много ГБ.",
    )
    return parser.parse_args()


def estimated_size_mb(spec: ModelSpec, precision: str) -> int:
    if precision == "int4":
        return spec.est_size_mb_int4 or spec.est_size_mb_fp16 or spec.est_size_mb_fp32
    if precision == "fp16":
        return spec.est_size_mb_fp16 or spec.est_size_mb_fp32
    return spec.est_size_mb_fp32


RUNTIME_FAMILY_BY_MODEL = {
    "kokoro_82m": "kokoro_v1",
    "moss_nano_100m": "moss_v1_multigraph",
    "supertonic_tts_2": "supertonic_v2",
    "chatterbox_multilingual": "chatterbox_v1",
    "chatterbox_turbo": "chatterbox_turbo_v1",
}

# Прежнее поведение: тянуть все *.onnx (все квантования) — десятки ГБ.
LEGACY_DOWNLOAD_PATTERNS = ["*.onnx", "*.onnx_data", "*.json", "*.txt", "*.md"]

_TURBO_SUBGRAPHS = ("conditional_decoder", "embed_tokens", "language_model", "speech_encoder")

# multilingual: только language_model выпускает fp16/q4; остальные подграфы — базовые веса одни.
_MULTILINGUAL_DECODER_EMBED = [
    "onnx/conditional_decoder.onnx",
    "onnx/conditional_decoder.onnx_data",
    "onnx/embed_tokens.onnx",
    "onnx/embed_tokens.onnx_data",
]
_MULTILINGUAL_SPEECH_BASE = [
    "onnx/speech_encoder.onnx",
    "onnx/speech_encoder.onnx_data",
]
_MULTILINGUAL_LM_FP32 = [
    "onnx/language_model.onnx",
    "onnx/language_model.onnx_data",
]
_MULTILINGUAL_LM_FP16 = [
    "onnx/language_model_fp16.onnx",
    "onnx/language_model_fp16.onnx_data",
]
_MULTILINGUAL_LM_Q4 = [
    "onnx/language_model_q4.onnx",
    "onnx/language_model_q4.onnx_data",
]

SUPERTONIC_ONNX_FILES = [
    "onnx/latent_denoiser.onnx",
    "onnx/latent_denoiser.onnx_data",
    "onnx/text_encoder.onnx",
    "onnx/text_encoder.onnx_data",
    "onnx/voice_decoder.onnx",
    "onnx/voice_decoder.onnx_data",
]

MOSS_TTS_FILES = [
    "moss_tts_prefill.onnx",
    "moss_tts_decode_step.onnx",
    "moss_tts_global_shared.data",
    "moss_tts_local_decoder.onnx",
    "moss_tts_local_cached_step.onnx",
    "moss_tts_local_fixed_sampled_frame.onnx",
    "moss_tts_local_shared.data",
    "tokenizer.model",
    "tts_browser_onnx_meta.json",
    "browser_poc_manifest.json",
]

MOSS_AUDIO_TOKENIZER_FILES = [
    "moss_audio_tokenizer_decode_full.onnx",
    "moss_audio_tokenizer_decode_step.onnx",
    "moss_audio_tokenizer_decode_shared.data",
    "moss_audio_tokenizer_encode.onnx",
    "moss_audio_tokenizer_encode.data",
    "codec_browser_onnx_meta.json",
]


def _turbo_onnx_pairs(quant_suffix: str) -> list[str]:
    """В turbo у каждого подграфа есть одинаковый суффикс (fp32 / _fp16 / _q4 / …)."""
    out: list[str] = []
    for base in _TURBO_SUBGRAPHS:
        stem = f"onnx/{base}{quant_suffix}"
        out.append(f"{stem}.onnx")
        out.append(f"{stem}.onnx_data")
    return out


def _multilingual_onnx_patterns(precision: str) -> list[str]:
    p = precision.lower()
    stem = (
        _MULTILINGUAL_DECODER_EMBED
        + (_MULTILINGUAL_LM_FP16 if p == "fp16" else _MULTILINGUAL_LM_Q4 if p == "int4" else _MULTILINGUAL_LM_FP32)
        + _MULTILINGUAL_SPEECH_BASE
    )
    return stem


def _root_meta_patterns() -> list[str]:
    return [
        "README.md",
        ".gitattributes",
        "*.json",
        "*.md",
        "*.wav",
        "Cangjie5*.json",
    ]


def snapshot_allow_patterns(spec: ModelSpec, precision: str) -> list[str]:
    """Только нужные веса под выбранную точность (без параллельной загрузки всех KV)."""
    p = precision.lower()
    root = _root_meta_patterns()

    if spec.model_id == "kokoro_82m":
        if p == "fp16":
            weights = ["onnx/model_fp16.onnx", "onnx/model_fp16.onnx_data"]
        elif p == "int4":
            weights = ["onnx/model_quantized.onnx", "onnx/model_quantized.onnx_data"]
        else:
            weights = ["onnx/model.onnx", "onnx/model.onnx_data"]
        return weights + ["voices/*"] + root

    if spec.model_id == "chatterbox_multilingual":
        return _multilingual_onnx_patterns(p) + root

    if spec.model_id == "chatterbox_turbo":
        if p == "fp16":
            q = "_fp16"
        elif p == "int4":
            q = "_q4"
        else:
            q = ""
        return _turbo_onnx_pairs(q) + root + ["preprocessor_config.json"]

    if spec.model_id == "supertonic_tts_2":
        return SUPERTONIC_ONNX_FILES + root

    if spec.model_id == "moss_nano_100m":
        return MOSS_TTS_FILES + root

    return LEGACY_DOWNLOAD_PATTERNS


def save_manifest(spec: ModelSpec, precision: str, output_path: Path) -> None:
    output_path.mkdir(parents=True, exist_ok=True)
    manifest = {
        "modelId": spec.model_id,
        "repoId": spec.repo_id,
        "precision": precision,
        "license": spec.license_name,
        "languages": spec.languages,
        "estimatedSizeMb": estimated_size_mb(spec, precision),
        "androidLoadMode": "external_storage_only",
        "runtimeFamily": RUNTIME_FAMILY_BY_MODEL.get(spec.model_id, "unsupported"),
        "note": "Do not commit model weights. Keep only manifest/config in git.",
    }
    if spec.model_id == "kokoro_82m":
        manifest["kokoroVoice"] = "af_bella"
        manifest["note"] = (
            manifest["note"]
            + " Kokoro: also place hexgrad/Kokoro-82M config.json in this folder (see script)."
        )
    (output_path / "model_manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )


def download_moss_audio_tokenizer_pack(output_path: Path) -> None:
    """Второй репозиторий MOSS (аудио-кодек), без дубля всех весов основного."""
    try:
        from huggingface_hub import snapshot_download
    except Exception as exc:
        raise RuntimeError("pip install huggingface_hub") from exc

    output_path.mkdir(parents=True, exist_ok=True)
    readme = output_path / "_PAIR_WITH_MOSS_TTS_MAIN.txt"
    readme.write_text(
        "Companion pack for MOSS-TTS-Nano-100M-ONNX (encode/decode audio tokenizer ONNX).\n",
        encoding="utf-8",
    )
    snapshot_download(
        repo_id="OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX",
        local_dir=str(output_path),
        local_dir_use_symlinks=False,
        allow_patterns=MOSS_AUDIO_TOKENIZER_FILES + _root_meta_patterns(),
        resume_download=True,
    )


def maybe_download(
    spec: ModelSpec,
    output_path: Path,
    *,
    precision: str = "fp32",
    legacy_broad_download: bool = False,
) -> None:
    try:
        from huggingface_hub import hf_hub_download, snapshot_download
    except Exception as exc:
        raise RuntimeError(
            "huggingface_hub is required for --download. Install: pip install huggingface_hub"
        ) from exc

    allow = spec.include_patterns if legacy_broad_download else snapshot_allow_patterns(spec, precision)

    snapshot_download(
        repo_id=spec.repo_id,
        local_dir=str(output_path),
        local_dir_use_symlinks=False,
        allow_patterns=allow,
        resume_download=True,
    )
    if spec.model_id == "kokoro_82m":
        # Голоса в репозитории — Git LFS; только snapshot + allow_patterns часто даёт копию без blobs.
        ensure_kokoro_voice_bins_downloaded(spec.repo_id, output_path)
        # vocab для text→id берётся из PyTorch репозитория (KModel в kokoro использует этот config)
        hf_hub_download(
            repo_id="hexgrad/Kokoro-82M",
            filename="config.json",
            local_dir=str(output_path),
            local_dir_use_symlinks=False,
        )


def ensure_kokoro_voice_bins_downloaded(repo_id: str, output_path: Path) -> None:
    """Докачивает каждый voices/*.bin через hf_hub_download (надёжно для LFS)."""
    try:
        from huggingface_hub import hf_hub_download, list_repo_files
    except ImportError:
        raise RuntimeError("pip install huggingface_hub") from None

    try:
        all_files = list_repo_files(repo_id=repo_id, repo_type="model")
    except Exception as exc:
        print(f"WARNING: не удалось list_repo_files для {repo_id}: {exc}")
        return

    voice_files = sorted(
        f for f in all_files if f.startswith("voices/") and f.lower().endswith(".bin")
    )
    if not voice_files:
        print(f"WARNING: в {repo_id} не найдено paths voices/*.bin в индексе репозитория.")
        return

    voices_dir = output_path / "voices"
    voices_dir.mkdir(parents=True, exist_ok=True)
    skipped = ok = failures = 0
    print(f"Kokoro: проверка/докачка {len(voice_files)} voice .bin через hf_hub_download…")

    for rel in voice_files:
        dest = output_path / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.is_file() and dest.stat().st_size >= MIN_KOKORO_VOICE_BYTES:
            skipped += 1
            continue
        try:
            if dest.is_file() and dest.stat().st_size < MIN_KOKORO_VOICE_BYTES:
                dest.unlink(missing_ok=True)
            hf_hub_download(
                repo_id=repo_id,
                filename=rel,
                local_dir=str(output_path),
                local_dir_use_symlinks=False,
                resume_download=True,
            )
            if dest.is_file() and dest.stat().st_size >= MIN_KOKORO_VOICE_BYTES:
                ok += 1
            else:
                failures += 1
                print(
                    f"WARNING: после загрузки {rel} размер всё ещё "
                    f"{dest.stat().st_size if dest.is_file() else 0} B",
                )
        except Exception as err:
            failures += 1
            print(f"WARNING: не удалось скачать {rel}: {err}")

    print(f"Kokoro voices: пропуск (уже ОК): {skipped}, заново ок: {ok}, ошибки: {failures}")
    # Браузерам нужен хотя бы дефолтный голос
    fallback = voices_dir / "af_bella.bin"
    if not fallback.is_file() or fallback.stat().st_size < MIN_KOKORO_VOICE_BYTES:
        try:
            if fallback.is_file():
                fallback.unlink()
            hf_hub_download(
                repo_id=repo_id,
                filename="voices/af_bella.bin",
                local_dir=str(output_path),
                local_dir_use_symlinks=False,
                resume_download=True,
            )
        except Exception as err:
            print(f"WARNING: не удалось принудительно скачать voices/af_bella.bin: {err}")


def verify_kokoro_voice_bins(pack_root: Path) -> None:
    """Голоса в HF — Git LFS; без нормальной загрузки остаются pointer-файлы ~100 B."""
    voices = pack_root / "voices"
    if not voices.is_dir():
        print(
            "WARNING: нет voices/ — Kokoro на устройстве не заработает. "
            "Нужны *.bin из onnx-community/Kokoro-82M-v1.0-ONNX (huggingface_hub --download или git lfs)."
        )
        return
    bins = list(voices.glob("*.bin"))
    if not bins:
        print("WARNING: voices/*.bin пусто — проверьте загрузку с Hugging Face.")
        return
    small = [b for b in bins if b.stat().st_size < MIN_KOKORO_VOICE_BYTES]
    if small:
        ex = small[0]
        print(
            "WARNING: подозрение на Git LFS pointers, не реальные веса: "
            f"{ex.name} = {ex.stat().st_size} B (ожидается ≥{MIN_KOKORO_VOICE_BYTES} B, обычно сотни KiB). "
            "Переустановите huggingface_hub, запустите prepare_onnx_pack / download_all с --download "
            "(скрипт вызывает hf_hub_download для каждого voices/*.bin), либо `git lfs pull`."
        )


def main() -> int:
    args = parse_args()
    spec = MODEL_SPECS[args.model]

    if args.russian_only and "ru" not in spec.languages:
        raise SystemExit(f"Model '{args.model}' has no Russian support")

    out_dir = Path(args.output_dir) / args.model / args.precision
    save_manifest(spec, args.precision, out_dir)

    if args.download:
        maybe_download(
            spec,
            out_dir,
            precision=args.precision,
            legacy_broad_download=args.legacy_broad_download,
        )

    if spec.model_id == "kokoro_82m":
        verify_kokoro_voice_bins(out_dir)

    print(f"Prepared: {out_dir}")
    print(f"Repo: {spec.repo_id}")
    print(f"Estimated size: {estimated_size_mb(spec, args.precision)} MB")
    print("Reminder: keep weights out of git")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
