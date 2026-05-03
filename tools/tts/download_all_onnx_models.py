#!/usr/bin/env python3
"""
Скачивает все TTS ONNX из MODEL_SPECS (prepare_onnx_pack.py) по очереди.

По умолчанию качается только нужная точность (fp32/fp16/int4) — без параллельной
подтяжки всех квантований (--legacy-broad-download раньше тянул всё *.onnx и легко
набиралось 30–40+ ГБ).

  pip install huggingface_hub
  python tools/tts/download_all_onnx_models.py
  python tools/tts/download_all_onnx_models.py --skip kokoro_82m

Для MOSS дополнительно: только нужные файлы из MOSS-Audio-Tokenizer-Nano-ONNX в
external_models/tts/moss_audio_tokenizer_nano/<precision>/

Kokoro: после snapshot голоса дополнительно докачиваются через hf_hub_download по
каждому voices/*.bin (Git LFS надёжнее, чем только allow_patterns).

Запускай из корня репозитория soll_app или из каталога tools/tts/.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parent
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from prepare_onnx_pack import (
    MODEL_SPECS,
    ModelSpec,
    download_moss_audio_tokenizer_pack,
    estimated_size_mb,
    maybe_download,
    save_manifest,
    verify_kokoro_voice_bins,
)

MOSS_TOKENIZER_DIRNAME = "moss_audio_tokenizer_nano"


def _precisions_for_model(spec: ModelSpec, requested: str) -> list[str]:
    if requested == "all":
        out: list[str] = ["fp32"]
        if spec.est_size_mb_fp16 is not None:
            out.append("fp16")
        if spec.est_size_mb_int4 is not None:
            out.append("int4")
        return out
    if requested == "fp16" and spec.est_size_mb_fp16 is None:
        return ["fp32"]
    if requested == "int4" and spec.est_size_mb_int4 is None:
        return ["fp32"]
    return [requested]


def download_one(
    spec: ModelSpec,
    precision: str,
    output_root: Path,
    *,
    legacy_broad_download: bool,
) -> None:
    out_dir = output_root / spec.model_id / precision
    save_manifest(spec, precision, out_dir)
    print(f"\n[{spec.model_id}/{precision}] {spec.repo_id} -> {out_dir}")
    maybe_download(
        spec,
        out_dir,
        precision=precision,
        legacy_broad_download=legacy_broad_download,
    )
    if spec.model_id == "kokoro_82m":
        verify_kokoro_voice_bins(out_dir)
    mb = estimated_size_mb(spec, precision)
    print(f"    (~{mb} MB est.) Done.")
    if spec.model_id == "moss_nano_100m":
        dest = output_root / MOSS_TOKENIZER_DIRNAME / precision
        print(f"  -> MOSS audio tokenizer pack -> {dest}")
        download_moss_audio_tokenizer_pack(dest)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Download all ONNX TTS packs from MODEL_SPECS")
    p.add_argument(
        "--output-dir",
        default="external_models/tts",
        help="Базовая директория (как в prepare_onnx_pack.py)",
    )
    p.add_argument(
        "--precision",
        default="fp32",
        choices=("fp32", "fp16", "int4", "all"),
        help="fp32 по умолчанию; all — все доступные варианты у каждой модели",
    )
    p.add_argument(
        "--legacy-broad-download",
        action="store_true",
        help="Тянуть все *.onnx в репозитории (очень большой объём, как раньше).",
    )
    p.add_argument(
        "--skip",
        action="append",
        default=[],
        metavar="MODEL_ID",
        help="Пропуск (можно указать несколько раз): python ... --skip kokoro_82m",
    )
    p.add_argument(
        "--only",
        nargs="*",
        default=None,
        metavar="MODEL_ID",
        help="Если указано — только эти модели (ids из MODEL_SPECS)",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    output_root = Path(args.output_dir)
    skip_set = frozenset(args.skip)
    model_ids = list(MODEL_SPECS.keys())
    if args.only:
        unknown = set(args.only) - set(MODEL_SPECS.keys())
        if unknown:
            raise SystemExit(f"Unknown model ids: {unknown}")
        model_ids = [m for m in args.only if m in MODEL_SPECS]

    total = len([m for m in model_ids if m not in skip_set])
    if total == 0:
        print("Nothing to download (all skipped?).")
        return 0

    print(f"Output root: {output_root.resolve()}")
    print(f"Precision mode: {args.precision}")
    print(f"Legacy broad (all ONNX in repo): {args.legacy_broad_download}")
    print(f"Models order: {[m for m in model_ids if m not in skip_set]}")

    for model_id in model_ids:
        if model_id in skip_set:
            print(f"\n(skip) {model_id}")
            continue
        spec = MODEL_SPECS[model_id]
        for precision in _precisions_for_model(spec, args.precision):
            try:
                download_one(
                    spec,
                    precision,
                    output_root,
                    legacy_broad_download=args.legacy_broad_download,
                )
            except Exception as e:
                print(f"\nERROR {model_id}/{precision}: {e}", file=sys.stderr)
                raise

    print("\nAll requested downloads finished. Не коммить веса в git.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
