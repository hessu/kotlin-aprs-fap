package fi.aprs.fap

enum class PacketType(val value: String) {
    LOCATION("location"),
    OBJECT("object"),
    ITEM("item"),
    MESSAGE("message"),
    WX("wx"),
    TELEMETRY("telemetry"),
    TELEMETRY_MESSAGE("telemetry-message"),
    STATUS("status"),
    CAPABILITIES("capabilities"),
    BEACON("beacon"),
}
