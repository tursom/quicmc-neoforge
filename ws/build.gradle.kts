val libraries by configurations.getting

jarJar.enable()

dependencies {
  implementation(project(":netmix"))

  val nettyVersion = "4.1.97.Final"
  libraries("io.netty", "netty-codec-http", nettyVersion) {
    exclude("io.netty")
  }
  jarJar("io.netty", "netty-codec-http", "[$nettyVersion,$nettyVersion]")
}