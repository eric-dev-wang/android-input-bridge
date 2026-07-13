// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

private data class BridgeVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val isSnapshot: Boolean,
) {
  val displayName: String
    get() = if (isSnapshot) "0.0.0-SNAPSHOT" else "$major.$minor.$patch"

  val versionCode: Int
    get() = if (isSnapshot) 1 else major * 1_000_000 + minor * 1_000 + patch
}

private fun parseBridgeVersion(rawVersion: String): BridgeVersion {
  if (rawVersion == "0.0.0-SNAPSHOT") {
    return BridgeVersion(major = 0, minor = 0, patch = 0, isSnapshot = true)
  }

  val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(rawVersion)
    ?: error("bridgeVersion must be SemVer major.minor.patch or 0.0.0-SNAPSHOT: $rawVersion")
  val (major, minor, patch) = match.destructured
  return BridgeVersion(
    major = major.toIntOrNull() ?: error("bridgeVersion major is out of range: $rawVersion"),
    minor = minor.toIntOrNull() ?: error("bridgeVersion minor is out of range: $rawVersion"),
    patch = patch.toIntOrNull() ?: error("bridgeVersion patch is out of range: $rawVersion"),
    isSnapshot = false,
  )
}

private val bridgeVersion = parseBridgeVersion(
  providers.gradleProperty("bridgeVersion").orElse("0.0.0-SNAPSHOT").get(),
)

allprojects {
  version = bridgeVersion.displayName
}

extra["bridgeVersionCode"] = bridgeVersion.versionCode
