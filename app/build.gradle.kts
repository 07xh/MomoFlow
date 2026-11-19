plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "io.github.personal.momoflow"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.personal.momoflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.7.0"
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildFeatures { compose = true }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
