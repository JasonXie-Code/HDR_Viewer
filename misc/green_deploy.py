#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HDR Viewer — 一键绿化（便携环境 + 启动安装调试菜单）

由仓库根目录「AAA一键绿色部署.bat」仅负责调用本脚本；中文提示与逻辑均放在此，
避免 bat 对 UTF-8/中文支持不稳。

用法：
  python misc/green_deploy.py [传递给 misc/AAA安装调试App.py 的参数……]
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def _repo_roots() -> tuple[Path, Path]:
    misc_dir = Path(__file__).resolve().parent
    repo_root = misc_dir.parent
    return misc_dir, repo_root


def apply_portable_env(repo_root: Path) -> dict[str, str]:
    """与 misc/env_portable.cmd 一致：JDK、SDK、嵌入式 Python、adb 优先于 PATH。"""
    env = os.environ.copy()
    java_home = repo_root / "misc" / "tools" / "jdk-17"
    sdk_root = repo_root / "misc" / "tools" / "android-sdk"
    py_home = repo_root / "misc" / "tools" / "python"
    android_dir = repo_root / "android"

    env["HDRV_ROOT"] = str(repo_root)
    env["HDRV_ANDROID"] = str(android_dir)
    env["JAVA_HOME"] = str(java_home)
    env["ANDROID_SDK_ROOT"] = str(sdk_root)
    env["ANDROID_HOME"] = str(sdk_root)
    env["PYTHON_HOME"] = str(py_home)

    prefix_parts = [
        str(py_home),
        str(py_home / "Scripts"),
        str(java_home / "bin"),
        str(sdk_root / "platform-tools"),
    ]
    sep = os.pathsep
    env["PATH"] = sep.join(prefix_parts) + sep + env.get("PATH", "")

    return env


def _configure_stdio_utf8() -> None:
    if sys.platform != "win32":
        return
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def main() -> int:
    _configure_stdio_utf8()
    misc_dir, repo_root = _repo_roots()
    os.chdir(repo_root)

    py_self = Path(sys.executable).resolve()
    menu = misc_dir / "AAA安装调试App.py"
    if not menu.is_file():
        print("未找到 misc/AAA安装调试App.py", file=sys.stderr)
        return 1

    env = apply_portable_env(repo_root)
    print("[HDR Viewer] 便携环境已注入（JAVA_HOME / ANDROID_SDK_ROOT / PATH 等）")
    print(f"[HDR Viewer] HDRV_ROOT={repo_root}")
    print(f"[HDR Viewer] HDRV_ANDROID={env.get('HDRV_ANDROID', '')}")
    print("[HDR Viewer] 正在启动安装调试菜单……\n")

    cmd = [str(py_self), str(menu), *sys.argv[1:]]
    try:
        proc = subprocess.run(cmd, cwd=str(repo_root), env=env)
        return int(proc.returncode if proc.returncode is not None else 0)
    except OSError as e:
        print(f"启动失败: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
