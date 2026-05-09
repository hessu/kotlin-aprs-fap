# CLAUDE.md — kotlin-aprs-fap

Guidance for AI coding agents working on this repository.

## Build and test commands

```bash
./gradlew build                              # Compile + test
./gradlew test                               # Tests only
./gradlew test --tests "*.UncompressedTest"  # Single test class
./gradlew test --info                        # Verbose test output
./gradlew dokkaHtml                          # Generate API docs
./gradlew publishToMavenLocal               # Local publish for testing
```

## Source layout

```
src/
  main/kotlin/fi/aprs/fap/
    Fap.kt              ← parse(), Packet, ParseOptions, AX.25 helpers
    PacketType.kt       ← enum LOCATION, OBJECT, ITEM, MESSAGE, …
    Format.kt           ← enum UNCOMPRESSED, COMPRESSED, MICE, NMEA
    ParseError.kt       ← ParseException, ParseWarning, error code constants
    Digipeater.kt       ← data class Digipeater
    Packet.kt           ← data class / mutable Packet
    Weather.kt          ← data class Weather + all parsers
    Telemetry.kt        ← data class Telemetry + parseTelemetry()
    Message.kt          ← data class Message + parseMessage() + encodeMessage()
    Position.kt         ← parseUncompressedPosition(), parseCompressedPosition(), …
    MicE.kt             ← parseMicE(), parseMicEMangled(), telemetry helpers
    Object.kt           ← parseObject()
    Item.kt             ← parseItem()
    Status.kt           ← parseStatus(), parseCapabilities()
    Nmea.kt             ← parseNMEA(), GPRMC/GPGGA/GPGLL
    Timestamp.kt        ← parseTimestamp()
    PositionEncoder.kt  ← encodePosition(), EncodePositionOpts
    Utilities.kt        ← distance(), direction()
    AprsIs.kt           ← AprsIsConnection, aprsPasscode()
  test/kotlin/fi/aprs/fap/
    (one test file per source file, see PLAN.md for mapping)
```

## Go → Kotlin translation patterns

These are the mechanical patterns to apply when porting each Go file.

### Nullable vs pointer

Go uses pointers for optional fields; Kotlin uses nullable types:

| Go | Kotlin |
|---|---|
| `*float64` | `Double?` |
| `*int` | `Int?` |
| `*bool` | `Boolean?` |
| `*time.Time` | `java.time.Instant?` |
| `*ParseError` (in Warnings) | `ParseWarning` (value type) |

### Error handling

Go: `return p, p.fail(ErrPosShort, "position body too short")`

Kotlin: `throw ParseException(ParseError.POS_SHORT, "position body too short")`

The Go parser returns a partially-filled `Packet` along with the error. In
Kotlin, the `Packet` is built up on a local mutable object; on a hard failure,
throw the exception without returning the partial packet. Tests that check for
errors only check `assertFailsWith<ParseException>` and inspect the code.

Go: `p.warn(ErrTimestampInvalid, "...")` 

Kotlin: `packet.warnings.add(ParseWarning(ParseError.TIMESTAMP_INVALID, "..."))`

### String operations

| Go | Kotlin |
|---|---|
| `strings.HasPrefix(s, "x")` | `s.startsWith("x")` |
| `strings.HasSuffix(s, "x")` | `s.endsWith("x")` |
| `strings.IndexByte(s, ':')` | `s.indexOf(':')` |
| `strings.LastIndexByte(s, '{')` | `s.lastIndexOf('{')` |
| `strings.Split(s, ",")` | `s.split(",")` |
| `strings.SplitN(s, ",", 8)` | `s.split(",", limit = 8)` |
| `strings.TrimSpace(s)` | `s.trim()` |
| `strings.TrimRight(s, "\r\n")` | `s.trimEnd('\r', '\n')` |
| `strings.EqualFold(a, b)` | `a.equals(b, ignoreCase = true)` |
| `strings.ToUpper(s)` | `s.uppercase()` |
| `strings.Repeat(" ", n)` | `" ".repeat(n)` |
| `fmt.Sprintf("%.4f", f)` | `"%.4f".format(f)` |
| `strconv.Atoi(s)` | `s.toInt()` / `s.toIntOrNull()` |
| `strconv.ParseFloat(s, 64)` | `s.toDouble()` / `s.toDoubleOrNull()` |
| `strconv.ParseUint(s, 16, 8)` | `s.toUInt(16)` / `s.toUByte(16)` |

### Byte/character access — CRITICAL

Go strings are byte slices. `s[i]` returns a `byte`. APRS packets can contain
bytes outside the ASCII range (Mic-E info field uses 0x1C–0x7F; other fields
may contain binary data).

**The `parse()` API accepts `ByteArray`**, not `String`. The top-level parser
and all sub-parsers operate on `ByteArray` (or a `ByteArray`-backed view).
Do not convert the packet to a Kotlin `String` for parsing; instead access
bytes directly:

```kotlin
val b  = bytes[i]                        // Byte (signed -128..127)
val u  = bytes[i].toInt() and 0xFF       // unsigned 0..255, like Go's byte
val ch = u.toChar()                      // character for ASCII comparisons
```

Text fields that are human-readable (comment, status text, message text) are
decoded from the raw bytes as UTF-8 when extracted:
```kotlin
val comment = bytes.sliceArray(startIdx until endIdx).toString(Charsets.UTF_8)
```

Header and addressing fields (callsigns, path) are 7-bit ASCII; simple
`and 0xFF` or direct comparison against character codes is fine.

**APRS-IS socket:** Read as raw bytes from `InputStream`. Do **not** use a
`Reader` or apply any charset to the socket stream. Return `ByteArray` lines
from `readLine()` / `readPacket()`. The login line sent to the server is plain
ASCII — encode with `Charsets.US_ASCII`.

### Integer arithmetic matching Go byte math

Go frequently does `int(body[i]) - 33` on raw bytes. Kotlin equivalent:
```kotlin
(bytes[i].toInt() and 0xFF) - 33
```

The `and 0xFF` is needed because Kotlin `Byte` is signed (-128..127).

### Boolean pointers

Go uses `new(true)` / `new(false)` for optional boolean fields.
Kotlin: just assign `true` / `false` to the nullable `Boolean?` field.

### time.Time → Instant

Go `time.Now().UTC()` → Kotlin `Instant.now()`  
Go `time.Date(y, m, d, h, min, s, 0, time.UTC)` →
```kotlin
ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC).toInstant()
```

For the `DDHHMMz` timestamp logic (assume last month if in the future):
```kotlin
val now = Instant.now()
var ts = ...
if (ts.isAfter(now)) ts = ts.minus(30, ChronoUnit.DAYS)  // approximate
```

### Maps / companion objects

Go `var micEDestTable = map[byte]micEDestInfo{...}` →

```kotlin
private val MIC_E_DEST_TABLE = mapOf(
    '0'.code to MicEDestInfo(digit = 0, msgBit = 0, isNorth = false),
    // ...
)
```

Use `Char.code` or `Byte.toInt() and 0xFF` consistently.

### Math functions

| Go | Kotlin |
|---|---|
| `math.Pow(a, b)` | `a.pow(b)` or `Math.pow(a, b)` |
| `math.Sqrt(x)` | `sqrt(x)` (kotlin.math) |
| `math.Sin(x)` | `sin(x)` |
| `math.Cos(x)` | `cos(x)` |
| `math.Atan2(y, x)` | `atan2(y, x)` |
| `math.Floor(x)` | `floor(x)` |
| `math.Round(x)` | `round(x)` |
| `math.Abs(x)` | `abs(x)` |
| `math.Pi` | `PI` |

Import: `import kotlin.math.*`

---

## Architecture rules

### One file per parsing domain

Each Go source file maps to one Kotlin source file. Do not merge multiple
parsers into a single file. This makes the mapping obvious when consulting the
Go source.

### Internal functions are private

All helper functions that have no Go-level equivalent exposed as public API
should be `private` or `internal` in Kotlin.

### Packet building

Go uses a pointer receiver `(p *Packet)` to build the packet in place.
In Kotlin, pass a mutable `Packet` builder object (or a plain mutable class
that is sealed before returning). Do not use a data class with `copy()` chains
for building — too verbose. Build a mutable object, then return it or convert
to a final type if needed.

### No coroutines in parser

The parser is pure synchronous logic. Do not introduce coroutines, Flow, or
suspend functions in the parsing core. The APRS-IS client may offer a
coroutine-friendly wrapper as a separate optional module in the future, but the
core `AprsIsConnection` class uses blocking Java I/O.

---

## Test conventions

### Test file naming

One test file per production file, named `<Production>Test.kt`.
Place all tests in `src/test/kotlin/fi/aprs/fap/`.

### Test style

Prefer individual test functions for named cases ported from Go tests.
Use `@Test` annotation. Use `@Nested` inner class for grouping related subtests
when the Go file uses table-driven tests with `t.Run`.

```kotlin
@Test
fun testUncompressedNortheast() {
    val p = parse("OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51N/02505.68E#PHG7220...")
    assertEquals(Format.UNCOMPRESSED, p.format)
    assertEquals(60.4752, p.latitude!!, 0.0001)
    assertEquals(25.0947, p.longitude!!, 0.0001)
}
```

### Float comparisons

Use `assertEquals(expected, actual, delta)` (JUnit 5) or `assertApprox()`:

```kotlin
private fun assertApprox(expected: Double, actual: Double?, delta: Double = 0.0001) {
    assertNotNull(actual)
    assertEquals(expected, actual!!, delta)
}
```

### Error code assertions

```kotlin
val ex = assertFailsWith<ParseException> { parse("bad packet") }
assertEquals(ParseError.POS_SHORT, ex.code)
```

### Test packet literals

**Copy Go test packet strings verbatim**, including any control characters.
In Kotlin, use escape sequences:
- Go `\x1C` → Kotlin `` in a `String` (but see byte-access note above)
- Go `\x00` → Kotlin ` `

When a test exercises binary data in Mic-E or other binary fields, build the
byte array explicitly if the string encoding would corrupt it:

```kotlin
// Go: "OH7LZB-13>SX15S6,...:'I',l \x1C>/]"
val raw = "OH7LZB-13>SX15S6,TCPIP*,qAC,FOURTH:'I',l >/]"
```

---

## APRS-specific traps

### Mic-E destination table

Characters `K` and `L` both map to digit `0` with different meanings.
The table in `MicE.kt` must be an exact copy of the Go `micEDestTable`.
Do not simplify or deduplicate it.

### Base-91 telemetry in Mic-E

The `|...|` search uses **last-match** semantics on the closing `|`, searching
from the right. This matches the Perl regex `/(.*)\|(.*)\|/` greedy behavior.
Port the exact algorithm from `parseMicEBase91Telemetry()` — do not use a
simple `indexOf` / `lastIndexOf` pair.

### Ambiguity box centering

When latitude ambiguity > 0, the parser centers the coordinate in the
ambiguity box by adding half the ambiguity step to the minutes. This is
non-obvious. Port the switch in `parseDegreesMinutes()` exactly.

### Longitude inherits ambiguity from latitude

Longitude uses the *same* ambiguity level computed for latitude. Spaces in
longitude digits within the ambiguous portion are acceptable; spaces in
non-ambiguous digits are errors.

### ULTW wind direction encoding

ULTW direction is `rawValue & 0xFF * 1.41176`, not a plain degrees value.
The masking with `0xFF` is intentional — see `ultwDirection()`.

### Mic-E speed encoding

Speed in Mic-E uses `sp * 10 + dc / 10` (integer division) for tens and units,
where `sp = body[3] - 28` and `dc = body[4] - 28`. If speed >= 800, subtract
800. If course >= 400, subtract 400. Port the exact arithmetic.

### NMEA position resolution

`nmeaPosResolution(decimals)` uses `1.852 * 1000 * 10^(-decimals)` with a
special case for `decimals <= -2` (base is 600 not 1000). Port exactly.

### DAO extension

The `!DAO!` search scans the comment string for `!Dxx!` patterns where D is
A-Z (human-readable, digits 0-9 in xx) or a-z (base-91, xx in range 0x21-0x7B).
Lowercase datum byte is uppercased. Port `parseDAO()` exactly — the loop
structure matters for finding the correct `!` pair.

### `new(true)` / `new(false)` in Go tests

Go test code uses `new(true)` as a shorthand for creating a `*bool` pointing to
`true`. In Kotlin tests, just compare against `true`/`false` directly or check
for non-null. There is no Kotlin equivalent; the point is just that the field
is set.

### Course 0 vs 360

In uncompressed position comments, course 0 means "unknown"; in compressed
position, `c1 == 0` is stored as `360` (north). Port each case from its
respective function — do not unify.

---

## Go source reference

The Go source is at `../go-aprs-fap/`. When in doubt, read the Go source
directly. Original Perl tests are at `../perl-aprs-fap/t/` — the Go port
already translated all packet strings and expected values; use the Go tests
as the canonical reference.

If a behavior in Kotlin differs from Go, the **Go version is authoritative**
unless the difference is due to a language-level concern (e.g. byte encoding).

---

## Publishing checklist (when ready to release)

1. All 28 test files ported and passing
2. Dokka generates without warnings
3. `./gradlew publishToMavenLocal` works and produces correct POM
4. GPG key configured in `~/.gradle/gradle.properties`
5. Sonatype OSSRH credentials in `~/.gradle/gradle.properties`
6. Version bumped in `build.gradle.kts`
7. `git tag v0.1.0 && git push --tags`
8. GitHub Actions publishes to staging; promote in Sonatype portal
