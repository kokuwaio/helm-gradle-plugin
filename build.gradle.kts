group = "com.kiwigrid"
version = "1.4.0"

plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish").version("0.12.0")
}
repositories {
    mavenCentral()
}

dependencies {
    implementation("de.undercouch:gradle-download-task:4.0.2")
    implementation("com.jayway.jsonpath:json-path:2.4.0")
    implementation("org.yaml:snakeyaml:1.20")
    implementation("commons-io:commons-io:2.6")
}
gradlePlugin {
    plugins {
        create("helmPlugin") {
            id = "com.kiwigrid.helm"
            implementationClass = "com.kiwigrid.k8s.helm.HelmPlugin"
        }
    }
}
pluginBundle {
    website = "https://github.com/kiwigrid/helm-gradle-plugin"
    vcsUrl = "https://github.com/kiwigrid/helm-gradle-plugin"
    description = """
                Gradle Plugin to integrate HELM Chart development in a gradle project with
                 strong focus on build and test.
    """.trimIndent()
    (plugins) {
        "helmPlugin" {
            displayName = "Gradle Helm Plugin"
            description = """
                Gradle Plugin to integrate HELM Chart development in a gradle project with
                 strong focus on build and test.
            """.trimIndent()
            tags = setOf("helm", "kubernetes")
            version = project.version.toString()
        }
    }
}