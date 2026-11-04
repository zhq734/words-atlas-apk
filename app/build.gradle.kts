plugins {
    id("com.android.application")
}

android {
    namespace = "tech.lei.wordatlas"
    compileSdk = 35

    defaultConfig {
        applicationId = "tech.lei.wordatlas"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "2.0.7"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf("META-INF/*.kotlin_module")
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
