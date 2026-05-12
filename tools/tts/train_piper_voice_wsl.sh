#!/usr/bin/env bash
set -euo pipefail

# WSL/Linux launcher for Piper voice training.
# Defaults are tuned for a single RTX 5090 and a clean single-speaker Russian
# audiobook dataset. Batch is probed automatically because Piper/Lightning OOMs
# late if the longest utterance is too large.

ACTION="${1:-all}"

DATASET_DIR="${DATASET_DIR:-/mnt/d/Projects/soll_app/voice/datasets/burunov_full}"
TRAIN_DIR="${TRAIN_DIR:-/mnt/d/Projects/soll_app/voice/training/burunov_full}"
PIPER_REPO="${PIPER_REPO:-/mnt/d/Projects/piper}"
LANGUAGE="${LANGUAGE:-ru}"
QUALITY="${QUALITY:-high}"
TRAINING_MODE="${TRAINING_MODE:-scratch}"
SAMPLE_RATE="${SAMPLE_RATE:-22050}"
BATCH_SIZE="${BATCH_SIZE:-auto}"
AUTO_BATCH_CANDIDATES="${AUTO_BATCH_CANDIDATES:-48 40 32 24 16}"
LEARNING_RATE="${LEARNING_RATE:-0.0002}"
LR_DECAY="${LR_DECAY:-0.999875}"
MAX_PHONEME_IDS="${MAX_PHONEME_IDS:-400}"
NUM_WORKERS="${NUM_WORKERS:-32}"
MAX_EPOCHS="${MAX_EPOCHS:-2200}"
VALIDATION_SPLIT="${VALIDATION_SPLIT:-0.02}"
NUM_TEST_EXAMPLES="${NUM_TEST_EXAMPLES:-0}"
CHECKPOINT_EPOCHS="${CHECKPOINT_EPOCHS:-50}"
TEST_EVERY_EPOCHS="${TEST_EVERY_EPOCHS:-50}"
KEEP_RECENT_CHECKPOINTS="${KEEP_RECENT_CHECKPOINTS:-3}"
TEST_TEXT="${TEST_TEXT:-Это контрольный тест нового голоса. Чтение должно звучать ровно, без шума, без металлического дрожания и без ощущения, что голос идёт волнами. Мы специально используем длинный абзац, чтобы услышать дыхание фразы, паузы, интонацию и устойчивость тембра на протяжении пятнадцати или двадцати секунд.}"
VOICE_NAME="${VOICE_NAME:-burunov}"
PRECISION="${PRECISION:-32}"
CHECKPOINT="${CHECKPOINT:-}"
EXPORT_CKPT="${EXPORT_CKPT:-}"
INSTALL_SYSTEM_DEPS="${INSTALL_SYSTEM_DEPS:-0}"
TORCH_VERSION="${TORCH_VERSION:-2.7.1+cu128}"
TORCH_INDEX_URL="${TORCH_INDEX_URL:-https://download.pytorch.org/whl/cu128}"
PYPI_INDEX_URL="${PYPI_INDEX_URL:-https://pypi.org/simple}"
PYTHON_VERSION="${PYTHON_VERSION:-3.11}"
PIPER_GIT_URL="${PIPER_GIT_URL:-https://github.com/rhasspy/piper.git}"

export CUDA_VISIBLE_DEVICES="${CUDA_VISIBLE_DEVICES:-0}"
export TORCH_CUDA_ARCH_LIST="${TORCH_CUDA_ARCH_LIST:-12.0}"
export NUMBA_CACHE_DIR="${NUMBA_CACHE_DIR:-$TRAIN_DIR/.numba_cache}"
# Official Piper checkpoints are trusted here; PyTorch >=2.6 otherwise refuses
# old Lightning checkpoints because torch.load defaults to weights_only=True.
export TORCH_FORCE_NO_WEIGHTS_ONLY_LOAD="${TORCH_FORCE_NO_WEIGHTS_ONLY_LOAD:-1}"
export PYTHONWARNINGS="${PYTHONWARNINGS:-ignore:pkg_resources is deprecated as an API,ignore:.*weight_norm.*:FutureWarning,ignore:Trying to infer the .batch_size.:UserWarning}"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

TEST_WATCHER_PID=""

stop_test_watcher() {
  if [[ -n "$TEST_WATCHER_PID" ]] && kill -0 "$TEST_WATCHER_PID" >/dev/null 2>&1; then
    kill "$TEST_WATCHER_PID" >/dev/null 2>&1 || true
    wait "$TEST_WATCHER_PID" >/dev/null 2>&1 || true
  fi
}

trap stop_test_watcher EXIT

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

ensure_dataset() {
  [[ -f "$DATASET_DIR/metadata.csv" ]] || fail "metadata.csv not found: $DATASET_DIR"
  [[ -d "$DATASET_DIR/wav" ]] || fail "wav directory not found: $DATASET_DIR/wav"
}

install_system_deps() {
  if [[ "$INSTALL_SYSTEM_DEPS" != "1" ]]; then
    return
  fi
  log "Installing system deps via apt"
  sudo apt-get update
  sudo apt-get install -y python3-dev python3-venv build-essential git espeak-ng
}

ensure_uv() {
  export PATH="$HOME/.local/bin:$PATH"
  if command -v uv >/dev/null 2>&1; then
    return
  fi
  need_cmd python3
  log "Installing uv for Python $PYTHON_VERSION environment management"
  python3 -m pip install --user --break-system-packages uv
  export PATH="$HOME/.local/bin:$PATH"
  command -v uv >/dev/null 2>&1 || fail "uv installation failed"
}

setup_piper() {
  install_system_deps
  need_cmd git
  need_cmd python3
  ensure_uv

  if [[ ! -d "$PIPER_REPO/.git" ]]; then
    log "Cloning Piper into $PIPER_REPO"
    mkdir -p "$(dirname "$PIPER_REPO")"
    git clone "$PIPER_GIT_URL" "$PIPER_REPO"
  fi

  cd "$PIPER_REPO/src/python"
  if [[ -x ".venv/bin/python" ]]; then
    current_version="$(".venv/bin/python" - <<'PY'
import sys
print(f"{sys.version_info.major}.{sys.version_info.minor}")
PY
)"
    if [[ "$current_version" != "$PYTHON_VERSION" ]]; then
      log "Removing .venv built with Python $current_version; Piper training needs Python $PYTHON_VERSION"
      rm -rf .venv
    elif ! ".venv/bin/python" -m pip --version >/dev/null 2>&1; then
      log "Removing .venv without pip seed"
      rm -rf .venv
    fi
  fi
  log "Creating/updating Python $PYTHON_VERSION venv"
  uv python install "$PYTHON_VERSION"
  if [[ ! -x ".venv/bin/python" ]]; then
    uv venv --python "$PYTHON_VERSION" --seed .venv
  fi
  # shellcheck disable=SC1091
  source .venv/bin/activate
  python --version
  # Piper's pinned Lightning 1.7.x has metadata that pip >=24.1 rejects.
  python -m pip install --upgrade "pip<24.1" wheel "setuptools<81"

  log "Installing Blackwell-compatible PyTorch $TORCH_VERSION"
  python -m pip install --upgrade \
    "torch==$TORCH_VERSION" \
    --index-url "$TORCH_INDEX_URL" \
    --extra-index-url "$PYPI_INDEX_URL"

  log "Installing Piper training deps without forcing legacy torch<2"
  python -m pip install \
    "numpy<2" \
    "cython>=0.29.0,<1" \
    "piper-phonemize~=1.1.0" \
    "librosa>=0.9.2,<1" \
    "onnxruntime>=1.11.0" \
    "pytorch-lightning==1.7.7" \
    "torchmetrics<1" \
    "tensorboard" \
    "onnx" \
    "onnxsim"
  python -m pip install --no-deps -e .

  log "Building Piper monotonic align extension"
  bash build_monotonic_align.sh

  python - <<'PY'
import torch
print("torch", torch.__version__)
print("cuda", torch.cuda.is_available())
if torch.cuda.is_available():
    print("gpu", torch.cuda.get_device_name(0))
    print("capability", torch.cuda.get_device_capability(0))
PY
}

activate_piper() {
  [[ -f "$PIPER_REPO/src/python/.venv/bin/activate" ]] || fail "Piper venv not found. Run setup first."
  cd "$PIPER_REPO/src/python"
  # shellcheck disable=SC1091
  source .venv/bin/activate
}

preprocess_dataset() {
  ensure_dataset
  activate_piper
  mkdir -p "$TRAIN_DIR"
  log "Preprocessing dataset"
  python -m piper_train.preprocess \
    --language "$LANGUAGE" \
    --input-dir "$DATASET_DIR" \
    --output-dir "$TRAIN_DIR" \
    --dataset-format ljspeech \
    --single-speaker \
    --sample-rate "$SAMPLE_RATE"
}

train_model() {
  ensure_dataset
  activate_piper
  mkdir -p "$TRAIN_DIR"
  quality_args=()
  if [[ "$QUALITY" == "high" ]]; then
    quality_args+=(--quality high)
  fi
  resume_args=()
  case "$TRAINING_MODE" in
    scratch)
      [[ -z "$CHECKPOINT" ]] || fail "TRAINING_MODE=scratch must not use CHECKPOINT. Use TRAINING_MODE=finetune to resume."
      ;;
    finetune)
      [[ -n "$CHECKPOINT" ]] || fail "TRAINING_MODE=finetune requires CHECKPOINT=/path/to/checkpoint.ckpt"
      [[ -f "$CHECKPOINT" ]] || fail "Checkpoint not found: $CHECKPOINT"
      resume_args+=(--resume_from_checkpoint "$CHECKPOINT")
      ;;
    *)
      fail "Unknown TRAINING_MODE=$TRAINING_MODE. Use scratch|finetune"
      ;;
  esac

  select_batch_size

  start_test_watcher

  log "Training Piper mode=$TRAINING_MODE quality=$QUALITY batch=$BATCH_SIZE workers=$NUM_WORKERS lr=$LEARNING_RATE precision=$PRECISION gpu=$CUDA_VISIBLE_DEVICES"
  python -m piper_train \
    --dataset-dir "$TRAIN_DIR" \
    --accelerator gpu \
    --devices 1 \
    --batch-size "$BATCH_SIZE" \
    --num-workers "$NUM_WORKERS" \
    --learning-rate "$LEARNING_RATE" \
    --lr-decay "$LR_DECAY" \
    --validation-split "$VALIDATION_SPLIT" \
    --num-test-examples "$NUM_TEST_EXAMPLES" \
    --max_epochs "$MAX_EPOCHS" \
    --checkpoint-epochs "$CHECKPOINT_EPOCHS" \
    --precision "$PRECISION" \
    --max-phoneme-ids "$MAX_PHONEME_IDS" \
    "${resume_args[@]}" \
    "${quality_args[@]}"
}

select_batch_size() {
  if [[ "$BATCH_SIZE" != "auto" && "$BATCH_SIZE" != "0" ]]; then
    return
  fi

  log "Auto-selecting batch size candidates=$AUTO_BATCH_CANDIDATES"
  local candidate
  for candidate in $AUTO_BATCH_CANDIDATES; do
    log "Batch probe candidate=$candidate"
    if python -m piper_train \
      --dataset-dir "$TRAIN_DIR" \
      --accelerator gpu \
      --devices 1 \
      --batch-size "$candidate" \
      --num-workers "$NUM_WORKERS" \
      --learning-rate "$LEARNING_RATE" \
      --lr-decay "$LR_DECAY" \
      --validation-split "$VALIDATION_SPLIT" \
      --num-test-examples "$NUM_TEST_EXAMPLES" \
      --max_epochs 1 \
      --fast_dev_run True \
      --precision "$PRECISION" \
      --max-phoneme-ids "$MAX_PHONEME_IDS" \
      "${resume_args[@]}" \
      "${quality_args[@]}"; then
      BATCH_SIZE="$candidate"
      log "Selected batch size: $BATCH_SIZE"
      return
    fi
    log "Batch probe failed candidate=$candidate"
  done

  fail "No batch size candidate worked. Lower AUTO_BATCH_CANDIDATES or MAX_PHONEME_IDS."
}

checkpoint_epoch() {
  local checkpoint_name
  checkpoint_name="$(basename "$1")"
  if [[ "$checkpoint_name" =~ epoch=([0-9]+)- ]]; then
    echo "${BASH_REMATCH[1]}"
  fi
}

is_test_checkpoint_epoch() {
  local epoch="$1"
  (( epoch > 0 && (epoch % TEST_EVERY_EPOCHS == 0 || (epoch + 1) % TEST_EVERY_EPOCHS == 0) ))
}

generate_test_wav() {
  local ckpt="$1"
  local epoch="$2"
  local padded_epoch
  padded_epoch="$(printf "%04d" "$epoch")"
  local test_dir="$TRAIN_DIR/test_wavs"
  local export_dir="$TRAIN_DIR/test_exports/epoch_$padded_epoch"
  local wav_path="$test_dir/${VOICE_NAME}_${QUALITY}_epoch${padded_epoch}_test.wav"
  local marker="$test_dir/.epoch_${padded_epoch}.done"

  [[ -f "$marker" ]] && return
  mkdir -p "$test_dir" "$export_dir"

  log "Generating test WAV for epoch=$epoch checkpoint=$ckpt"
  if python -m piper_train.export_onnx "$ckpt" "$export_dir/model.onnx" \
    && cp "$TRAIN_DIR/config.json" "$export_dir/model.onnx.json" \
    && printf '%s\n' "$TEST_TEXT" | PYTHONPATH="$PIPER_REPO/src/python_run" python -m piper \
      --model "$export_dir/model.onnx" \
      --config "$export_dir/model.onnx.json" \
      --output-file "$wav_path"; then
    touch "$marker"
    log "Test WAV written: $wav_path"
  else
    log "Test WAV generation failed for epoch=$epoch; training continues"
  fi
}

watch_test_checkpoints() {
  local seen_dir="$TRAIN_DIR/test_wavs/.seen_checkpoints"
  mkdir -p "$seen_dir"
  while true; do
    while IFS= read -r ckpt; do
      [[ -f "$ckpt" ]] || continue
      local epoch
      epoch="$(checkpoint_epoch "$ckpt")"
      [[ -n "$epoch" ]] || continue
      if is_test_checkpoint_epoch "$epoch"; then
        local seen_key
        seen_key="$(printf '%s' "$ckpt" | sha1sum | awk '{print $1}')"
        if [[ ! -f "$seen_dir/$seen_key" ]]; then
          touch "$seen_dir/$seen_key"
          generate_test_wav "$ckpt" "$epoch"
        fi
      fi
    done < <(find "$TRAIN_DIR/lightning_logs" -path "*/checkpoints/*.ckpt" -type f 2>/dev/null | sort)
    prune_old_checkpoints
    sleep 30
  done
}

prune_old_checkpoints() {
  [[ "$KEEP_RECENT_CHECKPOINTS" -ge 0 ]] || return
  local index=0
  while IFS= read -r ckpt; do
    [[ -f "$ckpt" ]] || continue
    index=$((index + 1))
    if (( index <= KEEP_RECENT_CHECKPOINTS )); then
      continue
    fi
    local epoch
    epoch="$(checkpoint_epoch "$ckpt")"
    if [[ -n "$epoch" ]] && is_test_checkpoint_epoch "$epoch"; then
      continue
    fi
    rm -f "$ckpt" && log "Pruned old checkpoint: $ckpt"
  done < <(find "$TRAIN_DIR/lightning_logs" -path "*/checkpoints/*.ckpt" -type f -printf '%T@ %p\n' 2>/dev/null | sort -nr | cut -d' ' -f2-)
}

start_test_watcher() {
  if [[ "$TEST_EVERY_EPOCHS" -le 0 ]]; then
    return
  fi
  watch_test_checkpoints &
  TEST_WATCHER_PID="$!"
  log "Started test WAV watcher pid=$TEST_WATCHER_PID every=${TEST_EVERY_EPOCHS} epochs"
}

latest_checkpoint() {
  find "$TRAIN_DIR/lightning_logs" -path "*/checkpoints/*.ckpt" -printf '%T@ %p\n' 2>/dev/null \
    | sort -n \
    | tail -1 \
    | cut -d' ' -f2-
}

export_model() {
  activate_piper
  ckpt="$EXPORT_CKPT"
  if [[ -z "$ckpt" ]]; then
    ckpt="$(latest_checkpoint || true)"
  fi
  [[ -n "$ckpt" && -f "$ckpt" ]] || fail "No checkpoint found. Set EXPORT_CKPT=/path/to/model.ckpt"

  out_dir="${EXPORT_DIR:-$DATASET_DIR/export}"
  mkdir -p "$out_dir"
  log "Exporting $ckpt to $out_dir"
  voice_file="ru_RU-${VOICE_NAME}-${QUALITY}.onnx"
  python -m piper_train.export_onnx "$ckpt" "$out_dir/$voice_file"
  cp "$TRAIN_DIR/config.json" "$out_dir/$voice_file.json"
  generate_sherpa_pack_files "$out_dir" "$voice_file"
  log "Android Piper/Sherpa pack ready: $out_dir"
}

generate_sherpa_pack_files() {
  local out_dir="$1"
  local voice_file="$2"
  python - "$out_dir/$voice_file" "$out_dir/$voice_file.json" "$out_dir/tokens.txt" <<'PY'
import json
import sys
from pathlib import Path

import onnx

model_path = Path(sys.argv[1])
config_path = Path(sys.argv[2])
tokens_path = Path(sys.argv[3])

config = json.loads(config_path.read_text(encoding="utf-8"))
with tokens_path.open("w", encoding="utf-8", newline="\n") as tokens:
    for token, ids in config["phoneme_id_map"].items():
        if not ids:
            continue
        tokens.write(f"{token} {int(ids[0])}\n")

model = onnx.load(model_path)
while model.metadata_props:
    model.metadata_props.pop()
meta_data = {
    "model_type": "vits",
    "comment": "piper",
    "language": config.get("language", {}).get("name_english", "Russian"),
    "voice": config.get("espeak", {}).get("voice", "ru"),
    "has_espeak": 1,
    "n_speakers": config.get("num_speakers", 1),
    "sample_rate": config.get("audio", {}).get("sample_rate", 22050),
}
for key, value in meta_data.items():
    meta = model.metadata_props.add()
    meta.key = key
    meta.value = str(value)
onnx.save(model, model_path)
PY

  espeak_source="$(python - <<'PY'
from pathlib import Path
import piper_phonemize

print(Path(piper_phonemize.__file__).resolve().parent / "espeak-ng-data")
PY
)"
  [[ -d "$espeak_source" ]] || fail "piper_phonemize espeak-ng-data not found: $espeak_source"
  rm -rf "$out_dir/espeak-ng-data"
  cp -a "$espeak_source" "$out_dir/espeak-ng-data"
}

run_tensorboard() {
  activate_piper
  tensorboard --logdir "$TRAIN_DIR/lightning_logs" --host 0.0.0.0 --port "${TENSORBOARD_PORT:-6006}"
}

case "$ACTION" in
  setup)
    setup_piper
    ;;
  preprocess)
    preprocess_dataset
    ;;
  train)
    train_model
    ;;
  export)
    export_model
    ;;
  tensorboard)
    run_tensorboard
    ;;
  all)
    setup_piper
    preprocess_dataset
    train_model
    ;;
  *)
    fail "Unknown action: $ACTION. Use setup|preprocess|train|export|tensorboard|all"
    ;;
esac
