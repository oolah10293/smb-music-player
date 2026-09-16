plugins {
    id("com.android.application")
}

android {
    namespace = "com.smbmusic.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smbmusic.player"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "0.3.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")

    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.slf4j:slf4j-nop:1.7.36")
}
