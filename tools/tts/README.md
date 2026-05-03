# ONNX TTS tooling (RU-first)

These scripts help you prepare model packs for Android **without committing weights**.

## 1) Generate RU model plan

```bash
python tools/tts/build_russian_model_plan.py
```

Creates `tools/tts/russian_model_plan.json`:
- `moss_nano_100m` as practical RU default (size/speed balance),
- `chatterbox_multilingual` as high-quality RU option (heavy).

## 2) Prepare pack metadata (and optional download)

```bash
python tools/tts/prepare_onnx_pack.py --model moss_nano_100m --precision fp32 --russian-only
python tools/tts/prepare_onnx_pack.py --model chatterbox_multilingual --precision int4 --russian-only
```

Optional download from Hugging Face:

```bash
pip install huggingface_hub
python tools/tts/prepare_onnx_pack.py --model moss_nano_100m --precision fp32 --russian-only --download
```

Output:
- `external_models/tts/<model>/<precision>/model_manifest.json`
- (if `--download`) model files in the same directory.
- Для Android runtime можно копировать эту папку в:
  - internal: `files/external_models/tts/...`
  - external app dir: `Android/data/<package>/files/tts_models/...`

## Notes

- Keep only code/config in git; model binaries must stay out of git.
- For Doogee S200 class devices:
  - default: `moss_nano_100m`
  - high quality (if acceptable latency/size): `chatterbox_multilingual` INT4.
