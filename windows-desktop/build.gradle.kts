import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

group = "com.cutterpro"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("com.fazecast:jSerialComm:2.11.0")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.cutterpro.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "CutterPro"
            packageVersion = "1.0.0"
            description = "CutterPro Vector CAD & Plotter Controller"
            vendor = "Jamil Alsayed Ahmed"
            modules("java.desktop", "java.logging", "java.xml", "jdk.crypto.ec", "jdk.charsets")

            windows {
                menuGroup = "CutterPro"
                shortcut = true
                dirChooser = true
                upgradeUuid = "71f65772-f8af-4675-9f65-583d85dcd5bf"
            }
        }
    }
}
