package no.nordicsemi.android.observability.internal

/**
 * This class parses the Authorization Header read from a characteristic.
 */
internal object AuthorisationHeader {

    /**
     * Parses the Authorisation Header from a byte array.
     *
     * The expected format is "Memfault-Project-Id:<auth_token>".
     */
    fun parse(bytes: ByteArray): String {
        val components = String(bytes).split(":")
        require(components.size == 2) { "Invalid Authorisation Header format" }
        return components[1]
    }

}

/**
 * Prints the key in the form of "xxxx...xxxx".
 */
internal fun String.shortened() = if (length <= 8) {
    this
} else {
    "${take(4)}...${takeLast(4)}"
}