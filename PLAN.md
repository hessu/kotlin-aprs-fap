# kotlin-aprs-fap — Implementation Plan

## Overview

Port of `go-aprs-fap` to Kotlin/JVM. The Go library is itself a port of the
Perl `Ham::APRS::FAP` module. All test packets and expected values from the Go
(and original Perl) test suite must be retained unchanged.

Source reference: `../go-aprs-fap/` (Go), `../perl-aprs-fap/t/` (original Perl tests).

---

## Deliverables

- Pure-Kotlin library, no external runtime dependencies (stdlib only)
- Full test suite matching the Go test suite (5 300+ lines of tests → Kotlin)
- Gradle build (Kotlin DSL) targeting JVM 11+ / Android API 26+
- Published to Maven Central (group `fi.aprs.fap`, artifact `kotlin-aprs-fap`)
- Dokka API documentation
- GitHub Actions CI

---

## Phase 1 — Project scaffolding

**Files to create:**

| File | Purpose |
|---|---|
| `settings.gradle.kts` | Project name, plugin management |
| `build.gradle.kts` | Kotlin, dokka, publishing, signing plugins |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle version pin |
| `.github/workflows/tests.yml` | CI: build + test on push/PR |
| `.gitignore` | Standard Kotlin/Gradle gitignore |

**Decisions:**
- Kotlin 2.x, JVM target 11
- JUnit 5 for tests (`kotlin-test-junit5`)
- No KMP for now; plain Kotlin/JVM
- `kotlin.jvm.toolchain(11)` so both JVM and Android are covered

---

## Phase 2 — Core data types

**Create `src/main/kotlin/fi/aprs/fap/`:**

### `PacketType.kt`
Enum with values: `LOCATION`, `OBJECT`, `ITEM`, `MESSAGE`, `WX`, `TELEMETRY`,
`TELEMETRY_MESSAGE`, `STATUS`, `CAPABILITIES`, `BEACON`

### `Format.kt`
Enum with values: `UNCOMPRESSED`, `COMPRESSED`, `MICE`, `NMEA`

### `ParseError.kt`
```kotlin
class ParseException(val code: String, message: String) : Exception(message)
data class ParseWarning(val code: String, val message: String)
```
All error code constants as companion object `val`s matching Go sentinel codes
(e.g. `PACKET_NO_BODY = "packet_no_body"`).

### `Packet.kt`
Data class (or mutable class built during parsing) with all fields from Go
`Packet` struct. Nullable fields replace Go pointer fields:
```kotlin
var latitude: Double? = null
var longitude: Double? = null
var speed: Double? = null
// ...
```

### `Weather.kt`
Data class matching Go `Weather` struct (all nullable fields).

### `Telemetry.kt`
Data class matching Go `Telemetry` struct.

### `Message.kt`
Data class matching Go `Message` struct.

### `Digipeater.kt`
Data class with `call: String` and `wasDigied: Boolean`.

---

## Phase 3 — Entry point and header parsing

**Create `Fap.kt`:**

```kotlin
fun parse(raw: String, options: ParseOptions = ParseOptions()): Packet
```

- Split header/body at first `:`
- `parseHeader()`: extract `SRC>DST,DIGI,...`
- AX.25 call validation: `checkAx25Call()`, `isValidSrcCall()`, `isValidDigiCall()`
- `isQConstruct()`, `isIPv6Hex()` helpers
- `IsBeacon()` extension

`ParseOptions` data class:
```kotlin
data class ParseOptions(
    val isAX25: Boolean = false,
    val acceptBrokenMicE: Boolean = false,
    val rawTimestamp: Boolean = false,
)
```

**Tests (Phase 3):**
- `FapTest.kt` — error cases, header parsing, basic dispatch
- `Ax25CallTest.kt` — AX.25 callsign validation
- `Ax25OptionTest.kt` — AX.25 option enforcement
- `BadPacketTest.kt` — malformed packets
- `BeaconTest.kt` — beacon packet detection

---

## Phase 4 — Uncompressed position

**Create `Position.kt`:**

- `parsePositionNoTimestamp()` — `!` and `=` packets
- `parsePositionWithTimestamp()` — `/` and `@` packets
- `parseUncompressedPosition()` — `DDMM.MM[NS]/DDDMM.MM[EW]`
- `parseUncompressedLat()` / `parseUncompressedLon()`
- `parseDegreesMinutes()` — ambiguity handling (spaces → center of box)
- `posResolution()` — meters per ambiguity level
- `isValidSymbolTable()`, `isPhgBase()`, `isUpperAlphaNum()`
- `parsePositionComment()` — PHG, RNG, course/speed, altitude, DAO
- `parsePositionFallback()` — last-resort `!` search in body
- `stripInlineTelemetry()` — remove `|...|` blocks

**Tests:**
- `UncompressedTest.kt` — NE/SW/ambiguity/PHG/RNG/comment cases
- `UncompressedMovingTest.kt` — course, speed, altitude
- `PositionFallbackTest.kt` — fallback `!` parsing
- `ErrorsTest.kt` — position-specific error codes

---

## Phase 5 — Compressed position

**Add to `Position.kt`:**

- `parseCompressedPosition()` — base-91 lat/lon decode, csT byte decode
  - Course/speed: `c1 * 4`, `pow(1.08, s1) * 1.852`
  - Altitude: `pow(1.002, c1*91+s1) * 0.3048`
  - Radio range: `2 * pow(1.08, s1) * 1.609344`
  - GPS fix status from compression type byte bit 5
- `isCompressedTableChar()`

**Tests:**
- `CompressedTest.kt`

---

## Phase 6 — DAO extension

**Add to `Position.kt`:**

- `parseDAO()` — find `!Dxx!` in comment, dispatch human/base-91
- `applyHumanDAO()` — 0.001 minute resolution adjustment
- `applyBase91DAO()` — 0.0001 minute resolution adjustment

**Tests:**
- `DaoTest.kt`

---

## Phase 7 — Timestamp

**Create `Timestamp.kt`:**

- `parseTimestamp()` — 7-char APRS timestamps:
  - `DDHHMMz` — UTC day/hour/min
  - `DDHHMM/` — local time day/hour/min
  - `HHMMSSh` — UTC hour/min/sec
- Returns `Instant?`; if in future, assumes last month

**Tests:**
- `TimestampTest.kt`

---

## Phase 8 — Mic-E

**Create `MicE.kt`:**

- `parseMicE()` — main entry, decode destination callsign table
- `parseMicEMangled()` — collapsed-space repair with `WithAcceptBrokenMicE`
- `parseMicEBase91Telemetry()` — extract `|...|` block (last-match semantics)
- `parseMicETelemetry()` — old hex telemetry (`'XXYYZZ...`)
- `MicEMBitsToMessage()` — decode 3-bit message status
- `micEDestTable` — decode table (same as Go map)

**Key pitfall:** Mic-E info field bytes can be in range 0x1C–0x7F. Kotlin
strings use UTF-16; read Mic-E data from the raw byte sequence. See CLAUDE.md.

**Tests:**
- `MicETest.kt` — non-moving, moving, altitude, south/west
- `TelemetryMicETest.kt` — base-91 telemetry in mic-e comments

---

## Phase 9 — Objects and Items

**Create `Object.kt`:**
- `parseObject()` — `;` packets, 9-char name, `*`/`_` alive indicator, timestamp + position

**Create `Item.kt`:**
- `parseItem()` — `)` packets, 3-9 char name terminated by `!` or `_`

**Tests:**
- `ObjectTest.kt`
- `ObjectInvTest.kt`
- `ItemTest.kt`

---

## Phase 10 — Messages

**Create `Message.kt`** (parsing) and **`MessageEncoder.kt`** (encoding):

- `parseMessage()` — `:ADDRESSEE:text{ID}AckID`
- `encodeMessage()` — validate and encode `Message` struct
- `isTelemetryMessage()` — detect PARM/UNIT/EQNS/BITS prefix
- `isValidMsgID()`, `containsCRLF()`

**Tests:**
- `MessageTest.kt`
- `MessageBinaryCleanTest.kt`

---

## Phase 11 — Status and Capabilities

**Create `Status.kt`:**
- `parseStatus()` — `>` packets, optional timestamp prefix
- `parseCapabilities()` — `<` packets, `key=value,key,...` parsing

**Tests:**
- `StatusTest.kt`

---

## Phase 12 — Weather

**Create `Weather.kt`:**

- `parseWeatherPositionless()` — `_MMDDHHMM...` format
- `parseWeatherFromComment()` — CCC/SSS wind + remaining fields
- `parseWeatherFields()` — field-by-field parser (`c`, `s`, `g`, `t`, `r`, `p`,
  `P`, `h`, `b`, `L`, `l`, `Os`, `F`, `X`, `V`, `#`)
- `parseULTW()` — `$ULTW...` hex-field weather
- `parseULTWLogging()` — `!!...` logging format
- `isSoftwareID()`, `skipWxField()`, `parseWxInt()`, `parseWxSignedInt()`
- ULTW helpers: `ultwWindSpeed()`, `ultwDirection()`, `ultwTemp()`, `ultwShift()`

**Tests:**
- `WxBasicTest.kt`
- `WxUltwTest.kt`

---

## Phase 13 — Telemetry

**Create `Telemetry.kt`:**
- `parseTelemetry()` — `T#seq,a1,...,bits` format
- `isNumericValue()` — validate analog channel values

**Tests:**
- `TelemetryTest.kt`

---

## Phase 14 — NMEA

**Create `Nmea.kt`:**

- `parseNMEA()` — dispatch `$GPRMC`, `$GPGGA`, `$GPGLL`
- `parseGPRMC()` — with fix check, timestamp, lat/lon, speed, course
- `parseGPGGA()` — with fix quality check, lat/lon, altitude
- `parseGPGLL()` — lat/lon only
- `parseGPRMCTimestamp()` — HHMMSS + DDMMYY → Instant
- `parseNMEACoordWithRes()` — degrees+minutes with resolution
- `nmeaPosResolution()` — resolution from decimal digit count
- NMEA checksum verification (XOR of bytes between `$` and `*`)

**Tests:**
- `NmeaTest.kt`

---

## Phase 15 — Utilities

**Create `Utilities.kt`:**
- `distance(lat0, lon0, lat1, lon1)` — Haversine, returns km
- `direction(lat0, lon0, lat1, lon1)` — initial bearing in degrees

**Tests:**
- `UtilitiesTest.kt`

---

## Phase 16 — Position encoding

**Create `PositionEncoder.kt`:**

- `encodePosition(lat, lon, speed, course, altitude, symbol, opts)` → `String`
- `EncodePositionOpts` data class (ambiguity, timestamp, messagingCapable, dao, comment)
- `formatMinutes()` — 4-digit minutes + optional DAO digits

**Tests:**
- `PositionEncodeTest.kt`

---

## Phase 17 — APRS-IS client

**Create `AprsIs.kt`:**

- `AprsIsConnection` class wrapping `java.net.Socket`
- `connect(addr, port, callsign, passcode, appName, appVer, filter?)` — factory
- `readLine(timeoutMs)` — read raw bytes until `\n`, return `ByteArray`
- `readPacket(timeoutMs)` — skip `#` comment lines; return raw `ByteArray`
- `sendLine(line)` — encode as ASCII and append `\r\n`
- `close()`
- `aprsPasscode(callsign): Short` — XOR hash, same algorithm as Go

**Important:** The APRS-IS socket is a **binary stream**. Read it as raw bytes
(`InputStream.read()`), never via a `Reader`. Do not apply any string encoding
(not UTF-8, not Latin-1) to the socket stream. Return packet lines as
`ByteArray`. The caller or parser is responsible for decoding specific text
fields (comment, status, message text) as UTF-8; header and addressing fields
are 7-bit ASCII.

**Tests:**
- `AprsIsTest.kt` — uses `ServerSocket` mock (mirrors Go test)
- `AprsIsPathsTest.kt`

---

## Phase 18 — API documentation and publishing

- Add Dokka plugin; document all public API with KDoc
- Configure Maven Central publishing (Sonatype OSSRH):
  - Group: `fi.aprs.fap`, artifact: `fap`
  - Include sources JAR and Javadoc JAR
  - PGP signing via `gradle-signing-plugin`
- Add `RELEASING.md` with release checklist
- Tag `v0.1.0` after all tests pass

---

## Phase 19 — CI/CD

`.github/workflows/tests.yml`:
- Trigger: push and pull_request on `main`
- Matrix: JDK 11, 17, 21
- Steps: `./gradlew test`
- On tag push: publish to Maven Central

---

## Test file mapping (Go → Kotlin)

| Go test file | Kotlin test file | Phase |
|---|---|---|
| `fap_test.go` | `FapTest.kt` | 3 |
| `ax25_call_test.go` | `Ax25CallTest.kt` | 3 |
| `ax25_option_test.go` | `Ax25OptionTest.kt` | 3 |
| `badpacket_test.go` | `BadPacketTest.kt` | 3 |
| `beacon_test.go` | `BeaconTest.kt` | 3 |
| `errors_test.go` | `ErrorsTest.kt` | 3 |
| `uncompressed_test.go` | `UncompressedTest.kt` | 4 |
| `uncompressed_moving_test.go` | `UncompressedMovingTest.kt` | 4 |
| `position_fallback_test.go` | `PositionFallbackTest.kt` | 4 |
| `compressed_test.go` | `CompressedTest.kt` | 5 |
| `dao_test.go` | `DaoTest.kt` | 6 |
| `timestamp_test.go` | `TimestampTest.kt` | 7 |
| `mice_test.go` | `MicETest.kt` | 8 |
| `telemetry_mice_test.go` | `TelemetryMicETest.kt` | 8 |
| `object_test.go` | `ObjectTest.kt` | 9 |
| `object_inv_test.go` | `ObjectInvTest.kt` | 9 |
| `item_test.go` | `ItemTest.kt` | 9 |
| `message_test.go` | `MessageTest.kt` | 10 |
| `message_binary_clean_test.go` | `MessageBinaryCleanTest.kt` | 10 |
| `status_test.go` | `StatusTest.kt` | 11 |
| `wx_basic_test.go` | `WxBasicTest.kt` | 12 |
| `wx_ultw_test.go` | `WxUltwTest.kt` | 12 |
| `telemetry_test.go` | `TelemetryTest.kt` | 13 |
| `nmea_test.go` | `NmeaTest.kt` | 14 |
| `utilities_test.go` | `UtilitiesTest.kt` | 15 |
| `position_encode_test.go` | `PositionEncodeTest.kt` | 16 |
| `aprsis_test.go` | `AprsIsTest.kt` | 17 |
| `aprsis_paths_test.go` | `AprsIsPathsTest.kt` | 17 |

---

## Dependency budget

| Scope | Dependency | Reason |
|---|---|---|
| `implementation` | Kotlin stdlib | Language |
| `testImplementation` | `kotlin-test-junit5` | Test framework |
| `testImplementation` | `junit-jupiter` | JUnit 5 engine |
| `testRuntimeOnly` | `junit-platform-launcher` | JUnit 5 runner |

No runtime dependencies outside the Kotlin stdlib.
