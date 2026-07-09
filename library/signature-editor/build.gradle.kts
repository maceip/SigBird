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
    implementation(projects.legacy.core)

    testImplementation(libs.assertk)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

codeCoverage {
    branchCoverage = 4
    lineCoverage = 6
}
