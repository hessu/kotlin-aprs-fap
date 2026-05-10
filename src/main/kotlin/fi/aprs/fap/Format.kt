package fi.aprs.fap

enum class Format(val value: String) {
    UNCOMPRESSED("uncompressed"),
    COMPRESSED("compressed"),
    MICE("mice"),
    NMEA("nmea"),
}
