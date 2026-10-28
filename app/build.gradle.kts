plugins { id("com.android.application") }
android {
 namespace = "com.grip.bombtimer"
 compileSdk = 35
 defaultConfig { applicationId = "com.grip.bombtimer"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("com.google.mlkit:text-recognition:16.0.1") }
