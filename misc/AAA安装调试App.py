#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HDR Viewer — 安装调试 App（交互式菜单）
功能：检查设备、Gradle 编译、adb 安装/启动、日志与无线调试等。
仅使用仓库内 misc/tools 下的 JDK、SDK、adb；Android 工程位于 android/。
本文件位于 misc/，通常由 misc/green_deploy.py 或直接在仓库根用 Python 启动。
"""

from __future__ import annotations

import os
import sys
import subprocess
import platform
import json
from pathlib import Path
from datetime import datetime

# ANSI 颜色转义码
if platform.system() == "Windows":
    # Windows 10+ 终端支持 ANSI，但可能需要初始化
    os.system('')

GREEN = "\033[32m"
RED = "\033[31m"
YELLOW = "\033[33m"
CYAN = "\033[36m"
RESET = "\033[0m"

def print_success(msg):
    print(f"{GREEN}✓ {msg}{RESET}")

def print_error(msg):
    print(f"{RED}✗ {msg}{RESET}")

def print_warning(msg):
    print(f"{YELLOW}⚠ {msg}{RESET}")

def print_info(msg):
    print(f"{CYAN}→ {msg}{RESET}")

# 项目根目录（本脚本位于 misc/，仓库根为其上一级）
MISC_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = MISC_DIR.parent
os.chdir(PROJECT_ROOT)

# Android 工程在 android/（与仓库根下 PROJECT.md、ROADMAP.md 并列）
ANDROID_ROOT = PROJECT_ROOT / "android"
APP_PACKAGE = "com.hdrviewer.app"
APP_ACTIVITY = "com.hdrviewer.app.MainActivity"
APK_PATH = ANDROID_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
APK_RELEASE_PATH = ANDROID_ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"

IS_WINDOWS = platform.system() == "Windows"
JDK_DIR = PROJECT_ROOT / "misc" / "tools" / "jdk-17"
GRADLEW = ANDROID_ROOT / ("gradlew.bat" if IS_WINDOWS else "gradlew")

# 日志保存目录
LOGS_DIR = PROJECT_ROOT / ".AppLogs"

# WiFi 调试历史 IP 存储（最多 5 个）
WIFI_HISTORY_FILE = PROJECT_ROOT / "misc" / "debug" / "debug_wifi_ips.json"
WIFI_HISTORY_MAX = 5

# 日志过滤关键词（过滤刷屏日志）
FILTER_PATTERNS = [
    "ViewRootImplStubImpl",   # MIUI 动画日志
    "onAnimationUpdate",       # 动画更新刷屏
    "AdrenoVK",                # GPU shader 警告
    "AudioCapabilities",       # 音频能力检测
    "VideoCapabilities",       # 视频能力检测
    "cr_VAUtil",               # Chromium 视频编解码检测
    "RenderInspector",         # 渲染检查超时
    "getMiuiFreeformStackInfo",# MIUI 窗口信息
    "VRI[MainActivity]",       # MIUI VRI 日志
    "FrameInsert",             # 帧插入错误
    "ProfileInstaller",        # Profile 安装
    "MIUIInput",               # MIUI 输入事件
    "HandWritingStubImpl",     # 手写输入
]


def get_adb():
    """
    仅使用项目内嵌 adb（纯便携模式）。
    """
    project_adb = PROJECT_ROOT / "misc" / "tools" / "android-sdk" / "platform-tools" / "adb.exe"
    if project_adb.exists():
        return str(project_adb)
    # 返回期望的项目内路径，调用方会给出更明确的错误提示
    return str(project_adb)


# 当前选中的设备（用于多设备场景）
SELECTED_DEVICE = None


def get_connected_devices():
    """获取已连接的设备列表，返回 [(device_id, status), ...]"""
    adb = get_adb()
    try:
        result = subprocess.run([adb, "devices"], capture_output=True, encoding='utf-8', errors='replace')
        if result.returncode != 0:
            return []
        devices = []
        for line in result.stdout.strip().split("\n")[1:]:  # 跳过第一行 "List of devices attached"
            line = line.strip()
            if line and "\t" in line:
                parts = line.split("\t")
                if len(parts) >= 2 and parts[1] == "device":
                    devices.append((parts[0], parts[1]))
        return devices
    except Exception:
        return []


def select_device():
    """如果有多个设备，让用户选择一个；如果只有一个，自动选中"""
    global SELECTED_DEVICE
    devices = get_connected_devices()
    
    if not devices:
        print_error("未检测到已连接的 Android 设备")
        SELECTED_DEVICE = None
        return None
    
    if len(devices) == 1:
        SELECTED_DEVICE = devices[0][0]
        print_success(f"已选择设备: {SELECTED_DEVICE}")
        return SELECTED_DEVICE
    
    # 多个设备，让用户选择
    print()
    print("检测到多个设备，请选择要操作的设备：")
    print()
    for i, (device_id, status) in enumerate(devices, 1):
        print(f"  [{i}] {device_id}")
    print()
    
    while True:
        choice = input(f"请输入选项 (1-{len(devices)}): ").strip()
        try:
            idx = int(choice)
            if 1 <= idx <= len(devices):
                SELECTED_DEVICE = devices[idx - 1][0]
                print_success(f"已选择设备: {SELECTED_DEVICE}")
                return SELECTED_DEVICE
        except ValueError:
            pass
        print("无效选项，请重新输入")


def ensure_device_selected():
    """确保已选择设备，如果多设备未选择则自动提示选择。返回 True 表示设备可用，False 表示无设备"""
    global SELECTED_DEVICE
    devices = get_connected_devices()
    
    if not devices:
        print_error("未检测到已连接的 Android 设备")
        SELECTED_DEVICE = None
        return False
    
    if len(devices) == 1:
        # 单设备自动选中
        if SELECTED_DEVICE != devices[0][0]:
            SELECTED_DEVICE = devices[0][0]
            print_info(f"自动选择设备: {SELECTED_DEVICE}")
        return True
    
    # 多设备情况
    if SELECTED_DEVICE:
        # 检查已选设备是否仍然连接
        device_ids = [d[0] for d in devices]
        if SELECTED_DEVICE in device_ids:
            return True
        else:
            print_warning(f"之前选择的设备 {SELECTED_DEVICE} 已断开")
            SELECTED_DEVICE = None
    
    # 多设备且未选择，提示用户选择
    print()
    print("检测到多个设备，请先选择要操作的设备：")
    print()
    for i, (device_id, status) in enumerate(devices, 1):
        print(f"  [{i}] {device_id}")
    print()
    
    while True:
        choice = input(f"请输入选项 (1-{len(devices)}): ").strip()
        try:
            idx = int(choice)
            if 1 <= idx <= len(devices):
                SELECTED_DEVICE = devices[idx - 1][0]
                print_success(f"已选择设备: {SELECTED_DEVICE}")
                print()
                return True
        except ValueError:
            pass
        print("无效选项，请重新输入")


def get_adb_cmd(extra_args=None):
    """获取 ADB 命令列表，如果有选中的设备，自动加上 -s 参数"""
    adb = get_adb()
    cmd = [adb]
    if SELECTED_DEVICE:
        cmd.extend(["-s", SELECTED_DEVICE])
    if extra_args:
        cmd.extend(extra_args)
    return cmd


def _addr_to_ip(addr):
    """从「IP」或「IP:端口」中提取 IP"""
    if not addr or not addr.strip():
        return ""
    s = addr.strip()
    return s.split(":", 1)[0].strip() if ":" in s else s


def load_wifi_history():
    """加载已保存的 WiFi 调试 IP 列表（只存 IP，不存端口；最多 WIFI_HISTORY_MAX 个）"""
    if not WIFI_HISTORY_FILE.exists():
        return []
    try:
        with open(WIFI_HISTORY_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, list):
            return []
        # 兼容旧数据：历史里可能是 IP:端口，统一只保留 IP
        ips = []
        for item in data[:WIFI_HISTORY_MAX]:
            ip = _addr_to_ip(str(item))
            if ip and ip not in ips:
                ips.append(ip)
        return ips
    except Exception:
        return []


def save_wifi_history(addr):
    """将成功连接的地址加入历史并保存（只存 IP，去重、置顶、最多保留 WIFI_HISTORY_MAX 个）"""
    ip = _addr_to_ip(addr)
    if not ip:
        return
    history = load_wifi_history()
    if ip in history:
        history.remove(ip)
    history.insert(0, ip)
    history = history[:WIFI_HISTORY_MAX]
    try:
        WIFI_HISTORY_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(WIFI_HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=0)
    except Exception:
        pass


def get_env_with_java():
    """返回带 JAVA_HOME 的环境变量（若存在本地 JDK）"""
    env = os.environ.copy()
    if JDK_DIR.exists():
        env["JAVA_HOME"] = str(JDK_DIR)
    return env


def find_android_sdk():
    """检测 Android SDK 路径：仅项目内嵌 SDK。"""
    project_sdk = PROJECT_ROOT / "misc" / "tools" / "android-sdk"
    if project_sdk.exists() and (project_sdk / "platform-tools").exists():
        return project_sdk
    return None


def ensure_local_properties():
    """确保 android/local.properties 存在且 sdk.dir 指向 misc/tools/android-sdk（支持相对路径 ../misc/...）。"""
    prop_file = ANDROID_ROOT / "local.properties"
    sdk = find_android_sdk()
    if sdk is None:
        print_error("未找到项目内 Android SDK。请将 SDK 放到：")
        print(f"  {PROJECT_ROOT / 'misc' / 'tools' / 'android-sdk'}")
        print("  并确保包含 platform-tools\\adb.exe")
        return False

    def resolve_sdk_dir(value: str):
        value = value.strip().strip('"').replace("\\\\", "\\")
        if not value:
            return None
        p = Path(value)
        if not p.is_absolute():
            p = (prop_file.parent / value).resolve()
        if p.exists() and (p / "platform-tools").exists():
            return p
        return None

    if prop_file.exists():
        try:
            for line in prop_file.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line.startswith("sdk.dir="):
                    value = line.split("=", 1)[1]
                    if resolve_sdk_dir(value) is not None:
                        return True
                    break
        except Exception:
            pass

    try:
        rel = Path(os.path.relpath(sdk, prop_file.parent)).as_posix()
    except ValueError:
        rel = sdk.as_posix()
    content = (
        "## SDK in repo portable tree (relative to android/)\n"
        f"sdk.dir={rel}\n"
    )
    prop_file.parent.mkdir(parents=True, exist_ok=True)
    prop_file.write_text(content, encoding="utf-8")
    print_info(f"已写入 sdk.dir={rel}")
    return True


def update_gradle_config():
    """
    更新 gradle.properties 中的 org.gradle.java.home
    确保其指向当前项目内嵌的 tools/jdk-17，实现便携性
    """
    if not JDK_DIR.exists():
        return

    gradle_props_path = ANDROID_ROOT / "gradle.properties"
    if not gradle_props_path.exists():
        return

    # 转换路径格式 (Windows 下必须用正斜杠或双反斜杠)
    jdk_path_str = str(JDK_DIR).replace("\\", "/")

    try:
        new_lines = []
        key_found = False
        
        with open(gradle_props_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            
        for line in lines:
            if line.strip().startswith('org.gradle.java.home='):
                new_lines.append(f"org.gradle.java.home={jdk_path_str}\n")
                key_found = True
            else:
                new_lines.append(line)
        
        if not key_found:
            new_lines.append(f"\n# 自动添加的 JDK 路径\norg.gradle.java.home={jdk_path_str}\n")
            
        with open(gradle_props_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
            
    except Exception:
        pass


def run(cmd, env=None, check=False, shell=False):
    """执行命令，返回 (returncode, stdout, stderr) 或直接继承终端"""
    if env is None:
        env = os.environ.copy()
    try:
        capture = not (cmd[0] == get_adb() and "logcat" in cmd)
        result = subprocess.run(
            cmd,
            env=env,
            shell=shell,
            check=check,
            capture_output=capture,
            # 使用 UTF-8 编码并忽略无法解码的字符，避免 Windows 下 GBK 解码错误
            encoding='utf-8',
            errors='replace',
        )
        if result.stdout is not None:
            return result.returncode, result.stdout or "", result.stderr or ""
        return result.returncode, "", ""
    except FileNotFoundError:
        return -1, "", "命令未找到"
    except Exception as e:
        return -1, "", str(e)


def section(title):
    """打印分节标题"""
    print()
    print("=" * 40)
    print(title)
    print("=" * 40)
    print()


def check_device():
    """[1] 检查设备连接"""
    section("检查设备连接")
    adb = get_adb()
    code, out, err = run([adb, "devices"])
    if code != 0:
        print_error("ADB 未找到或执行失败")
        print("请检查：")
        print("  1. 是否存在项目内 ADB: misc\\tools\\android-sdk\\platform-tools\\adb.exe")
        print("  2. 设备是否已开启 USB 调试并授权")
        return False
    if out:
        print(out)
    devices = get_connected_devices()
    if not devices:
        print_error("未检测到已连接的 Android 设备")
        print("请检查：")
        print("  1. 手机是否已开启 USB 调试")
        print("  2. 数据线是否支持 USB 传输")
        print("  3. 是否已授权本机进行 USB 调试")
        return False
    print_success(f"已检测到 {len(devices)} 台 Android 设备")
    return True


def build_apk(release=False):
    """[2] 编译 APK"""
    apk_type = "Release" if release else "Debug"
    section(f"编译 {apk_type} APK")
    
    # 更新 Gradle 配置
    update_gradle_config()
    
    if not ensure_local_properties():
        return False
    env = get_env_with_java()
    if JDK_DIR.exists():
        print_info(f"使用 JDK: {JDK_DIR}")
    else:
        print_warning("未检测到本地 Java，使用系统 Java")
    # 停止旧 Gradle 守护进程，避免其仍使用迁移前的 org.gradle.java.home（如 tools/jdk-17）
    try:
        subprocess.run(
            [str(GRADLEW), "--stop"],
            env=env,
            cwd=ANDROID_ROOT,
            capture_output=True,
            timeout=15,
        )
    except Exception:
        pass
    print("提示: 首次编译会启动 Gradle Daemon 并可能下载依赖，约需 1～3 分钟，请耐心等待。")
    print()
    
    # 编译命令
    task = "assembleRelease" if release else "assembleDebug"
    gradlew_cmd = [str(GRADLEW), task, "--console=plain"]
    print_info(f"正在编译 {apk_type} APK，请稍候...")
    code = subprocess.run(gradlew_cmd, env=env, cwd=ANDROID_ROOT).returncode
    if code != 0:
        print_error(f"APK 编译失败，请检查上述错误信息")
        return False
    
    apk_path = APK_RELEASE_PATH if release else APK_PATH
    if not apk_path.exists():
        print_error(f"APK 文件未找到: {apk_path}")
        return False
    size = apk_path.stat().st_size
    print_success(f"APK 已生成: {apk_path}")
    print(f"文件大小: {size / 1024:.1f} KB")
    return True


def clean_build():
    """清理构建缓存"""
    section("清理构建缓存")
    env = get_env_with_java()
    gradlew_cmd = [str(GRADLEW), "clean", "--console=plain"]
    print_info("正在清理构建缓存...")
    code = subprocess.run(gradlew_cmd, env=env, cwd=ANDROID_ROOT).returncode
    if code == 0:
        print_success("构建缓存已清理")
        return True
    print_error("清理失败")
    return False


def install_apk(release=False):
    """[3] 安装 APK（先尝试更新，失败后再卸载重装）"""
    apk_type = "Release" if release else "Debug"
    section(f"安装 {apk_type} APK")
    if not ensure_device_selected():
        return False
    
    apk_path = APK_RELEASE_PATH if release else APK_PATH
    if not apk_path.exists():
        print_error(f"APK 文件未找到: {apk_path}")
        r = input("是否先编译 APK？(Y/N, 默认 Y): ").strip().upper() or "Y"
        if r == "N":
            print("已取消安装")
            return False
        if not build_apk(release=release):
            return False
    
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    
    # 第一步：尝试覆盖安装（保留用户数据）
    print_info("尝试更新安装（使用 -r 参数覆盖安装）...")
    cmd_update = get_adb_cmd(["install", "-r", str(apk_path)])
    code, out, err = run(cmd_update)
    
    if code == 0 and "Success" in (out + err):
        print_success("APK 更新安装成功（已保留用户数据）")
        return True
    
    # 更新失败，分析错误原因
    msg = (err or out) or ""
    print_warning("更新安装失败，可能是签名不匹配或其他原因")
    if msg:
        print(f"错误信息: {msg[:200]}")  # 只显示前200字符
    
    # 第二步：卸载旧版本后重新安装
    print_info(f"正在卸载旧版本 {APP_PACKAGE}...")
    code_uninstall, _, _ = run(get_adb_cmd(["uninstall", APP_PACKAGE]))
    if code_uninstall == 0:
        print_success("旧版本卸载成功")
    else:
        print_warning("卸载跳过（App 可能尚未安装）")
    
    # 重新安装
    print_info("正在重新安装 APK...")
    cmd_install = get_adb_cmd(["install", str(apk_path)])
    code, out, err = run(cmd_install)
    if code != 0:
        msg = (err or out) or ""
        print_error("APK 安装失败，请检查设备连接与权限")
        if msg:
            print(msg)
        return False
    print_success("APK 已安装（注意：用户数据可能已被清除）")
    return True


def launch_app():
    """[4] 启动应用"""
    section("启动应用")
    if not ensure_device_selected():
        return False
    print_info("正在启动应用...")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    code, _, _ = run(get_adb_cmd(["shell", "am", "start", "-n", f"{APP_PACKAGE}/{APP_ACTIVITY}"]))
    if code != 0:
        print_error("启动失败，请检查应用是否已安装、设备是否已连接")
        return False
    print_success("应用已启动")
    return True


def stop_app():
    """[5] 停止应用"""
    section("停止应用")
    if not ensure_device_selected():
        return False
    print_info("正在停止应用...")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    code, _, _ = run(get_adb_cmd(["shell", "am", "force-stop", APP_PACKAGE]))
    if code != 0:
        print_error("停止应用失败")
        return False
    print_success("应用已停止")
    return True


def get_app_pid():
    """获取应用的 PID，如果应用未运行则返回 None"""
    code, out, _ = run(get_adb_cmd(["shell", "pidof", APP_PACKAGE]))
    if code == 0 and out.strip():
        # 可能有多个进程，取第一个
        return out.strip().split()[0]
    return None


def view_logs():
    """[6] 查看日志（实时，仅本应用，自动过滤刷屏日志）"""
    section("查看日志（实时）")
    if not ensure_device_selected():
        return False
    
    # 获取应用 PID，按进程过滤显示所有日志
    pid = get_app_pid()
    if pid:
        print_info(f"正在实时显示本应用日志 (PID: {pid})，按 Ctrl+C 退出...")
        print("        应用重启后需重新进入此菜单刷新 PID")
    else:
        print_warning("应用未运行，请先启动应用")
        print("        提示：请先选择 [4] 启动应用")
        return False
    
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    print_info("已启用智能过滤（排除刷屏日志）")
    print("-" * 50)
    
    # 使用 --pid 过滤（显示该进程的所有日志）
    cmd = get_adb_cmd(["logcat", "--pid", pid])
    
    proc = None
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            shell=False,
            text=True,
            encoding='utf-8',
            errors='replace',
            bufsize=1,
        )
        for line in proc.stdout:
            # 检查是否应该过滤此行
            should_filter = any(pattern in line for pattern in FILTER_PATTERNS)
            if not should_filter:
                print(line, end='')
        proc.wait()
    except KeyboardInterrupt:
        if proc is not None:
            try:
                proc.terminate()
                proc.wait()
            except Exception:
                pass
        print("\n已退出日志查看")
    except Exception as e:
        print_error(f"{e}")
    print()
    print("=" * 40)
    print("日志查看已结束")
    print("=" * 40)
    return True


def clear_logs():
    """[7] 清除日志缓冲区"""
    section("清除日志缓冲区")
    if not ensure_device_selected():
        return False
    print_info("正在清除设备日志缓冲区...")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    code, _, err = run(get_adb_cmd(["logcat", "-c"]))
    if code != 0:
        print_error("清除日志失败")
        return False
    print_success("日志缓冲区已清除")
    return True


def uninstall_app():
    """[8] 卸载应用"""
    section("卸载应用")
    if not ensure_device_selected():
        return False
    print_warning(f"即将卸载: {APP_PACKAGE}")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    r = input("确认卸载？(Y/N, 默认 N): ").strip().upper() or "N"
    if r != "Y":
        print("已取消卸载")
        return True
    print_info("正在卸载...")
    code, _, _ = run(get_adb_cmd(["uninstall", APP_PACKAGE]))
    if code != 0:
        print_error("卸载失败")
        return False
    print_success("应用已卸载")
    return True


def build_install_launch(release=False):
    """[9] 一键执行（编译 + 安装 + 启动）"""
    apk_type = "Release" if release else "Debug"
    section(f"一键执行（编译+安装+启动 {apk_type}）")
    
    # 先选择设备（如果有多个）
    device = select_device()
    if not device:
        print_error("未选择设备，无法继续")
        return False
    
    if not check_device():
        print_error("设备检查失败，无法继续")
        return False
    if not build_apk(release=release):
        print_error("编译失败，无法继续")
        return False
    if not install_apk(release=release):
        print_error("安装失败，无法继续")
        return False
    if not launch_app():
        print_error("启动失败，无法继续")
        return False
    print()
    print("=" * 40)
    print("一键执行完成")
    print("=" * 40)
    print()
    print("已完成：")
    print_success(f"{apk_type} APK 已编译")
    print_success("APK 已安装")
    print_success("应用已启动")
    print()
    print("可选择选项 6 查看实时日志")
    return True


def take_screenshot():
    """截取设备屏幕"""
    section("截取屏幕")
    if not ensure_device_selected():
        return False
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"screenshot_{timestamp}.png"
    local_path = PROJECT_ROOT / filename
    
    print_info("正在截取屏幕...")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    
    # 截图并拉取
    run(get_adb_cmd(["shell", "screencap", "-p", "/sdcard/screen.png"]))
    run(get_adb_cmd(["pull", "/sdcard/screen.png", str(local_path)]))
    run(get_adb_cmd(["shell", "rm", "/sdcard/screen.png"]))
    
    if local_path.exists():
        print_success(f"截图已保存: {local_path}")
        return True
    print_error("截图失败")
    return False


def view_crash_logs():
    """[A] 查看崩溃日志"""
    section("查看崩溃日志")
    if not ensure_device_selected():
        return False
    print_info("正在显示最近的崩溃日志...")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    print()
    
    # 获取应用 PID
    pid = get_app_pid()
    if pid:
        # 只显示本应用的错误日志
        cmd = get_adb_cmd(["logcat", "-d", "-v", "time", "--pid", pid, "*:E"])
    else:
        # 应用未运行，显示所有 AndroidRuntime 错误
        cmd = get_adb_cmd(["logcat", "-d", "-v", "time", "AndroidRuntime:E", "*:S"])
    
    code, out, err = run(cmd)
    if out:
        print(out)
    else:
        print_info("未发现崩溃日志")
    print()
    print("=" * 40)
    print("崩溃日志查看完成")
    print("=" * 40)
    print()
    print("提示：如果看到崩溃信息，请检查：")
    print("  1. 相册/存储权限是否已授予")
    print("  2. Android 版本是否兼容（minSdk 26）")
    print("  3. 查看堆栈中是否有 Coil / Compose 相关异常")
    return True


def connect_wireless_adb():
    """[W] 连接无线 ADB 设备"""
    global SELECTED_DEVICE
    section("连接无线 ADB 设备")
    
    history = load_wifi_history()
    
    print("无线 ADB 连接方式：")
    print()
    print("  [1] 输入 IP:端口 直接连接（需设备已开启无线调试）")
    print("  [2] 通过配对码配对新设备（Android 11+）")
    print("  [3] 从历史 IP 选择（选后只需输入端口即可连接）")
    if history:
        print("  （有历史时也可直接输入端口，用第一个 IP 连接）")
    print()
    if history:
        for i, ip in enumerate(history, 1):
            print(f"       {i}. {ip}")
        print()
    
    mode = input("请选择 (1/2/3 或直接输入端口): ").strip()
    
    # 直接输入端口：用第一个历史 IP 连接
    # 注意：1/2/3 是菜单选项，不能在这里被当作端口
    if mode.isdigit() and history and mode not in ("1", "2", "3"):
        addr = f"{history[0]}:{mode}"
        print_info(f"正在连接 {addr}...")
        adb = get_adb()
        code, out, err = run([adb, "connect", addr])
        if code == 0 and "connected" in (out + err).lower():
            print_success(f"已连接到 {addr}")
            SELECTED_DEVICE = addr
            save_wifi_history(addr)
            return True
        else:
            print_error("连接失败")
            if out:
                print(out)
            if err:
                print(err)
            return False
    
    if mode == "3":
        # 从历史选择 IP，再输入端口连接
        if not history:
            print_warning("暂无历史 IP，请先用 [1] 或 [2] 连接一次")
            return False
        print()
        for i, ip in enumerate(history, 1):
            print(f"  [{i}] {ip}")
        print()
        choice = input(f"请输入选项 (1-{len(history)}): ").strip()
        try:
            idx = int(choice)
            if 1 <= idx <= len(history):
                ip = history[idx - 1]
                port_in = input("请输入端口 (直接回车使用 5555): ").strip()
                port = port_in if port_in else "5555"
                addr = f"{ip}:{port}"
                print_info(f"正在连接 {addr}...")
                adb = get_adb()
                code, out, err = run([adb, "connect", addr])
                if code == 0 and "connected" in (out + err).lower():
                    print_success(f"已连接到 {addr}")
                    SELECTED_DEVICE = addr
                    save_wifi_history(addr)  # 只存 IP 到历史
                    return True
                else:
                    print_error("连接失败")
                    if out:
                        print(out)
                    if err:
                        print(err)
                    return False
        except ValueError:
            pass
        print_error("无效选项")
        return False
    
    if mode == "1":
        # 直接连接
        print()
        print("请输入设备的 IP 地址和端口（格式：IP:端口，或只输 IP 则用 5555）")
        print("例如：192.168.1.100:5555")
        if history:
            print("提示：有历史 IP 时也可只输入端口（如 32975），将用上一历史 IP 连接")
        print()
        addr = input("IP:端口: ").strip()
        if not addr:
            print("已取消")
            return False
        
        # 解析：可能是「IP:端口」「仅 IP」「仅端口」
        if ":" not in addr:
            if addr.isdigit() and history:
                # 只输入了端口，用历史第一个 IP
                addr = f"{history[0]}:{addr}"
            else:
                # 只输入了 IP，默认端口 5555
                addr += ":5555"
        
        print_info(f"正在连接 {addr}...")
        adb = get_adb()
        code, out, err = run([adb, "connect", addr])
        
        if code == 0 and "connected" in (out + err).lower():
            print_success(f"已连接到 {addr}")
            SELECTED_DEVICE = addr
            save_wifi_history(addr)
            return True
        else:
            print_error("连接失败")
            if out:
                print(out)
            if err:
                print(err)
            return False
    
    elif mode == "2":
        # 配对模式（Android 11+）
        print()
        print("请在手机上：")
        print("  1. 打开 设置 > 开发者选项 > 无线调试")
        print("  2. 点击「使用配对码配对设备」")
        print("  3. 手机上会显示 IP:端口 和 配对码")
        print()
        
        pair_addr = input("配对用 IP:端口: ").strip()
        if not pair_addr:
            print("已取消")
            return False
        
        pair_code = input("配对码: ").strip()
        if not pair_code:
            print("已取消")
            return False
        
        print_info(f"正在配对 {pair_addr}...")
        adb = get_adb()
        
        # 使用 subprocess 直接运行，因为 pair 命令需要交互
        proc = subprocess.Popen(
            [adb, "pair", pair_addr],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            encoding='utf-8',
            errors='replace'
        )
        out, err = proc.communicate(input=pair_code + "\n", timeout=30)
        
        if "Successfully paired" in (out + err) or "成功" in (out + err):
            print_success("配对成功！")
            print()
            print("现在需要连接设备。")
            print("请查看手机「无线调试」页面上显示的 IP 地址和端口")
            print("（注意：连接端口和配对端口可能不同）")
            print()
            
            conn_addr = input("连接用 IP:端口: ").strip()
            if conn_addr:
                code, out, err = run([adb, "connect", conn_addr])
                if code == 0 and "connected" in (out + err).lower():
                    print_success(f"已连接到 {conn_addr}")
                    SELECTED_DEVICE = conn_addr
                    save_wifi_history(conn_addr)
                    return True
                else:
                    print_error("连接失败")
                    if out:
                        print(out)
                    if err:
                        print(err)
            return False
        else:
            print_error("配对失败")
            if out:
                print(out)
            if err:
                print(err)
            return False
    
    else:
        print("无效选项")
        return False


def save_logs():
    """[S] 保存日志到文件（自动过滤刷屏日志）"""
    section("保存日志")
    if not ensure_device_selected():
        return False
    
    # 确保日志目录存在
    LOGS_DIR.mkdir(parents=True, exist_ok=True)
    
    # 获取设备型号
    try:
        adb = get_adb()
        model_cmd = [adb]
        if SELECTED_DEVICE:
            model_cmd.extend(["-s", SELECTED_DEVICE])
        model_cmd.extend(["shell", "getprop", "ro.product.model"])
        model_res = subprocess.run(model_cmd, capture_output=True, text=True)
        device_model = model_res.stdout.strip().replace(" ", "_")
    except:
        device_model = "unknown_device"
    
    # 生成带时间戳的文件名
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    log_file = LOGS_DIR / f"{device_model}_{timestamp}.log"
    
    print_info(f"正在获取设备日志...")
    if SELECTED_DEVICE:
        print_info(f"目标设备: {SELECTED_DEVICE}")
    print_info(f"保存路径: {log_file}")
    
    # 获取应用 PID，按进程过滤
    pid = get_app_pid()
    if pid:
        print_info(f"应用 PID: {pid}，将保存该进程的所有日志")
        cmd = get_adb_cmd(["logcat", "-d", "-v", "time", "--pid", pid])
    else:
        print_warning("应用未运行，将保存所有日志（可能不完整）")
        cmd = get_adb_cmd(["logcat", "-d", "-v", "time"])
    print()
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            timeout=30,
            shell=False,
            text=True,
            encoding='utf-8',
            errors='replace'
        )
        
        if result.returncode != 0:
            print_error("获取日志失败")
            if result.stderr:
                print(result.stderr)
            return False
        
        # 过滤刷屏日志
        filtered_lines = []
        for line in result.stdout.splitlines():
            if not any(pattern in line for pattern in FILTER_PATTERNS):
                filtered_lines.append(line)
        
        # 写入文件
        log_file.write_text("\n".join(filtered_lines), encoding="utf-8")
        file_size = log_file.stat().st_size
        print_success(f"日志已保存")
        print(f"文件: {log_file}")
        print(f"大小: {file_size / 1024:.1f} KB")
        if file_size == 0:
            print_warning("日志文件为空，可能设备日志缓冲区已被清除")
        else:
            # 显示日志的前几行作为预览
            lines = filtered_lines[:5]
            if lines:
                print()
                print("日志预览（前5行）：")
                for line in lines:
                    if line.strip():
                        print(f"  {line[:100]}")
    except subprocess.TimeoutExpired:
        print_error("获取日志超时")
        return False
    except Exception as e:
        print_error(f"保存日志文件失败: {e}")
        return False
    
    return True


def main_menu():
    """主菜单循环"""
    # 检查 ADB 状态
    adb = get_adb()
    try:
        result = subprocess.run([adb, "version"], capture_output=True, text=True)
        adb_ok = result.returncode == 0
    except:
        adb_ok = False
    
    while True:
        if IS_WINDOWS:
            os.system("cls")
        else:
            os.system("clear")
        
        print(f"{CYAN}{'='*50}{RESET}")
        print(f"{CYAN}  HDR Viewer - 安装调试工具{RESET}")
        print(f"{CYAN}{'='*50}{RESET}")
        print()
        
        # 状态信息
        adb_status = f"{GREEN}✓ 可用{RESET}" if adb_ok else f"{RED}✗ 未安装{RESET}"
        devices = get_connected_devices()
        device_status = f"{GREEN}✓ {len(devices)} 台设备{RESET}" if devices else f"{YELLOW}✗ 无设备{RESET}"
        
        print(f"  ADB 状态: {adb_status}")
        print(f"  设备状态: {device_status}")
        if SELECTED_DEVICE:
            print(f"  当前设备: {GREEN}{SELECTED_DEVICE}{RESET}")
        print(f"  App 目录: {ANDROID_ROOT}")
        print()
        
        print("  +-------------------------------------+")
        print("  |  [1] 检查设备连接                   |")
        print("  |  [2] 编译 Debug APK                 |")
        print("  |  [3] 编译 Release APK               |")
        print("  |  [4] 安装 Debug APK                 |")
        print("  |  [5] 安装 Release APK               |")
        print("  +-------------------------------------+")
        print("  |  [6] 启动应用                       |")
        print("  |  [7] 停止应用                       |")
        print("  |  [8] 卸载应用                       |")
        print("  +-------------------------------------+")
        print("  |  [9] 一键编译+安装+启动 (Debug)     |")
        print("  |  [R] 一键编译+安装+启动 (Release)   |")
        print("  +-------------------------------------+")
        print("  |  [L] 查看日志 (实时)                |")
        print("  |  [S] 保存日志到文件                 |")
        print("  |  [A] 查看崩溃日志                   |")
        print("  |  [C] 清除日志缓冲区                 |")
        print("  +-------------------------------------+")
        print("  |  [P] 截取屏幕                       |")
        print("  |  [D] 选择设备                       |")
        print("  |  [W] 连接无线 ADB                   |")
        print("  |  [X] 清理构建缓存                   |")
        print("  |  [0] 退出                           |")
        print("  +-------------------------------------+")
        print()
        choice = input("请选择操作: ").strip().upper()

        if choice == "1":
            check_device()
        elif choice == "2":
            build_apk(release=False)
        elif choice == "3":
            build_apk(release=True)
        elif choice == "4":
            install_apk(release=False)
        elif choice == "5":
            install_apk(release=True)
        elif choice == "6":
            launch_app()
        elif choice == "7":
            stop_app()
        elif choice == "8":
            uninstall_app()
        elif choice == "9":
            build_install_launch(release=False)
        elif choice == "R":
            build_install_launch(release=True)
        elif choice == "L":
            view_logs()
        elif choice == "S":
            save_logs()
        elif choice == "A":
            view_crash_logs()
        elif choice == "C":
            clear_logs()
        elif choice == "P":
            take_screenshot()
        elif choice == "D":
            section("选择设备")
            select_device()
        elif choice == "W":
            connect_wireless_adb()
        elif choice == "X":
            clean_build()
        elif choice == "0":
            print()
            print_success("再见！")
            break
        else:
            if choice:
                print_error("无效选项，请重新选择")

        if choice and choice != "0":
            input("\n按回车键继续...")


if __name__ == "__main__":
    main_menu()
