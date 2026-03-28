#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
持续通过 ADB 采集：系统亮度设置、Display 子系统亮度/HBM、**环境亮度**（**枚举** sensorservice
中全部环境光传感器各路末条 lux，主路写入 `env_lux`；Display 侧 `mAmbientLux` 为 `env_lux_display`），以及应用内 **0～200% 会话滑块**（logcat
tag `HDRV_Brightness`，消息 `session_pct=...`，由全屏预览页写入），以及 **全屏图解码后灰度直方图**（tag `HDRV_Histogram`，消息
`histogram_json=...`（**`v`: 2**、`note`=`display_window_pixelcopy`，全屏预览区 **PixelCopy** 合成后；**debug APK**）；与 `session_pct` 同轮 logcat 抓取）。

与 misc/test.md 中实验结论一致：
- 环境光以 sensorservice 的 ALS 事件为准；display 内 mAmbientLux 可能滞后。
- 部分机型上 `dumpsys sensors` 不可用，请用 `dumpsys sensorservice`。

用法（需本机已安装 adb 且在 PATH；会话百分比需设备上已安装并曾进入全屏预览或拖动滑块）:
  python misc/brightness_monitor.py
  python misc/brightness_monitor.py -s 192.168.88.161:35061 -i 2
  python misc/brightness_monitor.py --log brightness_log.csv

控制台默认输出 **缩进换行的 JSON**；**`--json-compact`** 为单行紧凑（JSON Lines）。CSV 仍为表格列（见 `--log`）。

每次运行默认在 **`misc/logs/`** 下新建 **`brightness_monitor_<时间戳>_<内容名>.log`**，每采一条 **立即追加**（与控制台格式一致），Ctrl+C 后文件仍保留；**`--no-session-log`** 关闭；**`--session-name`** 改内容名。
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Optional, TextIO


def _session_log_dir_default() -> Path:
    return Path(__file__).resolve().parent / "logs"


def _safe_filename_segment(s: str) -> str:
    t = s.strip()
    for c in '<>:"/\\|?*\n\r\t':
        t = t.replace(c, "_")
    if len(t) > 80:
        t = t[:79] + "…"
    return t or "session"


def _new_session_log_path(log_dir: Path, content_name: str) -> Path:
    """一次测试一个文件：时间戳 + 内容名 + pid，避免同秒冲突。"""
    ts = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]
    seg = _safe_filename_segment(content_name)
    return log_dir / f"brightness_monitor_{ts}_{os.getpid()}_{seg}.log"


def _build_adb_base(serial: Optional[str]) -> list[str]:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    return cmd


def _run(
    adb_base: list[str],
    shell_args: list[str],
    *,
    timeout: float = 120.0,
    text: bool = True,
) -> tuple[int, str, str]:
    full = adb_base + ["shell"] + shell_args
    try:
        p = subprocess.run(
            full,
            capture_output=True,
            timeout=timeout,
            text=text,
            encoding="utf-8",
            errors="replace",
        )
        return p.returncode, p.stdout or "", p.stderr or ""
    except subprocess.TimeoutExpired:
        return -1, "", "timeout"


def _run_adb(
    adb_base: list[str],
    adb_args: list[str],
    *,
    timeout: float = 60.0,
) -> tuple[int, str, str]:
    """直接执行 `adb ...`（非 adb shell），用于 logcat 等。"""
    full = adb_base + adb_args
    try:
        p = subprocess.run(
            full,
            capture_output=True,
            timeout=timeout,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        return p.returncode, p.stdout or "", p.stderr or ""
    except subprocess.TimeoutExpired:
        return -1, "", "timeout"


def _parse_last_session_pct_from_logcat(logcat_text: str) -> Optional[str]:
    """从 logcat 文本中取最后一条 HDRV_Brightness / session_pct= 的数值字符串。"""
    last: Optional[str] = None
    tag = "HDRV_Brightness"
    pat = re.compile(r"session_pct=([\d.]+)")
    for line in logcat_text.splitlines():
        if tag not in line:
            continue
        m = pat.search(line)
        if m:
            last = m.group(1)
    return last


def _parse_last_histogram_json_from_logcat(logcat_text: str) -> Optional[str]:
    """从 logcat 文本中取最后一条 HDRV_Histogram / histogram_json= 的 JSON 字符串（单行）。"""
    last: Optional[str] = None
    for line in logcat_text.splitlines():
        if "HDRV_Histogram" not in line:
            continue
        key = "histogram_json="
        idx = line.find(key)
        if idx < 0:
            continue
        last = line[idx + len(key) :].strip()
    return last


def _fetch_app_logcat_debug(adb_base: list[str]) -> tuple[str, str, str]:
    """
    返回 (app_session_pct, preview_histogram_json, logcat_error)。

    注意：**不要**使用 `logcat -d -t N -s TAG`：`-t N` 先截全局最后 N 行再按 tag 过滤，
    若这 N 行里恰好没有目标 tag，会得到空输出（环形缓冲里其实仍有该 tag）。
    正确做法：`-s HDRV_Brightness:D HDRV_Histogram:D` 不按行数截断；再不行用大 `-t` 的全文尾窗解析。
    """
    attempts: list[tuple[list[str], float]] = [
        (["logcat", "-d", "-s", "HDRV_Brightness:D", "HDRV_Histogram:D"], 30.0),
        (["logcat", "-d", "-t", "12000"], 45.0),
    ]
    last_err = ""
    for args, tmo in attempts:
        code, out, err = _run_adb(adb_base, args, timeout=tmo)
        if code != 0:
            last_err = err.strip() or f"exit {code}"
            continue
        pct = _parse_last_session_pct_from_logcat(out)
        hist = _parse_last_histogram_json_from_logcat(out)
        if pct is not None or hist is not None:
            return pct or "", hist or "", ""
        last_err = ""
    return "", "", last_err or "<no HDRV_Brightness / HDRV_Histogram in buffer>"


def _parse_display_key_lines(dump: str) -> dict[str, str]:
    """从 dumpsys display 全文中抽取常用键（首条匹配）。"""
    out: dict[str, str] = {}
    patterns = [
        (r"^\s*Display Brightness\s*=\s*(.+)$", "display_brightness_line"),
        (r"^\s*mBrightness\s*=\s*(.+)$", "mBrightness"),
        (r"^\s*mAmbientLux\s*=\s*(.+)$", "mAmbientLux"),
        (r"^\s*mHbmStatsState\s*=\s*(.+)$", "mHbmStatsState"),
        (r"^\s*mHbmMode\s*=\s*(.+)$", "mHbmMode"),
        (r"^\s*mScreenBrightnessSetting\s*=\s*(.+)$", "mScreenBrightnessSetting"),
        (r"^\s*mScreenBrightnessSettingDefault\s*=\s*(.+)$", "mScreenBrightnessSettingDefault"),
        (r"^\s*mScreenBrightnessModeSetting\s*=\s*(.+)$", "mScreenBrightnessModeSetting"),
    ]
    for line in dump.splitlines():
        for pat, key in patterns:
            if key in out:
                continue
            m = re.match(pat, line)
            if m:
                out[key] = m.group(1).strip()
    return out


_ALS_HEADER_RE = re.compile(r"^(.+):\s*last\s+\d+\s+events\s*$", re.MULTILINE)
# 单路融合 lux：行尾仅一个数，如 Non-wakeup `… ) 260.88,`
_ALS_EVENT_SINGLE_LUX_RE = re.compile(
    r"^\s*\d+\s+\(\s*ts=[^,]+,\s*wall=([^)]+)\)\s*([\d.]+)\s*,?\s*$"
)
# Raw / Strm 等多通道：`… ) 7650.17, 4378.01, 2612.02, …`（实测 dumpsys 见 Raw/Strm）
_ALS_EVENT_MULTI_PREFIX_RE = re.compile(
    r"^\s*\d+\s+\(\s*ts=[^,]+,\s*wall=([^)]+)\)\s*([\d.]+)"
)


def _is_ambient_light_sensor_name(name: str) -> bool:
    """判断 `*: last N events` 标题是否像环境光（ALS）传感器；排除纯接近传感器等。"""
    n = name.lower()
    if "proximity" in n and "light" not in n and "als" not in n and "ambient" not in n:
        return False
    if "ambient" in n and "light" in n:
        return True
    if "android.sensor.light" in n:
        return True
    if "light sensor" in n:
        return True
    if "alsp" in n or "alsprx" in n:
        return True
    return False


def _parse_last_event_lux_wall(chunk: str) -> tuple[Optional[str], Optional[str]]:
    """
    从单个传感器事件块中取最后一条 wall 与代表亮度标量。
    - Non-wakeup 常为单行单 lux；
    - Raw / Strm 为多路逗号分隔浮点，取 **第一个** 浮点作摘要（与 adb 实测一致）。
    """
    last_wall: Optional[str] = None
    last_lux: Optional[str] = None
    for line in chunk.splitlines():
        m = _ALS_EVENT_SINGLE_LUX_RE.match(line)
        if m:
            last_wall, last_lux = m.group(1).strip(), m.group(2).strip()
            continue
        m2 = _ALS_EVENT_MULTI_PREFIX_RE.match(line)
        if m2:
            last_wall, last_lux = m2.group(1).strip(), m2.group(2).strip()
    return last_wall, last_lux


def _parse_all_ambient_light_sensors(sensorservice_dump: str) -> list[dict[str, str]]:
    """
    枚举 sensorservice 中所有「环境光」类传感器的 `last N events` 块，各取末条 lux。
    旧版仅取第一个匹配标记，多 ALS 机型会漏记。
    """
    matches = list(_ALS_HEADER_RE.finditer(sensorservice_dump))
    out: list[dict[str, str]] = []
    for i, m in enumerate(matches):
        name = m.group(1).strip()
        if not _is_ambient_light_sensor_name(name):
            continue
        start = m.end()
        endpos = matches[i + 1].start() if i + 1 < len(matches) else len(sensorservice_dump)
        chunk = sensorservice_dump[start:endpos]
        w, lux = _parse_last_event_lux_wall(chunk)
        out.append({"name": name, "wall": w or "", "lux": lux or ""})
    return out


def _pick_primary_als_sensor(sensors: list[dict[str, str]]) -> dict[str, str]:
    """
    主读数：优先 **融合 ALS**（名称含 Non-wakeup 且 **非** Raw/Strm 工厂流），与 misc/test.md 策略侧一致。
    注意：**Raw Data Non-wakeup** 名称里也带 `non-wakeup`，若与融合路混在同一列表里按序先匹配，会误把 Raw 首通道当成「主传感器」。
    """
    if not sensors:
        return {}

    def has_lux(s: dict[str, str]) -> bool:
        return bool((s.get("lux") or "").strip())

    def is_raw_or_strm(name: str) -> bool:
        n = name.lower()
        if "strm" in n:
            return True
        return "raw data" in n

    non_wakeup = [s for s in sensors if "non-wakeup" in s["name"].lower()]
    fusion_non_wakeup = [s for s in non_wakeup if not is_raw_or_strm(s["name"])]
    tcs3720 = [s for s in sensors if "TCS3720" in s["name"] or "3720" in s["name"]]

    for s in fusion_non_wakeup:
        if has_lux(s):
            return s
    for s in non_wakeup:
        if has_lux(s):
            return s
    for s in tcs3720:
        if has_lux(s):
            return s
    for s in sensors:
        if has_lux(s):
            return s
    if non_wakeup:
        return non_wakeup[0]
    if tcs3720:
        return tcs3720[0]
    return sensors[0]


def _format_als_summary(sensors: list[dict[str, str]], *, max_name_len: int = 40) -> str:
    parts: list[str] = []
    for s in sensors:
        nm = s["name"].replace("|", " ")
        if len(nm) > max_name_len:
            nm = nm[: max_name_len - 1] + "…"
        parts.append(f"{nm}={s.get('lux', '')}@{s.get('wall', '')}")
    return "; ".join(parts)


def _settings_get(adb_base: list[str], table: str, key: str) -> str:
    code, out, err = _run(adb_base, ["settings", "get", table, key], timeout=15.0)
    if code != 0:
        return f"<err:{code} {err.strip() or 'no stderr'}>"
    return (out or "").strip() or "<empty>"


def _collect_once(
    adb_base: list[str],
    *,
    include_sensorservice: bool,
    include_logcat: bool,
) -> dict[str, Any]:
    row: dict[str, Any] = {}
    row["ts_local"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

    row["settings_screen_brightness"] = _settings_get(adb_base, "system", "screen_brightness")
    row["settings_screen_brightness_mode"] = _settings_get(
        adb_base, "system", "screen_brightness_mode"
    )

    code, disp_out, disp_err = _run(adb_base, ["dumpsys", "display"], timeout=90.0)
    if code != 0:
        row["dumpsys_display_error"] = disp_err.strip() or f"exit {code}"
    else:
        d = _parse_display_key_lines(disp_out)
        row.update(d)

    if include_sensorservice:
        sc, ss_out, ss_err = _run(adb_base, ["dumpsys", "sensorservice"], timeout=120.0)
        if sc != 0:
            row["sensorservice_error"] = ss_err.strip() or f"exit {sc}"
            row["als_wall_time"] = ""
            row["als_lux"] = ""
            row["als_sensor_count"] = "0"
            row["env_als_json"] = ""
            row["env_als_summary"] = ""
        else:
            row["sensorservice_error"] = ""
            all_als = _parse_all_ambient_light_sensors(ss_out)
            row["als_sensor_count"] = str(len(all_als))
            row["env_als_json"] = json.dumps(all_als, ensure_ascii=False)
            row["env_als_summary"] = _format_als_summary(all_als)
            primary = _pick_primary_als_sensor(all_als)
            row["als_wall_time"] = (primary.get("wall") or "").strip()
            row["als_lux"] = (primary.get("lux") or "").strip()
    else:
        row["als_wall_time"] = ""
        row["als_lux"] = ""
        row["als_sensor_count"] = ""
        row["env_als_json"] = ""
        row["env_als_summary"] = ""

    # 环境亮度：与 misc/test.md 一致——以传感器 ALS 为准；Display 侧仅作对照（常陈旧）
    row["env_lux"] = (row.get("als_lux") or "").strip()
    row["env_lux_wall"] = (row.get("als_wall_time") or "").strip()
    row["env_lux_display"] = (row.get("mAmbientLux") or "").strip()

    # logcat 放在每轮末尾：与打印时间最接近，且避免 `-t` 误截之前先被长 dumpsys 拖住导致采样错位
    if include_logcat:
        pct, hist, lc_err = _fetch_app_logcat_debug(adb_base)
        row["app_session_pct"] = pct
        row["preview_histogram_json"] = hist
        row["logcat_error"] = lc_err
    else:
        row["app_session_pct"] = ""
        row["preview_histogram_json"] = ""
        row["logcat_error"] = ""

    return row


def _row_field_order() -> list[str]:
    return [
        "ts_local",
        "app_session_pct",
        "preview_histogram_json",
        "logcat_error",
        "settings_screen_brightness",
        "settings_screen_brightness_mode",
        "display_brightness_line",
        "mBrightness",
        "mAmbientLux",
        "mHbmStatsState",
        "mHbmMode",
        "mScreenBrightnessSetting",
        "mScreenBrightnessModeSetting",
        "als_sensor_count",
        "als_wall_time",
        "als_lux",
        "env_lux",
        "env_lux_wall",
        "env_lux_display",
        "env_als_summary",
        "env_als_json",
        "dumpsys_display_error",
        "sensorservice_error",
    ]


def _row_for_json_log(row: dict[str, Any]) -> dict[str, Any]:
    """供控制台输出：去掉冗长 summary 字符串，将 `env_als_json` 解析为数组 `als_sensors`。"""
    out: dict[str, Any] = {
        k: v
        for k, v in row.items()
        if k not in ("env_als_json", "env_als_summary", "preview_histogram_json")
    }
    ej = row.get("env_als_json") or ""
    if ej:
        try:
            out["als_sensors"] = json.loads(ej)
        except json.JSONDecodeError:
            out["als_sensors"] = []
    else:
        out["als_sensors"] = []
    ph = row.get("preview_histogram_json") or ""
    if ph:
        try:
            out["preview_histogram"] = json.loads(ph)
        except json.JSONDecodeError:
            out["preview_histogram"] = None
    else:
        out["preview_histogram"] = None
    ac = out.get("als_sensor_count", "")
    if isinstance(ac, str) and ac.isdigit():
        out["als_sensor_count"] = int(ac)
    return out


def _format_json_log_text(row: dict[str, Any], *, compact: bool) -> str:
    obj = _row_for_json_log(row)
    if compact:
        return json.dumps(obj, ensure_ascii=False, separators=(",", ":")) + "\n"
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n\n"


def _print_json_log(
    row: dict[str, Any],
    *,
    compact: bool,
    session_file: Optional[TextIO] = None,
) -> None:
    text = _format_json_log_text(row, compact=compact)
    sys.stdout.write(text)
    sys.stdout.flush()
    if session_file is not None:
        session_file.write(text)
        session_file.flush()


def main() -> int:
    ap = argparse.ArgumentParser(description="ADB 亮度 / ALS 持续监控")
    ap.add_argument("-s", "--serial", help="adb 设备序列号（无线 adb 形如 IP:端口）")
    ap.add_argument("-i", "--interval", type=float, default=1.0, help="采样间隔（秒），默认 1")
    ap.add_argument(
        "--als-every",
        type=int,
        default=1,
        metavar="N",
        help="每 N 次采样才执行一次 dumpsys sensorservice（较重），默认 1 即每次都采",
    )
    ap.add_argument("--log", metavar="FILE.csv", help="追加写入 CSV（UTF-8）")
    ap.add_argument("--once", action="store_true", help="只采一次后退出")
    ap.add_argument(
        "--no-logcat",
        action="store_true",
        help="不抓取 logcat（不解析应用内 session_pct / preview 直方图）",
    )
    ap.add_argument(
        "--json-compact",
        action="store_true",
        help="控制台单行紧凑 JSON（JSON Lines）；默认缩进换行",
    )
    ap.add_argument(
        "--no-session-log",
        action="store_true",
        help="不写入 misc/logs 会话文件（默认每次运行新建一个 .log 并实时追加）",
    )
    ap.add_argument(
        "--session-name",
        default="亮度监控",
        metavar="NAME",
        help="会话日志文件名中的内容名（与时间戳、pid 组合），默认「亮度监控」",
    )
    ap.add_argument(
        "--session-log-dir",
        type=Path,
        default=None,
        help="会话日志目录，默认本脚本旁 misc/logs",
    )
    args = ap.parse_args()

    adb_base = _build_adb_base(args.serial)
    code, out, err = _run(adb_base, ["echo", "ok"], timeout=10.0)
    if code != 0:
        print(f"adb 不可用或设备未连接: {err or out}", file=sys.stderr)
        return 1

    fields = _row_field_order()

    session_fp: Optional[TextIO] = None
    if not args.no_session_log:
        sdir = args.session_log_dir or _session_log_dir_default()
        sdir.mkdir(parents=True, exist_ok=True)
        spath = _new_session_log_path(sdir, args.session_name)
        session_fp = open(spath, "w", encoding="utf-8", newline="\n")
        print(f"会话日志: {spath}", file=sys.stderr, flush=True)

    csv_fp: Optional[Any] = None
    log_writer: Optional[csv.DictWriter] = None
    if args.log:
        new_file = not os.path.exists(args.log)
        csv_fp = open(args.log, "a", encoding="utf-8", newline="")
        log_writer = csv.DictWriter(csv_fp, fieldnames=fields, extrasaction="ignore")
        if new_file:
            log_writer.writeheader()

    cycle = 0
    try:
        while True:
            cycle += 1
            include_ss = args.als_every <= 1 or (cycle % args.als_every == 0)
            row = _collect_once(
                adb_base,
                include_sensorservice=include_ss,
                include_logcat=not args.no_logcat,
            )
            # 确保缺失键存在，便于 CSV
            for f in fields:
                row.setdefault(f, "")
            _print_json_log(row, compact=args.json_compact, session_file=session_fp)
            if log_writer is not None and csv_fp is not None:
                log_writer.writerow(row)
                csv_fp.flush()

            if args.once:
                break
            time.sleep(max(0.1, args.interval))
    except KeyboardInterrupt:
        print("\n已停止。", flush=True)
    finally:
        if csv_fp is not None:
            csv_fp.close()
        if session_fp is not None:
            session_fp.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
