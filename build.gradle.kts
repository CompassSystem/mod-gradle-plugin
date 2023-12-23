import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("jvm") version "1.9.22"
}

group = "compass_system"
version = "1.0.0"

repositories {
    maven {
        name = "Architectury Maven"
        url = uri("https://maven.architectury.dev")
    }
    gradlePluginPortal()
    mavenCentral()
    maven {
        name = "MinecraftForge Maven"
        url = uri("https://maven.minecraftforge.net/")
    }
    maven {
        name = "FabricMC Maven"
        url = uri("https://maven.fabricmc.net")
    }
}

dependencies {
    implementation("dev.architectury:architectury-loom:1.4.373")
    implementation("com.modrinth.minotaur:Minotaur:2.7.5") {
        exclude(group = "com.squareup.okio", module = "okio-jvm")
    }
    implementation("com.squareup.okio:okio-jvm:3.7.0") {
        because("Minotaur pulls an outdated version of Okio that is vulnerable")
    }
    implementation("org.jetbrains:annotations:24.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = sourceCompatibility
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = java.sourceCompatibility.majorVersion
    }
}

gradlePlugin {
    plugins {
        create("mod-gradle-plugin") {
            id = "mod-gradle-plugin"
            implementationClass = "compass_system.mod_gradle_plugin.ModPlugin"
        }
    }
}
