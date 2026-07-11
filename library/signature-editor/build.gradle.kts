plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "app.k9mail.library.signatureeditor"
    resourcePrefix = "signature_editor_"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.library.htmlCleaner)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    // Ed25519 holder PoP for private-identity presentations (tamayo-compatible).
    implementation("net.i2p.crypto:eddsa:0.3.0")

    testImplementation(libs.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
}

codeCoverage {
    branchCoverage = 4
    lineCoverage = 6
}
