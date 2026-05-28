#!/usr/bin/env python3
"""
Collect Android benchmark JSON outputs, build a compact metrics summary,
and compare against a baseline in report-only mode.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from statistics import median
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, help="mobile-android root path")
    parser.add_argument("--baseline", required=True, help="Baseline JSON path")
    parser.add_argument("--summary-file", required=True, help="GitHub summary markdown file")
    return parser.parse_args()


def find_candidate_json(root: Path) -> list[Path]:
    candidates: list[Path] = []
    for p in root.rglob("*.json"):
        # Avoid huge irrelevant dependency metadata.
        if any(part in {"build-cache", ".gradle"} for part in p.parts):
            continue
        candidates.append(p)
    return candidates


def is_benchmark_payload(obj: Any) -> bool:
    return isinstance(obj, dict) and "benchmarks" in obj and isinstance(obj["benchmarks"], list)


def to_float(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    return None


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = (len(ordered) - 1) * p
    lo = int(idx)
    hi = min(lo + 1, len(ordered) - 1)
    frac = idx - lo
    return ordered[lo] * (1.0 - frac) + ordered[hi] * frac


def extract_scalar_stats(metric_value: Any) -> dict[str, float]:
    out: dict[str, float] = {}
    if isinstance(metric_value, dict):
        # Common benchmark keys
        for key in ("minimum", "maximum", "median", "p50", "p90", "p95", "p99"):
            fv = to_float(metric_value.get(key))
            if fv is not None:
                out[key] = fv
        # Some formats provide list of runs/samples.
        runs = metric_value.get("runs")
        if isinstance(runs, list):
            nums = [to_float(v) for v in runs]
            nums = [v for v in nums if v is not None]
            if nums:
                out.setdefault("median", median(nums))
                out.setdefault("p50", percentile(nums, 0.50))
                out.setdefault("p90", percentile(nums, 0.90))
                out.setdefault("p95", percentile(nums, 0.95))
                out.setdefault("p99", percentile(nums, 0.99))
        samples = metric_value.get("samples")
        if isinstance(samples, list):
            nums = [to_float(v) for v in samples]
            nums = [v for v in nums if v is not None]
            if nums:
                out.setdefault("median", median(nums))
                out.setdefault("p50", percentile(nums, 0.50))
                out.setdefault("p90", percentile(nums, 0.90))
                out.setdefault("p95", percentile(nums, 0.95))
                out.setdefault("p99", percentile(nums, 0.99))
    return out


def extract_metrics(payloads: list[dict[str, Any]]) -> dict[str, dict[str, float]]:
    collected: dict[str, dict[str, float]] = {}
    for payload in payloads:
        for b in payload.get("benchmarks", []):
            if not isinstance(b, dict):
                continue
            name = str(b.get("name", "unknown"))
            metrics = b.get("metrics", {})
            if not isinstance(metrics, dict):
                continue
            for metric_name, metric_value in metrics.items():
                stats = extract_scalar_stats(metric_value)
                if not stats:
                    continue
                key = f"{name}:{metric_name}"
                collected[key] = stats
    return collected


def load_baseline(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"metrics": {}}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {"metrics": {}}


def compare_report_only(
    current: dict[str, dict[str, float]],
    baseline: dict[str, Any],
) -> list[str]:
    # Report-only thresholds from plan; non-blocking warnings.
    warn_lines: list[str] = []
    b_metrics = baseline.get("metrics", {})
    if not isinstance(b_metrics, dict):
        return warn_lines
    for key, curr in current.items():
        base = b_metrics.get(key)
        if not isinstance(base, dict):
            continue
        for stat_key, limit in (("p95", 0.15), ("p99", 0.20), ("median", 0.10)):
            c = to_float(curr.get(stat_key))
            b = to_float(base.get(stat_key))
            if c is None or b is None or b <= 0:
                continue
            ratio = (c - b) / b
            if ratio > limit:
                warn_lines.append(
                    f"- WARNING `{key}` {stat_key}: current={c:.2f}, baseline={b:.2f}, regression={ratio * 100:.1f}%"
                )
    return warn_lines


def write_summary(
    summary_file: Path,
    payload_count: int,
    current: dict[str, dict[str, float]],
    warnings: list[str],
) -> None:
    lines: list[str] = []
    lines.append("## Android Benchmark Summary")
    lines.append("")
    lines.append(f"- Parsed benchmark payloads: **{payload_count}**")
    lines.append(f"- Extracted metric groups: **{len(current)}**")
    lines.append("- Mode: **report-only** (CI does not fail on regressions)")
    lines.append("")
    if current:
        lines.append("### Key metrics")
        lines.append("")
        for key in sorted(current.keys()):
            stats = current[key]
            p95 = stats.get("p95")
            p99 = stats.get("p99")
            med = stats.get("median", stats.get("p50"))
            lines.append(
                f"- `{key}` median={med:.2f} p95={p95:.2f} p99={p99:.2f}"
                if med is not None and p95 is not None and p99 is not None
                else f"- `{key}` stats={json.dumps(stats, ensure_ascii=False)}"
            )
    else:
        lines.append("### Key metrics")
        lines.append("")
        lines.append("- No benchmark metrics were extracted from JSON outputs.")
    lines.append("")
    lines.append("### Baseline comparison")
    lines.append("")
    if warnings:
        lines.extend(warnings)
    else:
        lines.append("- No report-only regression warnings against baseline.")
    summary_file.parent.mkdir(parents=True, exist_ok=True)
    summary_file.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()
    baseline_path = (root / args.baseline).resolve() if not Path(args.baseline).is_absolute() else Path(args.baseline)
    summary_file = Path(args.summary_file)

    payloads: list[dict[str, Any]] = []
    for p in find_candidate_json(root):
        try:
            obj = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        if is_benchmark_payload(obj):
            payloads.append(obj)

    current = extract_metrics(payloads)
    baseline = load_baseline(baseline_path)
    warnings = compare_report_only(current, baseline)
    write_summary(summary_file, len(payloads), current, warnings)

    # Also persist current results artifact for optional baseline refresh.
    out_dir = root / "benchmark" / "results"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "latest_metrics.json").write_text(
        json.dumps({"metrics": current}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
