#!/usr/bin/env python3
"""
Generate RU-focused model recommendation plan by quality vs size.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from pathlib import Path


@dataclass
class ModelPlanItem:
    model_id: str
    quality_score_ru: int
    size_mb_int4: int | None
    size_mb_fp16: int | None
    size_mb_fp32: int
    recommendation: str


def main() -> int:
    items = [
        ModelPlanItem(
            model_id="moss_nano_100m",
            quality_score_ru=6,
            size_mb_int4=None,
            size_mb_fp16=None,
            size_mb_fp32=673,
            recommendation="default_ru_offline",
        ),
        ModelPlanItem(
            model_id="chatterbox_multilingual",
            quality_score_ru=9,
            size_mb_int4=860,
            size_mb_fp16=1040,
            size_mb_fp32=4980,
            recommendation="high_quality_ru_if_storage_and_ram_allow",
        ),
    ]

    out_path = Path("tools/tts/russian_model_plan.json")
    out_path.write_text(
        json.dumps([asdict(i) for i in items], indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"Wrote {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
