plugins {
  kotlin("jvm") version "1.9.20"
  `java-gradle-plugin`
}

group = "cn.tursom"
version = "1.1-SNAPSHOT"

repositories {
  maven("https://maven.neoforged.net/releases")
  mavenCentral()
}

dependencies {
  compileOnly(kotlin("gradle-plugin"))
  compileOnly("net.neoforged.gradle:dsl-common:7.0.189")
}

gradlePlugin {
  plugins {
    create("neoforge-gradle-kts") {
      id = "neoforge-gradle-kts"
      implementationClass = "cn.tursom.gradle.NeoForgeGradleKts"
    }
  }
}
