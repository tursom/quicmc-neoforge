val libraries by configurations.getting

plugins {
  id("com.gradleup.shadow") version "8.3.8"
}

dependencies {
  implementation(project(":netmix"))

  val nettyQuicVersion = "0.0.72.Final"
  libraries("io.netty.incubator:netty-incubator-codec-classes-quic:$nettyQuicVersion")
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
  dependencies {
    exclude(dependency("^(?!io.netty.incubator).*:.*:.*"))
  }

  //finalizedBy("reobfShadowJar")
}

//reobf {
//  create("shadowJar")
//}
