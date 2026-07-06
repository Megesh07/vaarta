plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:reasoning"))
    implementation(project(":core:complaint"))
}

application {
    mainClass.set("ai.vaarta.tools.demo.DemoKt")
}
