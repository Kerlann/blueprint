plugins {
    id("fabric-loom")
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))
}
