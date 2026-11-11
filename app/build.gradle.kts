plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.musicsquare.player"
 compileSdk = 35
 defaultConfig { applicationId = "com.musicsquare.player"; minSdk = 24; targetSdk = 34; versionCode = 2; versionName = "0.2.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-session:1.5.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("com.google.guava:guava:33.3.1-android")
 implementation("com.mpatric:mp3agic:0.9.1")
 implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.9")
 testImplementation("junit:junit:4.13.2")
}
