@file:Suppress("UnstableApiUsage")

rootProject.name = "nose-store"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val uiLibRoot = file("vendor/miuix")
if (!uiLibRoot.resolve("settings.gradle.kts").exists()) {
    throw GradleException(
        "Falta el motor de interfaz en './vendor/miuix'. " +
            "Ejecuta 'iniciar.bat' (o './iniciar.sh') para inicializarlo automaticamente.",
    )
}

includeBuild(uiLibRoot) {
    dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-core")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-ui")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-material")).using(project(":miuix-material"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass")).using(project(":miuix-glass"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-icons")).using(project(":miuix-glass-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-squircle")).using(project(":miuix-squircle"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-nav")).using(project(":miuix-nav"))
    }
}

include(":webApp")
