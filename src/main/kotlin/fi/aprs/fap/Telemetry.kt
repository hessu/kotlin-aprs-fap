package fi.aprs.fap

/** Telemetry data from a T# telemetry packet or embedded Mic-E telemetry. */
class Telemetry {
    /** Sequence number. */
    var seq: Int = 0
    /** Analog channel values (up to 5). Null elements represent undefined channels. */
    var vals: Array<Double?> = arrayOfNulls(5)
    /** Digital bits as an 8-character binary string (e.g. "10110000"), LSB first. */
    var bits: String = ""
}
