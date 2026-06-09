plugins {
    java
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.4.2"
}

group = "dev.nighter"
version = "1.0.9"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype-public"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    @Suppress("GradleDependency")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT")
    compileOnly("com.github.GriefPrevention:GriefPrevention:17.0.0")
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.jar {
    archiveBaseName.set("CelestCombatJar")
    archiveVersion.set(version.toString())

    from(sourceSets["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

tasks.shadowJar {
    archiveBaseName.set("CelestCombat")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    from(sourceSets["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
