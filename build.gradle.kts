plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.5.2"
}

group = "com.hgy.plugin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2021.2")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf(/* Plugin Dependencies */))
}

dependencies {
    implementation("com.fifesoft:rsyntaxtextarea:3.3.4")
    implementation("com.sun.codemodel:codemodel:2.6")
}

tasks {
//    compileJava {
//        options.compilerArgs = listOf("-XDignore.symbol.file")
////        options.fork = true // may not needed on 1.8
//        options.forkOptions.executable = "javac" // may not needed on 1.8
//    }
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    patchPluginXml {
        sinceBuild.set("212")
        untilBuild.set("222.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
