plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
    `maven-publish`
    signing
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "fi.aprs.fap"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "kotlin-aprs-fap"
            from(components["java"])
            pom {
                name.set("kotlin-aprs-fap")
                description.set("Finnish APRS Parser (FAP) - Kotlin/JVM edition, for Android and Kotlin applications")
                url.set("https://github.com/hessu/kotlin-aprs-fap")
                licenses {
                    license {
                        name.set("Perl Artistic License")
                        url.set("https://opensource.org/licenses/Artistic-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("oh2kku")
                        name.set("Tapio Sokura")
                    }
                    developer {
                        id.set("oh7lzb")
                        name.set("Heikki Hannikainen")
                    }
                }
                scm {
                    url.set("https://github.com/hessu/kotlin-aprs-fap")
                    connection.set("scm:git:https://github.com/hessu/kotlin-aprs-fap.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "OSSRH"
            url =
                uri(
                    if (version.toString().endsWith("SNAPSHOT")) {
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    } else {
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                    },
                )
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: ""
                password = project.findProperty("ossrhPassword") as String? ?: ""
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
