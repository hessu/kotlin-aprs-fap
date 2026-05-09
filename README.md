# kotlin-aprs-fap

Finnish APRS Parser (Fabulous APRS Parser) — Kotlin edition

A Kotlin/JVM port of [go-aprs-fap](https://github.com/hessu/go-aprs-fap), which
is itself a port of the [Ham::APRS::FAP](https://metacpan.org/pod/Ham::APRS::FAP)
Perl module. A fairly complete APRS (Automatic Packet Reporting System) parser,
designed for use in Kotlin and Android applications. Implemented in pure Kotlin
with no runtime dependencies (stdlib only).

The original Perl module is stable and mature, and is used to power the
<http://aprs.fi/> web site.

## Supported packet types

- Position (uncompressed, compressed, Mic-E)
- Objects and items
- Messages, acks, and rejects
- Weather reports (standard, ULTW, positionless)
- Telemetry
- Status reports
- Station capabilities
- NMEA (GPRMC, GPGGA, GPGLL)

## Not handled

- Special objects (area, signpost, etc)
- Network tunneling / third party packets
- Direction finding
- Station capability queries
- User defined data formats

Based on APRS specification 1.0.1.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("fi.aprs.fap:kotlin-aprs-fap:0.1.0")
}
```

Maven:

```xml
<dependency>
    <groupId>fi.aprs.fap</groupId>
    <artifactId>kotlin-aprs-fap</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

```kotlin
import fi.aprs.fap.parse

val raw = "N0CALL>APRS,WIDE1-1,WIDE2-1,qAo,IGATE:!6128.23N/02353.52E-PHG2360/Testing"
val packet = parse(raw)

println("Source: ${packet.srcCallsign}")
println("Type: ${packet.type}")
println("Lat: ${"%.4f".format(packet.latitude!!)}")
println("Lon: ${"%.4f".format(packet.longitude!!)}")
```

## Error handling

`parse()` throws `ParseException` on hard parse failures. The exception carries
a machine-readable `code` (e.g. `"pos_short"`) and a human-readable message.
Use error code constants from `ParseError` for reliable matching:

```kotlin
import fi.aprs.fap.parse
import fi.aprs.fap.ParseException
import fi.aprs.fap.ParseError

try {
    val packet = parse(raw)
} catch (e: ParseException) {
    when (e.code) {
        ParseError.LOC_INVALID -> println("invalid location")
        ParseError.POS_SHORT   -> println("position too short")
        else                   -> println("parse failure: ${e.code}: ${e.message}")
    }
}
```

### Warnings

Non-fatal issues (e.g. unparseable timestamp in an otherwise valid packet) are
collected in `Packet.warnings`:

```kotlin
val packet = parse(raw)
for (w in packet.warnings) {
    println("warning: ${w.code}: ${w.message}")
}
```

## Parse options

```kotlin
import fi.aprs.fap.ParseOptions

val packet = parse(raw, ParseOptions(
    isAX25          = true,   // enforce strict AX.25 callsign rules
    acceptBrokenMicE = true,  // attempt to repair corrupted Mic-E packets
    rawTimestamp    = true,   // return timestamp as raw string, not Instant
))
```

## Position encoding

`encodePosition` builds an uncompressed APRS position body string ready to
transmit. Coordinates are decimal degrees, speed in km/h, course in degrees,
altitude in meters. Symbol is a 2-character string (table + code).

```kotlin
import fi.aprs.fap.encodePosition
import fi.aprs.fap.EncodePositionOpts

val body = encodePosition(
    lat    = 60.4525,
    lon    = 24.9842,
    speed  = 45.0,
    course = 180.0,
    alt    = 120.0,
    symbol = "/>",
)
// "!6027.15N/02459.05E/>180/024/A=000394"
```

Optional parameters via `EncodePositionOpts`:

```kotlin
val body = encodePosition(60.4525, 24.9842, symbol = "/-",
    opts = EncodePositionOpts(
        ambiguity        = 2,      // 0-4: reduces coordinate precision
        messagingCapable = true,   // changes ! to =
        dao              = true,   // add !DAO! extension for extra precision
        comment          = "Test",
    )
)
```

Include a timestamp (changes data type identifier to `/`):

```kotlin
import java.time.Instant

val body = encodePosition(60.4525, 24.9842, symbol = "/-",
    opts = EncodePositionOpts(
        timestamp = Instant.parse("2024-03-15T12:30:45Z"),
    )
)
// "/123045h6027.15N/02459.05E-"
```

## Message encoding

`encodeMessage` encodes a `Message` into an APRS message body string.

```kotlin
import fi.aprs.fap.encodeMessage
import fi.aprs.fap.Message

val body = encodeMessage(Message(
    destination = "N0CALL",
    text        = "Hello world",
    id          = "42",
))
// ":N0CALL   :Hello world{42"

// Send an ack
val ack = encodeMessage(Message(destination = "N0CALL", ackId = "42"))

// Send a reject
val rej = encodeMessage(Message(destination = "N0CALL", rejId = "42"))
```

## APRS-IS client

```kotlin
import fi.aprs.fap.AprsIsConnection

val conn = AprsIsConnection.connect(
    host     = "rotate.aprs2.net",
    port     = 14580,
    callsign = "N0CALL",
    passcode = "-1",
    appName  = "myapp",
    appVer   = "0.1",
    filter   = "r/60.18/24.94/100",
)

// readPacket returns raw bytes; parse() accepts ByteArray
while (true) {
    val bytes  = conn.readPacket(timeoutMs = 30_000)
    val packet = parse(bytes)
    println("${packet.srcCallsign}> type=${packet.type}")
}

conn.close()
```

### Passcode calculation

```kotlin
import fi.aprs.fap.aprsPasscode

val code = aprsPasscode("N0CALL")   // returns Short
```

## Utilities

```kotlin
import fi.aprs.fap.distance
import fi.aprs.fap.direction

val km  = distance(60.18, 24.94, 61.47, 23.89)
val deg = direction(60.18, 24.94, 61.47, 23.89)
```

## Requirements

- Kotlin 2.x
- JVM 11+ (or Android API 26+ for `java.time`)

## See also

- [go-aprs-fap](https://github.com/hessu/go-aprs-fap) — Go edition (parent of this port)
- [Ham::APRS::FAP](https://metacpan.org/pod/Ham::APRS::FAP) — original Perl module
- [libfap](http://pakettiradio.net/libfap/) — C library port
- [aprs.fi](https://aprs.fi/) — powered by the original Perl module

## Copyright and licence

Copyright (C) 2005-2026 Tapio Sokura, OH2KKU  
Copyright (C) 2007-2026 Heikki Hannikainen, OH7LZB

This library is free software; you can redistribute it and/or modify
it under the same terms as Perl itself.
