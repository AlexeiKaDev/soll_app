"""Run DeepPavlov 1.6.0 NER on synthetic Soll notes without network access.

The runner never installs packages or downloads model files. DeepPavlov 1.6.0
and the ``ner_bert_base`` model must already exist in an isolated local Python
environment. Use ``--self-check`` to validate the offline guard, fixtures and
BIO decoder without importing DeepPavlov.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Sequence


CONFIG_NAME = "ner_bert_base"
TEST_NOTES = (
    "Soll schedules a synthetic review in Chisinau on 30 July 2026.",
    "Тестовый проект Soll назначил встречу в Кишинёве на 30 июля 2026 года.",
)


def enable_offline_mode() -> None:
    """Disable supported remote lookups before DeepPavlov is imported."""

    offline_environment = {
        "HF_DATASETS_OFFLINE": "1",
        "HF_HUB_DISABLE_TELEMETRY": "1",
        "HF_HUB_OFFLINE": "1",
        "PIP_NO_INDEX": "1",
        "TOKENIZERS_PARALLELISM": "false",
        "TRANSFORMERS_OFFLINE": "1",
        "WANDB_MODE": "offline",
    }
    os.environ.update(offline_environment)

    def block_outbound_network(event: str, _arguments: object) -> None:
        if event in {"socket.connect", "socket.getaddrinfo"}:
            raise RuntimeError(
                f"Offline Soll NER smoke blocked outbound network event: {event}"
            )

    sys.addaudithook(block_outbound_network)


def decode_bio(tokens: Sequence[str], tags: Sequence[str]) -> list[dict[str, object]]:
    """Convert aligned BIO tags into compact entity spans."""

    if len(tokens) != len(tags):
        raise ValueError(
            f"DeepPavlov returned {len(tokens)} tokens but {len(tags)} tags"
        )

    entities: list[dict[str, object]] = []
    active_label: str | None = None
    active_tokens: list[str] = []
    active_start = 0

    def flush() -> None:
        nonlocal active_label, active_tokens, active_start
        if active_label is not None:
            entities.append(
                {
                    "label": active_label,
                    "text": " ".join(active_tokens),
                    "token_start": active_start,
                    "token_end": active_start + len(active_tokens),
                }
            )
        active_label = None
        active_tokens = []

    for index, (token, tag) in enumerate(zip(tokens, tags)):
        prefix, separator, label = tag.partition("-")
        if not separator or prefix not in {"B", "I"}:
            flush()
            continue

        if prefix == "B" or label != active_label:
            flush()
            active_label = label
            active_tokens = [token]
            active_start = index
        else:
            active_tokens.append(token)

    flush()
    return entities


def format_predictions(
    notes: Sequence[str],
    tokens_batch: Sequence[Sequence[str]],
    tags_batch: Sequence[Sequence[str]],
) -> list[dict[str, object]]:
    if not (len(notes) == len(tokens_batch) == len(tags_batch)):
        raise ValueError("DeepPavlov returned an unexpected batch shape")

    return [
        {
            "note_id": f"synthetic-{index + 1}",
            "text": note,
            "tokens": list(tokens),
            "tags": list(tags),
            "entities": decode_bio(tokens, tags),
        }
        for index, (note, tokens, tags) in enumerate(
            zip(notes, tokens_batch, tags_batch)
        )
    ]


def run_self_check() -> list[dict[str, object]]:
    tokens_batch = (
        (
            "Soll",
            "schedules",
            "a",
            "synthetic",
            "review",
            "in",
            "Chisinau",
            "on",
            "30",
            "July",
            "2026",
            ".",
        ),
        (
            "Тестовый",
            "проект",
            "Soll",
            "назначил",
            "встречу",
            "в",
            "Кишинёве",
            "на",
            "30",
            "июля",
            "2026",
            "года",
            ".",
        ),
    )
    tags_batch = (
        (
            "B-BUSINESS_NAME",
            "O",
            "O",
            "O",
            "O",
            "O",
            "B-CITY",
            "O",
            "B-DATETIME",
            "I-DATETIME",
            "I-DATETIME",
            "O",
        ),
        (
            "O",
            "O",
            "B-BUSINESS_NAME",
            "O",
            "O",
            "O",
            "B-CITY",
            "O",
            "B-DATETIME",
            "I-DATETIME",
            "I-DATETIME",
            "I-DATETIME",
            "O",
        ),
    )
    predictions = format_predictions(TEST_NOTES, tokens_batch, tags_batch)
    if sum(len(item["entities"]) for item in predictions) != 6:
        raise AssertionError("BIO self-check did not produce the six expected spans")
    return predictions


def run_local_model() -> list[dict[str, object]]:
    try:
        from deeppavlov import build_model
    except ModuleNotFoundError as error:
        raise SystemExit(
            "DeepPavlov is not installed in this local environment. Install "
            "DeepPavlov 1.6.0 and pre-cache ner_bert_base in an isolated "
            "environment before running this offline smoke."
        ) from error

    try:
        model = build_model(
            CONFIG_NAME,
            download=False,
            install=False,
        )
        tokens_batch, tags_batch = model(list(TEST_NOTES))
    except RuntimeError as error:
        if "Offline Soll NER smoke blocked" in str(error):
            raise SystemExit(str(error)) from error
        raise

    return format_predictions(TEST_NOTES, tokens_batch, tags_batch)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--self-check",
        action="store_true",
        help="Validate fixtures and BIO decoding without importing DeepPavlov.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    enable_offline_mode()
    predictions = run_self_check() if args.self_check else run_local_model()
    print(json.dumps(predictions, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
