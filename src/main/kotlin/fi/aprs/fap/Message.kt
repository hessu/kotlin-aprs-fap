package fi.aprs.fap

/** Data from an APRS message, ack, or reject packet. */
class Message {
    /** Destination callsign (addressee). */
    var destination: String = ""
    /** Message text body. */
    var text: String = ""
    /** Message ID (1–5 alphanumeric characters). */
    var id: String = ""
    /** Acknowledgment message ID. */
    var ackId: String = ""
    /** Reject message ID. */
    var rejId: String = ""
}
