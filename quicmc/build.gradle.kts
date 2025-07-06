val libraries by configurations.getting

plugins {
  id("com.gradleup.shadow") version "8.3.8"
}

dependencies {
  implementation(project(":netmix"))

  val nettyQuicVersion = "0.0.72.Final"
  libraries("io.netty.incubator:netty-incubator-codec-classes-quic:$nettyQuicVersion")
  libraries("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:linux-x86_64")
  libraries("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:linux-aarch_64")
  libraries("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:osx-x86_64")
  libraries("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:osx-aarch_64")
  libraries("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:windows-x86_64")
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
  dependencies {
    exclude(dependency("^(?!io.netty.incubator).*:.*:.*"))
  }
}
