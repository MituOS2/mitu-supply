import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("top.yukonga.miuix.kmp:miuix-ui")
            implementation("top.yukonga.miuix.kmp:miuix-preference")
            implementation("top.yukonga.miuix.kmp:miuix-material")
            implementation("top.yukonga.miuix.kmp:miuix-blur")
            implementation("top.yukonga.miuix.kmp:miuix-glass")
            implementation("top.yukonga.miuix.kmp:miuix-glass-icons")
            implementation("top.yukonga.miuix.kmp:miuix-squircle")
            implementation("top.yukonga.miuix.kmp:miuix-icons")
            implementation("top.yukonga.miuix.kmp:miuix-nav")
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.androidx.navigationevent)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }

        val webMain =
            create("webMain") {
                dependsOn(commonMain.get())
            }

        wasmJsMain {
            dependsOn(webMain)
        }
    }
}
