# PAF - Pixiv Advanced Filter Android App

一个运行在 Android 手机上的 Pixiv 搜索结果收集工具。

## 功能特性

- 🔍 **关键词搜索** - 输入任意关键词搜索 Pixiv 作品
- 📊 **最少图片数过滤** - 只收集指定页数以上的作品
- 📑 **页数范围控制** - 指定起始页和终止页
- 🤖 **AI作品过滤** - 可选排除AI生成作品
- 🔞 **R18过滤** - 不过滤/排除R18/仅R18/仅R18G
- ⏹️ **自动停止** - 达到目标数或终止页自动停止
- 📱 **离线查看** - 结果保存在本地

## 技术栈

- Kotlin 1.9.20
- Jetpack Compose (Material 3)
- OkHttp 4.12.0
- Room Database
- Coil 图片加载

## 构建 APK

### 方式一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio，选择 `File → Open`
3. 选择 `E:\PixivAdvancedApp` 文件夹
4. 等待 Gradle 同步完成
5. 点击 `Build → Build Bundle(s) / APK(s) → Build APK(s)`
6. APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`

### 方式二：命令行

确保已安装：
- JDK 17+
- Android SDK (API 34)

```bash
# 设置环境变量
export ANDROID_HOME=/path/to/android/sdk
export JAVA_HOME=/path/to/jdk17

# 进入项目目录
cd E:\PixivAdvancedApp

# 构建 Debug APK
./gradlew assembleDebug

# APK 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 方式三：GitHub Actions（自动构建）

1. 将代码推送到 GitHub 仓库
2. GitHub Actions 会自动构建
3. 从 Actions artifacts 下载 APK

## 使用方法

1. **安装 APK** - 将 `app-debug.apk` 安装到手机
2. **登录 Pixiv** - 打开 App，点击登录按钮，使用 WebView 登录
3. **设置参数** - 输入关键词、最少图片数、起始/终止页
4. **开始收集** - 点击开始按钮
5. **查看结果** - 收集过程中实时显示结果

## 接口说明

基于 Pixiv Ajax API:

- 搜索: `https://www.pixiv.net/ajax/search/artworks/{keyword}?p={page}`
- 详情: `https://www.pixiv.net/ajax/works/{id}`

响应中已包含 `pageCount` 字段，无需额外请求。

## 项目结构

```
app/
├── src/main/
│   ├── java/com/paf/app/
│   │   ├── MainActivity.kt
│   │   ├── data/
│   │   │   ├── api/PixivApiService.kt   # API 服务
│   │   │   ├── database/               # Room 数据库
│   │   │   └── model/                  # 数据模型
│   │   ├── domain/
│   │   │   └── CollectorEngine.kt      # 收集器引擎
│   │   └── ui/
│   │       ├── theme/                   # 主题
│   │       └── screens/                 # 界面
│   └── res/                             # 资源文件
├── build.gradle.kts                     # App 构建配置
└── proguard-rules.pro
```

## 注意事项

- 需要 Pixiv 账号登录才能获取完整搜索结果
- 部分 R18 内容需要登录后才能访问
- App 运行期间请保持网络连接

## 许可证

仅供个人自用，请勿传播或用于商业目的。
