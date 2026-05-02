"""Export UtrobinTTS High Quality Russian VITS to ONNX."""
import os
os.environ["PYTHONIOENCODING"] = "utf-8"

import torch
import json

print("Loading model...")
from transformers import VitsModel, AutoTokenizer

model_name = "utrobinmv/tts_ru_free_hf_vits_high_multispeaker"
model = VitsModel.from_pretrained(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)
model.eval()

sr = model.config.sampling_rate
print(f"Sample rate: {sr}, Speakers: {model.config.num_speakers}")

# Test
text = "Привет, это тест."
inputs = tokenizer(text, return_tensors="pt")
print(f"Tokens: {inputs['input_ids'].shape}")

with torch.no_grad():
    out = model(**inputs, speaker_id=torch.tensor([0]))
    print(f"Audio: {out.waveform.shape}")

# Wrapper
class W(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m
    def forward(self, input_ids, attention_mask, speaker_id):
        return self.m(input_ids=input_ids, attention_mask=attention_mask, speaker_id=speaker_id).waveform

w = W(model)
w.eval()

out_dir = "utrobin_onnx"
os.makedirs(out_dir, exist_ok=True)

print("Exporting ONNX (legacy)...")
torch.onnx.export(
    w,
    (inputs['input_ids'], inputs['attention_mask'], torch.tensor([0])),
    f"{out_dir}/model.onnx",
    input_names=['input_ids', 'attention_mask', 'speaker_id'],
    output_names=['waveform'],
    dynamic_axes={
        'input_ids': {1: 'seq'},
        'attention_mask': {1: 'seq'},
        'waveform': {2: 'samples'}
    },
    opset_version=14,
    dynamo=False
)

sz = os.path.getsize(f'{out_dir}/model.onnx') / 1024 / 1024
print(f"Saved: {out_dir}/model.onnx ({sz:.1f} MB)")

# Save vocab
with open(f"{out_dir}/vocab.json", "w", encoding="utf-8") as f:
    json.dump(tokenizer.get_vocab(), f, ensure_ascii=False, indent=2)

cfg = {"sample_rate": sr, "num_speakers": model.config.num_speakers, "speakers": ["Woman", "Man"]}
with open(f"{out_dir}/config.json", "w") as f:
    json.dump(cfg, f, indent=2)

print("Done!")
