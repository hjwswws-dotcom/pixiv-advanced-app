#!/usr/bin/env python3
"""
PixivAdvanced Preflight Check Script
用途：本地预检 Kotlin 语法、XML 合法性、工程结构
运行：python tools/preflight.py
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Tuple

# 项目根目录
PROJECT_ROOT = Path(__file__).parent.parent.resolve()

# ANSI 颜色
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
RESET = "\033[0m"

FAILED = False


def log_info(msg: str, color: str = ""):
    """打印日志"""
    print(f"{color}{msg}{RESET}")


def log_ok(msg: str):
    log_info(f"  [OK] {msg}", GREEN)


def log_fail(msg: str):
    global FAILED
    FAILED = True
    log_info(f"  [FAIL] {msg}", RED)


def log_warn(msg: str):
    log_info(f"  [WARN] {msg}", YELLOW)


# ============================================
# 1. 关键工程文件存在检查
# ============================================
def check_required_files() -> bool:
    log_info("[1/5] 检查关键工程文件...", YELLOW)

    required_files = [
        "app/build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/paf/app/MainActivity.kt",
        "gradle/wrapper/gradle-wrapper.properties",
        ".github/workflows/build.yml",
    ]

    all_passed = True
    for file in required_files:
        path = PROJECT_ROOT / file
        if path.exists():
            log_ok(file)
        else:
            log_fail(f"Missing: {file}")
            all_passed = False

    return all_passed


# ============================================
# 2. XML 合法性检查
# ============================================
def check_xml_files() -> bool:
    log_info("", YELLOW)
    log_info("[2/5] 检查 XML 文件合法性...", YELLOW)

    xml_files = [
        PROJECT_ROOT / "app/src/main/AndroidManifest.xml",
        PROJECT_ROOT / "app/src/main/res/values/strings.xml",
    ]

    # 添加 res 目录下所有 XML
    res_dir = PROJECT_ROOT / "app/src/main/res"
    if res_dir.exists():
        xml_files.extend(res_dir.rglob("*.xml"))

    all_passed = True
    for xml_file in xml_files:
        if xml_file.exists():
            try:
                ET.parse(xml_file)
                rel_path = xml_file.relative_to(PROJECT_ROOT)
                log_ok(str(rel_path))
            except ET.ParseError as e:
                rel_path = xml_file.relative_to(PROJECT_ROOT)
                log_fail(f"Invalid XML: {rel_path} - {e}")
                all_passed = False
            except Exception as e:
                rel_path = xml_file.relative_to(PROJECT_ROOT)
                log_fail(f"Error parsing {rel_path}: {e}")
                all_passed = False

    return all_passed


# ============================================
# 3. Kotlin 语法检查 (基础检查)
# ============================================
def check_kotlin_syntax() -> bool:
    log_info("", YELLOW)
    log_info("[3/5] 检查 Kotlin 文件语法...", YELLOW)

    kt_files = list((PROJECT_ROOT / "app/src/main/java").rglob("*.kt"))

    if not kt_files:
        log_warn("No Kotlin files found")
        return True

    errors = 0
    for kt_file in kt_files:
        content = kt_file.read_text(encoding="utf-8", errors="ignore")
        rel_path = kt_file.relative_to(PROJECT_ROOT)

        # 检查括号匹配
        open_brace = content.count("{")
        close_brace = content.count("}")
        if open_brace != close_brace:
            log_warn(
                f"Brace mismatch in: {rel_path} (open: {open_brace}, close: {close_brace})"
            )

        open_paren = content.count("(")
        close_paren = content.count(")")
        if open_paren != close_paren:
            log_warn(
                f"Parenthesis mismatch in: {rel_path} (open: {open_paren}, close: {close_paren})"
            )

        # 检查常见语法错误
        # 空 import
        if re.search(r"^\s*import\s+\.$", content, re.MULTILINE):
            log_fail(f"Invalid import in: {rel_path}")
            errors += 1

        # 检查未闭合的字符串（简化检查）
        single_quote = content.count("'")
        double_quote = content.count('"')
        if single_quote % 2 != 0:
            log_warn(f"Unmatched single quotes in: {rel_path}")
        if double_quote % 2 != 0:
            log_warn(f"Unmatched double quotes in: {rel_path}")

        # 检查 // 注释是否正确关闭
        if re.search(r"//.*[^/]$", content, re.MULTILINE):
            log_warn(f"Potential unclosed comment in: {rel_path}")

    if errors == 0:
        log_ok("Kotlin syntax check passed")

    return errors == 0


# ============================================
# 4. Gradle 配置检查
# ============================================
def check_gradle_config() -> bool:
    log_info("", YELLOW)
    log_info("[4/5] 检查 Gradle 配置...", YELLOW)

    all_passed = True

    # 检查 settings.gradle.kts
    settings_file = PROJECT_ROOT / "settings.gradle.kts"
    if settings_file.exists():
        content = settings_file.read_text(encoding="utf-8", errors="ignore")
        has_root = bool(re.search(r"rootProject\.name\s*=", content))
        has_include = bool(re.search(r"include\s*\(", content))

        if has_root and has_include:
            log_ok("settings.gradle.kts 结构正确")
        else:
            log_fail("settings.gradle.kts 缺少必要配置")
            all_passed = False
    else:
        log_fail("settings.gradle.kts 不存在")
        all_passed = False

    # 检查 app/build.gradle.kts
    app_build_file = PROJECT_ROOT / "app/build.gradle.kts"
    if app_build_file.exists():
        content = app_build_file.read_text(encoding="utf-8", errors="ignore")
        has_android = bool(re.search(r"android\s*\{", content))
        has_deps = bool(re.search(r"dependencies\s*\{", content))

        if has_android and has_deps:
            log_ok("app/build.gradle.kts 结构正确")
        else:
            log_fail("app/build.gradle.kts 缺少必要配置")
            all_passed = False
    else:
        log_fail("app/build.gradle.kts 不存在")
        all_passed = False

    return all_passed


# ============================================
# 5. GitHub Actions Workflow 检查
# ============================================
def check_workflows() -> bool:
    log_info("", YELLOW)
    log_info("[5/5] 检查 GitHub Actions Workflow...", YELLOW)

    workflow_dir = PROJECT_ROOT / ".github/workflows"
    if not workflow_dir.exists():
        log_warn(".github/workflows 目录不存在")
        return True

    all_passed = True
    for workflow_file in workflow_dir.glob("*.yml"):
        try:
            content = workflow_file.read_text(encoding="utf-8", errors="ignore")

            # 基础 YAML 语法检查
            has_name = "name:" in content
            has_on = "on:" in content

            if has_name and has_on:
                log_ok(workflow_file.name)
            else:
                log_fail(f"{workflow_file.name} 缺少必要字段")
                all_passed = False
        except Exception as e:
            log_fail(f"{workflow_file.name} 解析失败: {e}")
            all_passed = False

    return all_passed


# ============================================
# 主函数
# ============================================
def main():
    global FAILED

    print(f"{CYAN}========================================{RESET}")
    print(f"{CYAN}  PixivAdvanced Preflight Check{RESET}")
    print(f"{CYAN}========================================{RESET}")
    print()

    # 运行所有检查
    check_required_files()
    check_xml_files()
    check_kotlin_syntax()
    check_gradle_config()
    check_workflows()

    # 总结
    print()
    print(f"{CYAN}========================================{RESET}")

    if FAILED:
        print(f"{RED}  Preflight Check: FAILED{RESET}")
        print(f"{RED}  请修复上述错误后重试{RESET}")
        print(f"{CYAN}========================================{RESET}")
        sys.exit(1)
    else:
        print(f"{GREEN}  Preflight Check: PASSED{GREEN}")
        print(f"{GREEN}  所有检查项通过{GREEN}")
        print(f"{CYAN}========================================{RESET}")
        sys.exit(0)


if __name__ == "__main__":
    main()
