plugins { id("com.android.application") }
android {
    namespace = "io.github.personal.momoflow"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.personal.momoflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.5.4"
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
