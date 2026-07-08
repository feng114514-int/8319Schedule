plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.a8319schedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.a8319schedule"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    // 构建变体：standard=标准安卓小部件(默认), miui=尝试小米小部件模式
    flavorDimensions += "widgetMode"
    productFlavors {
        create("standard") {
            dimension = "widgetMode"
            // 不声明miuiWidget，小部件显示在"安卓小部件"区域，保证兼容
            manifestPlaceholders["miuiWidgetEnabled"] = "false"
        }
        create("miui") {
            dimension = "widgetMode"
            // 声明miuiWidget=true，尝试让小部件出现在"小米小部件"区域
            manifestPlaceholders["miuiWidgetEnabled"] = "true"
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true  // 启用R8代码压缩和优化
            isShrinkResources = true  // 启用资源压缩
            
            // 启用资源优化
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_DEV_TOOLS_OPTION_IN_UI", "false")
            buildConfigField("boolean", "ENABLE_ADDRESS_BAR_TOGGLE_BUTTON", "true")
        }
        debug {
            buildConfigField("boolean", "ENABLE_DEV_TOOLS_OPTION_IN_UI", "true")
            buildConfigField("boolean", "ENABLE_ADDRESS_BAR_TOGGLE_BUTTON", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.serialization.InternalSerializationApi",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    // 优化资源打包
    androidResources {
        // 禁用自动每应用语言支持以避免resources.properties错误
        generateLocaleConfig = false
    }
}

dependencies {
    // Miuix - 小米 HyperOS 风格组件库
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // 课程表应用新增依赖
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    // HTML解析（教务课表导入）
    implementation(libs.jsoup)
    // OkHttp（用于HTTP请求）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    // DataStore（用于保存设置）
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // App Widget（小部件支持）
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // ProcessLifecycleOwner（应用生命周期监听）
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    
    // Gson（JSON解析）
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Kotlin序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    // 安全加密存储（用于API Key等敏感信息）
    implementation(libs.security.crypto)
    

}