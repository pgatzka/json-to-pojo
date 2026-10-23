plugins {
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "pgatzka_json-to-pojo")
        property("sonar.organization", "pgatzka")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
