plugins {
    id("fabric-loom") version "1.6-SNAPSHOT"
    id("maven-publish")
}

base {
    archivesName = project.property("archives_base_name").toString()
    version = project.property("mod_version").toString()
    group = project.property("maven_group").toString()
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${project.base.archivesName.get()}"}
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }

    javadoc {
        // MultiVersion has a ton of constants without javadocs, this just shuts up the linter
        (options as CoreJavadocOptions).addBooleanOption("Xdoclint:none", true)
    }

    java {
        withSourcesJar()
        withJavadocJar()

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }

    repositories {
        maven("https://maven.meteordev.org/releases") {
            name = "meteor-maven"

            credentials {
                username = System.getenv("MAVEN_METEOR_ALIAS")
                password = System.getenv("MAVEN_METEOR_TOKEN")
            }

            authentication {
                create("basic", BasicAuthentication::class)
            }
        }
    }
}