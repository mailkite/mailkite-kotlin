// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Android library module for `dev.mailkite:mailkite-client` — the client-side
// MailKite SDK. A user signs into their own MailKite account via OAuth 2.1 +
// PKCE (Chrome Custom Tabs) and the resulting short-lived token drives the same
// API methods the server SDKs expose.
//
// The pure-JVM core (transport, OAuth/PKCE math, webhook verify, JSON) carries
// no Android imports, so the unit tests in src/test run on a plain JVM with no
// emulator — see scripts/run-jvm-tests.sh.
//
// Published to Maven Central as dev.mailkite:mailkite-client via the vanniktech
// plugin (Central Portal + GPG signing). publish-kotlin.yml runs
// `./gradlew publishToMavenCentral`. Docs: docs/architecture/sdk-public-repos.md

import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library") version "8.5.2"
    kotlin("android") version "2.0.20"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "dev.mailkite"
version = "0.1.0"

android {
    namespace = "dev.mailkite.client"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")

        // The redirect Activity's intent-filter scheme. The app sets this to the
        // scheme matching its OAuth client id, e.g.:
        //   manifestPlaceholders["mailkiteRedirectScheme"] = "mailkite-cli_abc123"
        // A placeholder default keeps a fresh app buildable before it's set.
        manifestPlaceholders["mailkiteRedirectScheme"] = "mailkite-client"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Chrome Custom Tabs for the OAuth authorize step.
    implementation("androidx.browser:browser:1.8.0")
    // EncryptedSharedPreferences-backed token store.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<Test> { useJUnitPlatform() }

// Maven Central publishing (Central Portal + GPG signing). Credentials come from
// the workflow as ORG_GRADLE_PROJECT_mavenCentralUsername / ...Password and
// ORG_GRADLE_PROJECT_signingInMemoryKey / ...signingInMemoryKeyPassword.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("dev.mailkite", "mailkite-client", version.toString())
    pom {
        name.set("MailKite Client")
        description.set("Client-side MailKite SDK — OAuth 2.1 + PKCE login on Android.")
        url.set("https://github.com/mailkite/mailkite-kotlin")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("mailkite")
                name.set("MailKite")
                url.set("https://mailkite.dev")
            }
        }
        scm {
            url.set("https://github.com/mailkite/mailkite-kotlin")
            connection.set("scm:git:https://github.com/mailkite/mailkite-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/mailkite/mailkite-kotlin.git")
        }
    }
}
