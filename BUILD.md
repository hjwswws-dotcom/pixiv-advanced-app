# Build 流程文档

## 概述

本文档描述 PixivAdvanced Android 项目的完整构建流程，包括本地构建和 CI/CD 构建。

---

## 本地构建

### 环境要求

| 工具 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Android SDK | API 34 |
| Gradle | 8.2+ |
| Python | 3.8+ (用于预检) |

### 环境配置

```bash
# 设置环境变量 (Linux/Mac)
export ANDROID_HOME=/path/to/android/sdk
export JAVA_HOME=/path/to/jdk17

# Windows
set ANDROID_HOME=C:\path\to\android\sdk
set JAVA_HOME=C:\path\to\jdk17
```

### 构建步骤

#### 1. 克隆代码

```bash
git clone https://github.com/hjwswws-dotcom/pixiv-advanced-app.git
cd pixiv-advanced-app
```

#### 2. 运行预检 (推荐)

```bash
# Python 方式 (推荐)
python tools/preflight.py

# PowerShell 方式 (Windows)
powershell -File tools/preflight.ps1
```

预检项目：
- ✅ 关键工程文件存在性检查
- ✅ XML 文件合法性检查
- ✅ Kotlin 语法基础检查
- ✅ Gradle 配置结构检查
- ✅ GitHub Actions Workflow 语法检查

#### 3. 构建 Debug APK

```bash
# 方式一：使用 gradlew (推荐)
./gradlew assembleDebug

# 方式二：使用 gradle
gradle assembleDebug
```

#### 4. 获取 APK

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## CI/CD 构建

### GitHub Actions 工作流

#### 1. syntax-check.yml (语法检查)

**触发条件：**
- Push/Pull Request 修改以下文件时：
  - `**.kt`
  - `**.xml`
  - `**.gradle.kts`
  - `**.yml`
  - `tools/**`

**检查项目：**
- 关键工程文件存在性
- XML 合法性
- Kotlin 语法基础检查
- Gradle 配置结构
- Workflow YAML 语法

#### 2. build.yml (APK 构建)

**触发条件：**
- Push/Pull Request 到 main/master 分支
- 手动触发 (workflow_dispatch)

**前置条件：**
- syntax-check 必须通过
- 只有语法检查通过后才执行构建

**构建步骤：**
1. Checkout 代码
2. Setup JDK 17
3. Setup Android SDK
4. 执行 `./gradlew assembleDebug`
5. 上传 APK 到 Artifacts

### 查看构建状态

访问：https://github.com/hjwswws-dotcom/pixiv-advanced-app/actions

---

## 常见问题

### 1. 预检失败

**问题：** 预检脚本报错

**解决：**
```bash
# 查看具体错误信息
python tools/preflight.py

# 常见修复：
# - 检查文件是否完整克隆
# - 检查 XML 语法错误
# - 检查 Kotlin 语法错误
```

### 2. Gradle 构建失败

**问题：** `./gradlew assembleDebug` 失败

**解决：**
```bash
# 清理构建缓存
./gradlew clean

# 重新构建
./gradlew assembleDebug --stacktrace

# 检查错误信息
# 常见问题：
# - JDK 版本不对 → 确保 JDK 17+
# - Android SDK 未配置 → 设置 ANDROID_HOME
# - 依赖下载失败 → 检查网络
```

### 3. GitHub Actions 构建失败

**问题：** CI 构建失败

**解决：**
1. 查看 Actions 日志确定错误类型
2. 本地运行预检确认问题
3. 修复后重新推送

---

## 版本信息

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.1 | 2026-03-16 | 初始版本 |
| 1.0.2 | 2026-03-16 | 添加预检系统 |

---

## 附录

### 完整构建命令列表

```bash
# 完整本地构建流程
git clone https://github.com/hjwswws-dotcom/pixiv-advanced-app.git
cd pixiv-advanced-app
python tools/preflight.py
./gradlew assembleDebug

# APK 位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 目录结构

```
pixiv-advanced-app/
├── .github/
│   └── workflows/
│       ├── build.yml          # APK 构建工作流
│       └── syntax-check.yml   # 语法检查工作流
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/paf/app/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── api/
│       │   │   └── model/
│       │   ├── domain/
│       │   └── ui/
│       └── res/
├── tools/
│   ├── preflight.ps1          # PowerShell 预检脚本
│   └── preflight.py           # Python 预检脚本
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
