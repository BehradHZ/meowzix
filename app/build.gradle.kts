plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.room")
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val telegramApiId = providers.gradleProperty("MEOWZIX_TELEGRAM_API_ID")
    .orElse(providers.environmentVariable("MEOWZIX_TELEGRAM_API_ID"))
    .getOrElse("")
val telegramApiHash = providers.gradleProperty("MEOWZIX_TELEGRAM_API_HASH")
    .orElse(providers.environmentVariable("MEOWZIX_TELEGRAM_API_HASH"))
    .getOrElse("")

android {
    namespace = "dev.behradhz.meowzix"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.behradhz.meowzix"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TELEGRAM_API_ID", telegramApiId.asBuildConfigString())
        buildConfigField("String", "TELEGRAM_API_HASH", telegramApiHash.asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    val serializationBom = platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1")
    implementation(composeBom)
    implementation(serializationBom)
    androidTestImplementation(composeBom)
    androidTestImplementation(serializationBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Stable Haze v1 keeps real backdrop blur while allowing an Android 16 / API 36 baseline.
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")

    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("io.github.tdlib-android:core:0.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
