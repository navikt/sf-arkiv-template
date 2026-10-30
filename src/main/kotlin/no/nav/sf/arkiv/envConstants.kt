@file:Suppress("ktlint:standard:filename", "ktlint:standard:property-naming")

package no.nav.sf.arkiv

/**
 * Naming convention applied to environment variable constants: a lowercase prefix separated from the actual constant, i.e. prefix_ENVIRONMENT_VARIABLE_NAME.
 *
 * Motivation:
 * The prefix provides contextual naming that describes the source and nature of the variables they represent while keeping the names short.
 * A prefix marks a constant representing an environment variable with where one can find the value of that variable
 *
 * - env: Denotes an environment variable typically injected into the pod by the Nais platform.
 *
 * - config: Denotes an environment variable explicitly configured in YAML files (see dev.yaml, prod.yaml) or via Nais console configs
 *
 * - secret: Denotes an environment variable loaded from a Kubernetes secret.
 */
const val config_DEV_TEST_BYPASS_ALLOWED = "DEV_TEST_BYPASS_ALLOWED"
const val config_HENTE_ALLOWED = "HENTE_ALLOWED"
const val config_DB_INIT_ALLOWED = "DB_INIT_ALLOWED"
const val config_DUMMY_READ_TO_TEST_READINESS = "DUMMY_READ_TO_TEST_READINESS"

/**
 * Shortcuts for fetching environment variables
 */
fun env(name: String): String = System.getenv(name) ?: throw NullPointerException("Missing env $name")
