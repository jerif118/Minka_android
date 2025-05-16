plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}


android {
    namespace = "com.minka.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.minka.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Excluir manifiestos duplicados de OSGI en versiones Java 9+
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("io.coil-kt:coil-compose:1.3.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("com.google.android.gms:play-services-ads:23.1.0")
    implementation("com.google.firebase:firebase-ads:21.5.0")
    implementation ("com.journeyapps:zxing-android-embedded:4.3.0")
    //Material 3
    implementation (libs.material3)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.identity.jvm)
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)
    // Abril-25
    implementation (libs.androidx.compose.material3.material3)          // 1.3.2 estable
    implementation (libs.androidx.material3.window.size.class1)
    implementation (libs.androidx.material3.adaptive.navigation.suite)
    implementation (libs.androidx.runtime.livedata)
    //icons
    implementation (libs.androidx.material.icons.extended)
    //implementation ("androidx.compose.material3:material3:1.0.0-alpha01")
    // Eliminado com.android.support:support-v4
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    //cam

    //security
    implementation (libs.androidx.security.crypto)
    //websocket
    implementation ("com.squareup.okhttp3:okhttp:4.10.0")
    implementation ("com.squareup.okio:okio:3.3.0")


}
