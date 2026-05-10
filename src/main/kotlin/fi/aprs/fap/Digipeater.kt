package fi.aprs.fap

/** A digipeater entry in the APRS packet path. */
data class Digipeater(
    val call: String,
    val wasDigied: Boolean,
)
