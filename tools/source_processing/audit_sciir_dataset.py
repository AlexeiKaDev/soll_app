#!/usr/bin/env python3
"""Offline integrity and quality audit for a downloaded SciIR-82k snapshot."""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import math
import re
import statistics
import struct
import tarfile
import urllib.parse
import zlib
from pathlib import Path
from typing import Any, Iterable, Iterator


DIMENSIONS = ("ScientificLaw", "EntityStructure", "ScientificProcess")
CAPTION_NAME = re.compile(r"^(img_\d{6})_(\d{2})\.png$")
METADATA_ID = re.compile(r"^img_\d{6}$")
REQUIRED_METADATA_FIELDS = (
    "image_id",
    "article_title",
    "article_abstract",
    "article_body",
    "source_citation",
    "figure_title",
    "figure_caption",
    "figure_index",
    "image_url",
    "source_article_url",
    "figure_page_url",
    "license",
    "license_url",
    "segments",
    "Keywords",
    "subject",
)
CANONICAL_SUBJECTS = {
    "Biological sciences",
    "Physical sciences",
    "Health sciences",
    "Earth and environmental sciences",
    "Scientific community and society",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset_dir", type=Path)
    parser.add_argument("--repository-api", type=Path)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--task-id", required=True)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def percentile(values: list[int], fraction: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, math.ceil(fraction * len(ordered)) - 1)
    return ordered[index]


def numeric_summary(values: list[int]) -> dict[str, int | float]:
    if not values:
        return {"min": 0, "p50": 0, "p95": 0, "max": 0, "mean": 0.0}
    return {
        "min": min(values),
        "p50": percentile(values, 0.50),
        "p95": percentile(values, 0.95),
        "max": max(values),
        "mean": round(statistics.fmean(values), 2),
    }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_blob_sha1(path: Path) -> str:
    digest = hashlib.sha1()
    digest.update(f"blob {path.stat().st_size}\0".encode("ascii"))
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_blank(value: Any) -> bool:
    return value is None or (isinstance(value, str) and not value.strip())


def iter_json_array(path: Path) -> Iterator[dict[str, Any]]:
    decoder = json.JSONDecoder()
    with path.open("r", encoding="utf-8") as stream:
        buffer = ""
        position = 0
        started = False
        eof = False
        while True:
            if position >= len(buffer) - 1 and not eof:
                buffer = buffer[position:]
                position = 0
                chunk = stream.read(1024 * 1024)
                if chunk:
                    buffer += chunk
                else:
                    eof = True

            while position < len(buffer) and (
                buffer[position].isspace() or buffer[position] == ","
            ):
                position += 1

            if not started:
                if position >= len(buffer):
                    if eof:
                        raise ValueError(f"Empty JSON array: {path}")
                    continue
                if buffer[position] != "[":
                    raise ValueError(f"Expected JSON array in {path}")
                started = True
                position += 1
                continue

            if position < len(buffer) and buffer[position] == "]":
                return

            try:
                value, next_position = decoder.raw_decode(buffer, position)
            except json.JSONDecodeError:
                if eof:
                    raise
                buffer = buffer[position:]
                position = 0
                chunk = stream.read(1024 * 1024)
                if chunk:
                    buffer += chunk
                else:
                    eof = True
                continue

            if not isinstance(value, dict):
                raise ValueError(f"Expected object in {path}, got {type(value).__name__}")
            yield value
            position = next_position


def audit_repository_files(
    dataset_dir: Path,
    repository_api: Path | None,
) -> dict[str, Any]:
    if repository_api is None:
        return {"checked": False}

    payload = json.loads(repository_api.read_text(encoding="utf-8"))
    expected = {entry["rfilename"]: entry for entry in payload["siblings"]}
    observed_names = {
        path.name
        for path in dataset_dir.iterdir()
        if path.is_file() and not path.name.endswith(".audit.json")
    }
    missing = sorted(set(expected) - observed_names)
    unexpected = sorted(observed_names - set(expected))
    mismatches: list[dict[str, Any]] = []
    receipts: list[dict[str, Any]] = []

    for name in sorted(set(expected) & observed_names):
        path = dataset_dir / name
        entry = expected[name]
        lfs = entry.get("lfs") or {}
        expected_size = int(entry["size"])
        expected_hash = lfs.get("sha256") or entry.get("blobId")
        hash_kind = "sha256" if lfs else "git_blob_sha1"
        observed_hash = sha256_file(path) if lfs else git_blob_sha1(path)
        size_matches = path.stat().st_size == expected_size
        hash_matches = observed_hash == expected_hash
        if not size_matches or not hash_matches:
            mismatches.append(
                {
                    "name": name,
                    "size_matches": size_matches,
                    "hash_matches": hash_matches,
                }
            )
        receipts.append(
            {
                "name": name,
                "bytes": path.stat().st_size,
                "hash_kind": hash_kind,
                "hash": observed_hash,
                "matches_upstream": size_matches and hash_matches,
            }
        )

    return {
        "checked": True,
        "repository": payload["id"],
        "revision": payload["sha"],
        "expected_file_count": len(expected),
        "observed_file_count": len(observed_names),
        "expected_bytes": sum(int(item["size"]) for item in expected.values()),
        "observed_bytes": sum(
            (dataset_dir / name).stat().st_size for name in observed_names
        ),
        "missing_files": missing,
        "unexpected_files": unexpected,
        "integrity_mismatches": mismatches,
        "receipts": receipts,
    }


def audit_captions(path: Path) -> tuple[dict[str, Any], set[str], set[str]]:
    filenames: set[str] = set()
    base_ids: set[str] = set()
    duplicate_names = 0
    invalid_names = 0
    blank_prompts = 0
    blank_cots = 0
    dimension_missing = collections.Counter()
    dimension_empty = collections.Counter()
    alignment_mismatches = collections.Counter()
    unpaired_terms = collections.Counter()
    unpaired_visualizations = collections.Counter()
    blank_term_items = collections.Counter()
    blank_visualization_items = collections.Counter()
    repeated_terms_within_record = collections.Counter()
    term_counts = {dimension: [] for dimension in DIMENSIONS}
    prompt_lengths: list[int] = []
    cot_lengths: list[int] = []
    prompt_hashes: collections.Counter[str] = collections.Counter()
    cot_hashes: collections.Counter[str] = collections.Counter()
    row_count = 0
    records_without_nonempty_reasoning = 0
    mismatch_examples: list[dict[str, Any]] = []

    with path.open("r", encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid caption JSON at line {line_number}") from exc
            row_count += 1
            filename = row.get("file_name", "")
            match = CAPTION_NAME.fullmatch(filename)
            if match is None:
                invalid_names += 1
            else:
                base_ids.add(match.group(1))
            if filename in filenames:
                duplicate_names += 1
            filenames.add(filename)

            prompt = row.get("science_abstract_prompt")
            cot = row.get("sci-RCoT")
            if is_blank(prompt):
                blank_prompts += 1
            else:
                prompt_lengths.append(len(prompt))
                prompt_hashes[hashlib.sha256(prompt.encode("utf-8")).hexdigest()] += 1
            if is_blank(cot):
                blank_cots += 1
            else:
                cot_lengths.append(len(cot))
                cot_hashes[hashlib.sha256(cot.encode("utf-8")).hexdigest()] += 1

            reasoning = row.get("reasoning")
            if not isinstance(reasoning, dict):
                reasoning = {}
            nonempty_dimensions = 0
            for dimension in DIMENSIONS:
                value = reasoning.get(dimension)
                if not isinstance(value, dict):
                    dimension_missing[dimension] += 1
                    term_counts[dimension].append(0)
                    continue
                terms = value.get("terms")
                visualizations = value.get("visualization")
                terms = terms if isinstance(terms, list) else []
                visualizations = visualizations if isinstance(visualizations, list) else []
                term_counts[dimension].append(len(terms))
                if not terms or not visualizations:
                    dimension_empty[dimension] += 1
                else:
                    nonempty_dimensions += 1
                if len(terms) != len(visualizations):
                    alignment_mismatches[dimension] += 1
                    unpaired_terms[dimension] += max(0, len(terms) - len(visualizations))
                    unpaired_visualizations[dimension] += max(
                        0, len(visualizations) - len(terms)
                    )
                    if len(mismatch_examples) < 20:
                        mismatch_examples.append(
                            {
                                "filename": filename,
                                "dimension": dimension,
                                "terms": len(terms),
                                "visualizations": len(visualizations),
                            }
                        )
                blank_term_items[dimension] += sum(is_blank(item) for item in terms)
                blank_visualization_items[dimension] += sum(
                    is_blank(item) for item in visualizations
                )
                normalized = [str(item).strip().casefold() for item in terms]
                repeated_terms_within_record[dimension] += len(normalized) - len(
                    set(normalized)
                )
            if nonempty_dimensions == 0:
                records_without_nonempty_reasoning += 1

    return (
        {
            "records": row_count,
            "unique_filenames": len(filenames),
            "unique_base_image_ids": len(base_ids),
            "duplicate_filenames": duplicate_names,
            "invalid_filename_patterns": invalid_names,
            "blank_science_abstract_prompts": blank_prompts,
            "blank_sci_rcot": blank_cots,
            "records_without_nonempty_reasoning_dimensions": (
                records_without_nonempty_reasoning
            ),
            "prompt_characters": numeric_summary(prompt_lengths),
            "sci_rcot_characters": numeric_summary(cot_lengths),
            "exact_duplicate_prompt_rows": sum(
                count - 1 for count in prompt_hashes.values() if count > 1
            ),
            "exact_duplicate_sci_rcot_rows": sum(
                count - 1 for count in cot_hashes.values() if count > 1
            ),
            "reasoning_dimensions": {
                dimension: {
                    "missing_records": dimension_missing[dimension],
                    "empty_records": dimension_empty[dimension],
                    "term_visualization_length_mismatches": alignment_mismatches[
                        dimension
                    ],
                    "unpaired_terms": unpaired_terms[dimension],
                    "unpaired_visualizations": unpaired_visualizations[dimension],
                    "blank_term_items": blank_term_items[dimension],
                    "blank_visualization_items": blank_visualization_items[dimension],
                    "repeated_terms_within_records": repeated_terms_within_record[
                        dimension
                    ],
                    "terms_per_record": numeric_summary(term_counts[dimension]),
                }
                for dimension in DIMENSIONS
            },
            "term_visualization_mismatch_examples": mismatch_examples,
        },
        filenames,
        base_ids,
    )


def host(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    return urllib.parse.urlparse(value).netloc.casefold()


def audit_metadata(
    path: Path,
    caption_filenames: set[str],
    caption_base_ids: set[str],
) -> tuple[dict[str, Any], set[str]]:
    ids: set[str] = set()
    segment_names: set[str] = set()
    duplicate_ids = 0
    duplicate_segment_names = 0
    invalid_ids = 0
    missing_fields = collections.Counter()
    blank_fields = collections.Counter()
    licenses = collections.Counter()
    subjects = collections.Counter()
    article_hosts = collections.Counter()
    image_hosts = collections.Counter()
    license_hosts = collections.Counter()
    source_article_urls: set[str] = set()
    figure_page_urls: set[str] = set()
    row_count = 0
    segment_count = 0
    noncanonical_subject_records = 0
    verbose_subject_records = 0

    for row in iter_json_array(path):
        row_count += 1
        image_id = row.get("image_id", "")
        if not METADATA_ID.fullmatch(image_id):
            invalid_ids += 1
        if image_id in ids:
            duplicate_ids += 1
        ids.add(image_id)
        for field in REQUIRED_METADATA_FIELDS:
            if field not in row:
                missing_fields[field] += 1
            elif is_blank(row[field]):
                blank_fields[field] += 1

        licenses[str(row.get("license", "")).strip()] += 1
        subject = str(row.get("subject", "")).strip()
        subjects[subject] += 1
        if subject not in CANONICAL_SUBJECTS:
            noncanonical_subject_records += 1
        if len(subject) > 80:
            verbose_subject_records += 1
        article_hosts[host(row.get("source_article_url"))] += 1
        image_hosts[host(row.get("image_url"))] += 1
        license_hosts[host(row.get("license_url"))] += 1
        if isinstance(row.get("source_article_url"), str):
            source_article_urls.add(row["source_article_url"])
        if isinstance(row.get("figure_page_url"), str):
            figure_page_urls.add(row["figure_page_url"])

        segments = row.get("segments")
        if not isinstance(segments, list):
            continue
        segment_count += len(segments)
        for segment in segments:
            filename = segment.get("filename", "") if isinstance(segment, dict) else ""
            if filename in segment_names:
                duplicate_segment_names += 1
            segment_names.add(filename)

    return (
        {
            "records": row_count,
            "unique_image_ids": len(ids),
            "duplicate_image_ids": duplicate_ids,
            "invalid_image_id_patterns": invalid_ids,
            "segments": segment_count,
            "unique_segment_filenames": len(segment_names),
            "duplicate_segment_filenames": duplicate_segment_names,
            "required_field_missing_records": dict(sorted(missing_fields.items())),
            "required_field_blank_records": dict(sorted(blank_fields.items())),
            "licenses": dict(licenses.most_common()),
            "canonical_subject_counts": {
                subject: subjects[subject] for subject in sorted(CANONICAL_SUBJECTS)
            },
            "canonical_subject_categories": sorted(CANONICAL_SUBJECTS),
            "noncanonical_subject_records": noncanonical_subject_records,
            "noncanonical_subject_breakdown": {
                "empty_or_missing": subjects[""],
                "literal_none": subjects["None"],
                "verbose_over_80_characters": verbose_subject_records,
                "other": (
                    noncanonical_subject_records
                    - subjects[""]
                    - subjects["None"]
                    - verbose_subject_records
                ),
            },
            "verbose_subject_records_over_80_characters": verbose_subject_records,
            "unique_source_articles": len(source_article_urls),
            "unique_figure_pages": len(figure_page_urls),
            "source_article_hosts": dict(article_hosts.most_common()),
            "image_hosts": dict(image_hosts.most_common()),
            "license_hosts": dict(license_hosts.most_common()),
            "caption_base_ids_without_metadata": len(caption_base_ids - ids),
            "metadata_ids_without_captions": len(ids - caption_base_ids),
            "caption_filenames_without_segments": len(
                caption_filenames - segment_names
            ),
            "segment_filenames_without_captions": len(
                segment_names - caption_filenames
            ),
        },
        segment_names,
    )


def parse_png(data: bytes) -> tuple[int, int]:
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("invalid PNG signature")
    position = 8
    width = height = 0
    saw_ihdr = saw_iend = False
    while position + 12 <= len(data):
        length = struct.unpack(">I", data[position : position + 4])[0]
        chunk_type = data[position + 4 : position + 8]
        data_start = position + 8
        data_end = data_start + length
        crc_end = data_end + 4
        if crc_end > len(data):
            raise ValueError("truncated PNG chunk")
        expected_crc = struct.unpack(">I", data[data_end:crc_end])[0]
        observed_crc = zlib.crc32(chunk_type)
        observed_crc = zlib.crc32(data[data_start:data_end], observed_crc) & 0xFFFFFFFF
        if expected_crc != observed_crc:
            raise ValueError("PNG CRC mismatch")
        if chunk_type == b"IHDR":
            if saw_ihdr or length != 13:
                raise ValueError("invalid PNG IHDR")
            width, height = struct.unpack(">II", data[data_start : data_start + 8])
            saw_ihdr = True
        if chunk_type == b"IEND":
            saw_iend = True
            if length != 0:
                raise ValueError("invalid PNG IEND")
            if crc_end != len(data):
                raise ValueError("trailing data after PNG IEND")
            break
        position = crc_end
    if not saw_ihdr or not saw_iend or width <= 0 or height <= 0:
        raise ValueError("incomplete PNG")
    return width, height


def audit_images(
    dataset_dir: Path,
    manifest: dict[str, Any],
    caption_filenames: set[str],
    segment_filenames: set[str],
) -> dict[str, Any]:
    names: set[str] = set()
    content_hashes: collections.Counter[str] = collections.Counter()
    widths: list[int] = []
    heights: list[int] = []
    byte_lengths: list[int] = []
    duplicate_names = 0
    non_regular_members = 0
    unsafe_member_names = 0
    invalid_png_count = 0
    invalid_pngs: list[dict[str, str]] = []
    tiny_images = 0
    extreme_aspect_images = 0
    shard_size_mismatches: list[str] = []
    shard_sample_mismatches: list[str] = []
    shard_boundary_mismatches: list[str] = []

    for shard in manifest["shards"]:
        path = dataset_dir / shard["name"]
        if path.stat().st_size != int(shard["tar_bytes"]):
            shard_size_mismatches.append(shard["name"])
        shard_names: list[str] = []
        with tarfile.open(path, mode="r:") as archive:
            for member in archive:
                if not member.isfile():
                    non_regular_members += 1
                    continue
                name = member.name
                shard_names.append(name)
                if Path(name).is_absolute() or ".." in Path(name).parts:
                    unsafe_member_names += 1
                if name in names:
                    duplicate_names += 1
                names.add(name)
                extracted = archive.extractfile(member)
                if extracted is None:
                    invalid_png_count += 1
                    if len(invalid_pngs) < 50:
                        invalid_pngs.append(
                            {"name": name, "error": "unreadable member"}
                        )
                    continue
                data = extracted.read()
                byte_lengths.append(len(data))
                content_hashes[hashlib.sha256(data).hexdigest()] += 1
                try:
                    width, height = parse_png(data)
                except ValueError as exc:
                    invalid_png_count += 1
                    if len(invalid_pngs) < 50:
                        invalid_pngs.append({"name": name, "error": str(exc)})
                    continue
                widths.append(width)
                heights.append(height)
                if width < 64 or height < 64:
                    tiny_images += 1
                aspect = max(width / height, height / width)
                if aspect > 10:
                    extreme_aspect_images += 1
        if len(shard_names) != int(shard["samples"]):
            shard_sample_mismatches.append(shard["name"])
        if shard_names and (
            shard_names[0] != shard["first"] or shard_names[-1] != shard["last"]
        ):
            shard_boundary_mismatches.append(shard["name"])

    return {
        "shards": len(manifest["shards"]),
        "images": len(names),
        "duplicate_member_names": duplicate_names,
        "non_regular_members": non_regular_members,
        "unsafe_member_names": unsafe_member_names,
        "invalid_png_count": invalid_png_count,
        "invalid_png_examples": invalid_pngs,
        "exact_duplicate_image_rows": sum(
            count - 1 for count in content_hashes.values() if count > 1
        ),
        "unique_image_content_hashes": len(content_hashes),
        "image_bytes": numeric_summary(byte_lengths),
        "width_pixels": numeric_summary(widths),
        "height_pixels": numeric_summary(heights),
        "tiny_images_below_64px_on_an_axis": tiny_images,
        "extreme_aspect_ratio_over_10": extreme_aspect_images,
        "shard_size_mismatches": shard_size_mismatches,
        "shard_sample_mismatches": shard_sample_mismatches,
        "shard_boundary_mismatches": shard_boundary_mismatches,
        "caption_filenames_without_images": len(caption_filenames - names),
        "images_without_captions": len(names - caption_filenames),
        "metadata_segment_filenames_without_images": len(segment_filenames - names),
        "images_without_metadata_segments": len(names - segment_filenames),
    }


def main() -> int:
    args = parse_args()
    dataset_dir = args.dataset_dir.resolve()
    manifest = json.loads((dataset_dir / "manifest.json").read_text(encoding="utf-8"))
    captions, caption_filenames, caption_base_ids = audit_captions(
        dataset_dir / "caption.jsonl"
    )
    metadata, segment_filenames = audit_metadata(
        dataset_dir / "metadata.json", caption_filenames, caption_base_ids
    )
    images = audit_images(
        dataset_dir, manifest, caption_filenames, segment_filenames
    )
    result = {
        "schema_version": 1,
        "task_id": args.task_id,
        "source_ref": args.source_ref,
        "source_trust": "untrusted_external_content",
        "dataset": "MAIR-Lab-HUST/SciIR-82k",
        "revision": args.revision,
        "manifest": {
            "total_images": manifest["total_images"],
            "total_shards": manifest["total_shards"],
            "total_source_bytes": manifest["total_source_bytes"],
        },
        "repository_files": audit_repository_files(
            dataset_dir, args.repository_api.resolve() if args.repository_api else None
        ),
        "captions": captions,
        "metadata": metadata,
        "images": images,
    }
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
