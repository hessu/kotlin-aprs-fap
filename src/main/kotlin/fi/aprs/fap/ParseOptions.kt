package fi.aprs.fap

/**
 * Options that control parsing behaviour.
 *
 * @property isAX25 Validate callsigns against strict AX.25 rules (base 1–6
 *   alphanumeric characters, numeric SSID 0–15). When false (default),
 *   APRS-IS relaxed rules are used.
 * @property acceptBrokenMicE Attempt to repair Mic-E packets where a
 *   non-printable byte was replaced by a space and adjacent spaces were
 *   collapsed, dropping a byte from the info field.
 * @property rawTimestamp Return timestamps as a raw 6-character string in
 *   [Packet.rawTimestamp] instead of a parsed [java.time.Instant] in
 *   [Packet.timestamp].
 */
data class ParseOptions(
    val isAX25: Boolean = false,
    val acceptBrokenMicE: Boolean = false,
    val rawTimestamp: Boolean = false,
)
