# PixivAdvanced Preflight Check Script
# 用途：本地预检 Kotlin 语法、XML 合法性、工程结构
# 运行：powershell -File tools/preflight.ps1

$ErrorActionPreference = "Continue"
$Script:FAILED = $false
$ProjectRoot = $PSScriptRoot -replace '\\tools$', ''

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PixivAdvanced Preflight Check" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ============================================
# 1. 关键工程文件存在检查
# ============================================
Write-Host "[1/5] 检查关键工程文件..." -ForegroundColor Yellow

$RequiredFiles = @(
    "app/build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/paf/app/MainActivity.kt",
    "gradle/wrapper/gradle-wrapper.properties",
    ".github/workflows/build.yml"
)

foreach ($file in $RequiredFiles) {
    $path = Join-Path $ProjectRoot $file
    if (Test-Path $path) {
        Write-Host "  [OK] $file" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Missing: $file" -ForegroundColor Red
        $Script:FAILED = $true
    }
}

# ============================================
# 2. XML 合法性检查
# ============================================
Write-Host ""
Write-Host "[2/5] 检查 XML 文件合法性..." -ForegroundColor Yellow

$XmlFiles = @(
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/values/strings.xml"
)

# 检查 res 目录下所有 XML
$resXmlFiles = Get-ChildItem -Path (Join-Path $ProjectRoot "app/src/main/res") -Recurse -Include "*.xml" -ErrorAction SilentlyContinue
if ($resXmlFiles) {
    $XmlFiles += $resXmlFiles.FullName
}

foreach ($file in $XmlFiles) {
    if (Test-Path $file) {
        try {
            [xml]$xml = Get-Content $file -ErrorAction Stop
            $relativePath = $file.Replace($ProjectRoot, "").TrimStart("\", "/")
            Write-Host "  [OK] $relativePath" -ForegroundColor Green
        } catch {
            $relativePath = $file.Replace($ProjectRoot, "").TrimStart("\", "/")
            Write-Host "  [FAIL] Invalid XML: $relativePath - $($_.Exception.Message)" -ForegroundColor Red
            $Script:FAILED = $true
        }
    }
}

# ============================================
# 3. Kotlin 语法检查 (基础检查)
# ============================================
Write-Host ""
Write-Host "[3/5] 检查 Kotlin 文件语法..." -ForegroundColor Yellow

$ktFiles = Get-ChildItem -Path (Join-Path $ProjectRoot "app/src/main/java") -Recurse -Include "*.kt" -ErrorAction SilentlyContinue

if ($ktFiles) {
    # 基础语法检查：括号匹配、常见语法错误
    $syntaxErrors = 0
    
    foreach ($file in $ktFiles) {
        $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($content) {
            $relativePath = $file.FullName.Replace($ProjectRoot, "").TrimStart("\", "/")
            
            # 检查括号匹配
            $openBrace = ($content | Select-String -Pattern "{" -AllMatches).Matches.Count
            $closeBrace = ($content | Select-String -Pattern "}" -AllMatches).Matches.Count
            if ($openBrace -ne $closeBrace) {
                Write-Host "  [WARN] Brace mismatch in: $relativePath (open: $openBrace, close: $closeBrace)" -ForegroundColor Yellow
            }
            
            $openParen = ($content | Select-String -Pattern "\(" -AllMatches).Matches.Count
            $closeParen = ($content | Select-String -Pattern "\)" -AllMatches).Matches.Count
            if ($openParen -ne $closeParen) {
                Write-Host "  [WARN] Parenthesis mismatch in: $relativePath (open: $openParen, close: $closeParen)" -ForegroundColor Yellow
            }
            
            # 检查常见语法错误
            if ($content -match "^\s*import\s+\.$") {
                Write-Host "  [FAIL] Invalid import in: $relativePath" -ForegroundColor Red
                $Script:FAILED = $true
                $syntaxErrors++
            }
            
            # 检查未闭合的字符串
            $singleQuote = ($content | Select-String -Pattern "'" -AllMatches).Matches.Count
            $doubleQuote = ($content | Select-String -Pattern '"' -AllMatches).Matches.Count
            if (($singleQuote % 2) -ne 0 -or ($doubleQuote % 2) -ne 0) {
                Write-Host "  [WARN] Unmatched quotes in: $relativePath" -ForegroundColor Yellow
            }
        }
    }
    
    if ($syntaxErrors -eq 0) {
        Write-Host "  [OK] Kotlin syntax check passed" -ForegroundColor Green
    }
} else {
    Write-Host "  [WARN] No Kotlin files found" -ForegroundColor Yellow
}

# ============================================
# 4. Gradle 配置检查
# ============================================
Write-Host ""
Write-Host "[4/5] 检查 Gradle 配置..." -ForegroundColor Yellow

# 检查 settings.gradle.kts 语法
$settingsFile = Join-Path $ProjectRoot "settings.gradle.kts"
if (Test-Path $settingsFile) {
    $content = Get-Content $settingsFile -Raw -ErrorAction SilentlyContinue
    if ($content) {
        # 检查基本的 Kotlin DSL 语法
        $hasRootProject = $content -match "rootProject\.name\s*="
        $hasInclude = $content -match "include\s*\("
        
        if ($hasRootProject -and $hasInclude) {
            Write-Host "  [OK] settings.gradle.kts 结构正确" -ForegroundColor Green
        } else {
            Write-Host "  [FAIL] settings.gradle.kts 缺少必要配置" -ForegroundColor Red
            $Script:FAILED = $true
        }
    }
}

# 检查 build.gradle.kts
$appBuildFile = Join-Path $ProjectRoot "app/build.gradle.kts"
if (Test-Path $appBuildFile) {
    $content = Get-Content $appBuildFile -Raw -ErrorAction SilentlyContinue
    if ($content) {
        $hasAndroid = $content -match "android\s*\{"
        $hasDependencies = $content -match "dependencies\s*\{"
        
        if ($hasAndroid -and $hasDependencies) {
            Write-Host "  [OK] app/build.gradle.kts 结构正确" -ForegroundColor Green
        } else {
            Write-Host "  [FAIL] app/build.gradle.kts 缺少必要配置" -ForegroundColor Red
            $Script:FAILED = $true
        }
    }
}

# ============================================
# 5. GitHub Actions Workflow 检查
# ============================================
Write-Host ""
Write-Host "[5/5] 检查 GitHub Actions Workflow..." -ForegroundColor Yellow

$workflowDir = Join-Path $ProjectRoot ".github/workflows"
if (Test-Path $workflowDir) {
    $workflows = Get-ChildItem -Path $workflowDir -Filter "*.yml" -ErrorAction SilentlyContinue
    
    foreach ($workflow in $workflows) {
        try {
            $content = Get-Content $workflow.FullName -Raw -ErrorAction Stop
            # 基础 YAML 语法检查
            if ($content -match "name:" -and $content -match "on:") {
                Write-Host "  [OK] $($workflow.Name)" -ForegroundColor Green
            } else {
                Write-Host "  [FAIL] $($workflow.Name) 缺少必要字段" -ForegroundColor Red
                $Script:FAILED = $true
            }
        } catch {
            Write-Host "  [FAIL] $($workflow.Name) YAML 解析失败" -ForegroundColor Red
            $Script:FAILED = $true
        }
    }
}

# ============================================
# 总结
# ============================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

if ($Script:FAILED) {
    Write-Host "  Preflight Check: FAILED" -ForegroundColor Red
    Write-Host "  请修复上述错误后重试" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Cyan
    exit 1
} else {
    Write-Host "  Preflight Check: PASSED" -ForegroundColor Green
    Write-Host "  所有检查项通过" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
    exit 0
}
