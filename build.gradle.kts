// Root build — VAARTA. Modules declare their own plugins via the version catalog.
// All plugins used by any subproject are declared here (apply false) so Gradle resolves one
// consistent version per plugin across the whole build instead of each module resolving its own.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
