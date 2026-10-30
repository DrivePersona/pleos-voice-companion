pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val githubPackagesUser = providers.gradleProperty("gpr.user")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        .orNull
    val githubPackagesToken = providers.gradleProperty("gpr.key")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
        .orNull

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Team workstations can opt into locally published private SDKs without
        // changing the public repository's dependency source.
        if (providers.gradleProperty("drivepersona.useMavenLocal").orNull == "true") {
            mavenLocal()
        }

        // Private DrivePersona packages. Credentials are never committed: use
        // gpr.user/gpr.key Gradle properties or GITHUB_ACTOR/GITHUB_TOKEN in CI.
        listOf("voice-pack-sdk", "character-pack-sdk").forEach { repository ->
            maven("https://maven.pkg.github.com/DrivePersona/$repository") {
                if (!githubPackagesUser.isNullOrBlank() && !githubPackagesToken.isNullOrBlank()) {
                    credentials {
                        username = githubPackagesUser
                        password = githubPackagesToken
                    }
                }
            }
        }

        // Pleos SDK. This repository is limited to the Pleos group.
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroupAndSubgroups("ai.pleos.playground") }
        }
    }
}

rootProject.name = "pleos-voice-companion"
include(":app")
