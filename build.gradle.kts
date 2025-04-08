import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("java")
  id("maven-publish")
}

group = "io.quagmire"
version = "1.0.0"

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
  mavenCentral()
  maven {
    name = "papermc"
    url = uri("https://repo.papermc.io/repository/maven-public")
  }
  maven {
    name = "codemc"
    url = uri("https://repo.codemc.io/repository/maven-public")
  }
  maven {
    name = "papi"
    url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
  }
  maven {
    name = "artifactory"
    url = uri(System.getenv("ARTIFACTORY_URL"))
    credentials {
      username = System.getenv("ARTIFACTORY_USER")
      password = System.getenv("ARTIFACTORY_PASS")
    }
  }
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

  compileOnly("io.quagmire:Core:2.2.63")


  compileOnly("org.projectlombok:lombok:1.18.30")
  annotationProcessor("org.projectlombok:lombok:1.18.30")
}

val copyJar by tasks.registering(Copy::class) {
  from(tasks.named("jar"))
  into("$rootDir/debug/plugins")
}

val watch by tasks.registering {
  inputs.files("src")
  dependsOn(copyJar)
}

tasks.compileJava {
  options.encoding = "UTF-8"
}

tasks.processResources {
  val fullVersion = project.version as String
  inputs.property("fullVersion", fullVersion)
  filter<ReplaceTokens>(
    "beginToken" to "\${",
    "endToken" to "}",
    "tokens" to mapOf(
      "full.version" to fullVersion
    )
  )
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
    }
  }
  repositories {
    maven {
      credentials {
        username = System.getenv("ARTIFACTORY_USER")
        password = System.getenv("ARTIFACTORY_PASS")
      }
      url = uri(System.getenv("ARTIFACTORY_URL"))
    }
  }
}