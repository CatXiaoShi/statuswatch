import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 解析签名密码，优先级：环境变量 > 密码文件 > 兜底值。
 *
 * sign/LewoWoff.key 在本机实际是「证书 + 私钥」的二进制混合文件，开头含非 ASCII 字节，
 * 并非纯文本密码。因此只在内容全为可打印 ASCII 时才当作密码使用，
 * 否则跳过，避免把二进制乱码当成密码导致 keystore 校验失败。
 */
fun resolveKeyPassword(projectDir: File): String {
    val env = System.getenv("WATCH_KEY_PASSWORD")
    if (!env.isNullOrBlank()) return env

    val keyFile = File(projectDir, "sign/LewoWoff.key")
    if (keyFile.canRead()) {
        val raw = runCatching { keyFile.readText() }.getOrNull()
        if (raw != null) {
            val text = raw.trim()
            val isPlainText = text.all { it.code in 32..126 || it == '\n' || it == '\r' }
            if (isPlainText && !text.startsWith("-----BEGIN")) {
                val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
                val value = if (line.contains("=")) line.substringAfter("=").trim() else line
                if (value.isNotEmpty() && value.all { it.code in 32..126 }) return value
            }
        }
    }
    return "MLFKWMC1337"
}

val watchKeyPassword = resolveKeyPassword(rootProject.projectDir)
val watchStoreFile = rootProject.file("sign/LewoWoff.jks")

android {
    namespace = "luolang.status"
    compileSdk = 35

    defaultConfig {
        applicationId = "luolang.status"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // 仓库不包含私钥；本地存在 sign/LewoWoff.jks 时才启用自定义签名，
        // 否则回退到 Android 默认的 debug 签名，保证 clone 后可直接构建。
        if (watchStoreFile.exists()) {
            create("watch") {
                storeFile = watchStoreFile
                storePassword = watchKeyPassword
                keyAlias = "lewowoff"
                keyPassword = watchKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("watch")
        }
        release {
            // R8 压缩 + 资源裁剪，显著减小 APK 体积
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("watch")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Compose 编译器版本由 org.jetbrains.kotlin.plugin.compose 插件决定，
        // 与 Kotlin 2.0.21 绑定，此处不再手动指定 kotlinCompilerExtensionVersion。
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
