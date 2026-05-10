package fi.aprs.fap

/**
 * Thrown on hard parse failures. [code] is a machine-readable error code
 * (e.g. "pos_short") matching the Go sentinel error codes. Use the constants
 * on [ParseError] for reliable comparisons.
 *
 * [packet] carries the partially-parsed packet at the point of failure, so
 * callers can inspect fields that were successfully decoded before the error.
 */
class ParseException(
    val code: String,
    message: String,
    val packet: Packet? = null,
) : Exception(message)

/** Non-fatal parsing issue collected in [Packet.warnings]. */
data class ParseWarning(val code: String, val message: String)

/** Machine-readable error code constants. Values match the Go/Perl FAP error codes. */
object ParseError {
    // Header / general
    const val PACKET_NO_BODY     = "packet_no_body"
    const val PACKET_SHORT       = "packet_short"
    const val SRCCALL_NOGT       = "srccall_nogt"
    const val SRCCALL_EMPTY      = "srccall_empty"
    const val SRCCALL_BADCHARS   = "srccall_badchars"
    const val SRCCALL_NOAX25     = "srccall_noax25"
    const val DSTCALL_EMPTY      = "dstcall_empty"
    const val DSTCALL_NOAX25     = "dstcall_noax25"
    const val DSTPATH_TOOMANY    = "dstpath_toomany"
    const val DIGI_EMPTY         = "digi_empty"
    const val DIGICALL_BADCHARS  = "digicall_badchars"
    const val DIGICALL_NOAX25    = "digicall_noax25"
    const val NO_BODY            = "no_body"
    const val TYPE_NOT_SUPPORTED = "type_not_supported"
    const val EXP_UNSUPP         = "exp_unsupp"

    // Position
    const val LOC_AMB_INV  = "loc_amb_inv"
    const val POS_SHORT    = "pos_short"
    const val POS_INV      = "pos_inv"
    const val POS_LAT_INV  = "pos_lat_inv"
    const val POS_LON_INV  = "pos_lon_inv"
    const val LOC_INV      = "loc_inv"
    const val LOC_LARGE    = "loc_large"

    // Symbol
    const val SYM_INV_TABLE = "sym_inv_table"

    // Compressed position
    const val COMP_SHORT = "comp_short"
    const val COMP_INV   = "comp_inv"

    // Mic-E
    const val MICE_SHORT         = "mice_short"
    const val MICE_INV_DSTCALL   = "mice_inv_dstcall"
    const val MICE_INV_INFOFIELD = "mice_inv_infofield"

    // Object / item
    const val OBJ_SHORT  = "obj_short"
    const val OBJ_INV    = "obj_inv"
    const val ITEM_SHORT = "item_short"
    const val ITEM_INV   = "item_inv"

    // Message
    const val MSG_SHORT    = "msg_short"
    const val MSG_INV      = "msg_inv"
    const val MSG_NO_DST   = "msg_no_dst"
    const val MSG_DST_LONG = "msg_dst_long"
    const val MSG_ID_INV   = "msg_id_inv"
    const val MSG_REPLYACK = "msg_replyack"
    const val MSG_ACK_REJ  = "msg_ack_rej"
    const val MSG_CR       = "msg_cr"

    // Position encoding
    const val POS_ENC_INV = "pos_enc_inv"

    // NMEA
    const val NMEA_SHORT   = "nmea_short"
    const val NMEA_INVALID = "nmea_invalid"
    const val GPRMC_NOFIX  = "gprmc_nofix"

    // Timestamp
    const val TIMESTAMP_INV = "timestamp_inv"

    // Weather
    const val WX_INV = "wx_inv"

    // Telemetry
    const val TLM_INV = "tlm_inv"
}
