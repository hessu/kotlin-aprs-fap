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
 * @property allowThirdParty Decode APRS "third party" packets (body type `}`),
 *   unwrapping the encapsulated original frame into the main fields and the
 *   relaying frame into the `outer*` fields. Third-party encapsulation is only
 *   valid on RF, so this defaults to false; the AX.25/RF decode path
 *   ([parseAx25]) enables it automatically. When false, a `}` body is treated
 *   as an ordinary (non-third-party) packet.
 */
data class ParseOptions(
    val isAX25: Boolean = false,
    val acceptBrokenMicE: Boolean = false,
    val rawTimestamp: Boolean = false,
    val allowThirdParty: Boolean = false,
)
