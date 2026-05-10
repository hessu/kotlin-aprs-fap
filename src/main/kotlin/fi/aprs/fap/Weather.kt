package fi.aprs.fap

import kotlin.math.pow
import kotlin.math.round

/** Weather data decoded from a weather report packet. All fields are in SI units. */
class Weather {
    /** Wind direction in degrees (0–360). */
    var windDirection: Double? = null
    /** Wind speed in m/s. */
    var windSpeed: Double? = null
    /** Wind gust speed in m/s. */
    var windGust: Double? = null
    /** Outdoor temperature in degrees Celsius. */
    var temp: Double? = null
    /** Indoor temperature in degrees Celsius. */
    var tempIn: Double? = null
    /** Relative humidity in percent (1–100). */
    var humidity: Int? = null
    /** Indoor humidity in percent (1–100). */
    var humidityIn: Int? = null
    /** Barometric pressure in millibars (hPa). */
    var pressure: Double? = null
    /** Rain in the last hour in mm. */
    var rain1h: Double? = null
    /** Rain in the last 24 hours in mm. */
    var rain24h: Double? = null
    /** Rain since midnight in mm. */
    var rainMidnight: Double? = null
    /** Snow in the last 24 hours in mm. */
    var snow24h: Double? = null
    /** Luminosity in watts per square meter. */
    var luminosity: Int? = null
    /** Water level above or below flood stage in meters. */
    var waterLevel: Double? = null
    /** Ionizing radiation in nanosieverts per hour. */
    var radiation: Double? = null
    /** Battery voltage in volts. */
    var batteryVoltage: Double? = null
    /** Software/device identifier string. */
    var software: String = ""

    /** Non-weather comment text remaining after parsing weather fields. */
    internal var commentAfterWx: String = ""

    fun hasData(): Boolean =
        windDirection != null || windSpeed != null || windGust != null ||
        temp != null || tempIn != null || humidity != null || humidityIn != null ||
        pressure != null || rain1h != null || rain24h != null || rainMidnight != null ||
        snow24h != null || luminosity != null
}

// ── Positionless weather ──────────────────────────────────────────────────────

internal fun Packet.parseWeatherPositionless(options: ParseOptions) {
    type = PacketType.WX

    val bodyStr = bodyAsString().drop(1) // skip '_'

    if (bodyStr.length < 8) fail(ParseError.WX_INV, "positionless weather report too short")

    val wxData = bodyStr.drop(8) // skip 8-char MMDDHHMM timestamp
    val weather = Weather()
    wx = weather
    parseWeatherFields(wxData, weather)
}

// ── Weather from position comment (symbol code '_') ───────────────────────────

internal fun parseWeatherFromComment(comment: String, wxData: Weather): String {
    if (comment.length < 7) return ""

    var data = comment
    if (data[3] == '/') {
        val dirStr = data.substring(0, 3)
        val spdStr = data.substring(4, 7)

        val (dir, dn) = parseWxInt(dirStr, 3)
        if (dn > 0) wxData.windDirection = dir.toDouble()

        val (spd, sn) = parseWxInt(spdStr, 3)
        if (sn > 0) wxData.windSpeed = spd * 0.44704

        data = data.drop(7)
    }

    parseWeatherFields(data, wxData)
    return wxData.commentAfterWx
}

// ── Core weather field scanner ────────────────────────────────────────────────

private fun parseWeatherFields(data: String, wx: Weather) {
    var i = 0
    while (i < data.length) {
        if (i + 1 >= data.length) break

        var consumed = 0

        when (data[i]) {
            'c' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.windDirection = v.toDouble(); consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            's' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.windSpeed = v * 0.44704; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'g' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.windGust = v * 0.44704; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            't' -> {
                val (v, n) = parseWxSignedInt(data.substring(i + 1), 3)
                if (n > 0) { wx.temp = (v - 32.0) * 5.0 / 9.0; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'r' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.rain1h = v * 0.254; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'p' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.rain24h = v * 0.254; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'P' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.rainMidnight = v * 0.254; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'h' -> {
                var (v, n) = parseWxInt(data.substring(i + 1), 2)
                if (n > 0) { if (v == 0) v = 100; wx.humidity = v; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 2); if (s > 0) consumed = 1 + s }
            }
            'b' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 5)
                if (n > 0) { wx.pressure = v / 10.0; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 5); if (s > 0) consumed = 1 + s }
            }
            'L', 'l' -> {
                var (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) {
                    if (data[i] == 'l') v += 1000
                    wx.luminosity = v
                    consumed = 1 + n
                } else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'O' -> {
                if (i + 1 < data.length && data[i + 1] == 's') {
                    val (v, n) = parseWxInt(data.substring(i + 2), 3)
                    if (n > 0) { wx.snow24h = v * 0.254; consumed = 2 + n }
                    else { val s = skipWxField(data.substring(i + 2), 3); if (s > 0) consumed = 2 + s }
                }
            }
            'F' -> {
                val (v, n) = parseWxSignedInt(data.substring(i + 1), 4)
                if (n > 0) { wx.waterLevel = v / 10.0 * 0.3048; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 4); if (s > 0) consumed = 1 + s }
            }
            'X' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) {
                    val sig = v / 10
                    val exp = v % 10
                    wx.radiation = sig * 10.0.pow(exp.toDouble())
                    consumed = 1 + n
                } else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            'V' -> {
                val (v, n) = parseWxInt(data.substring(i + 1), 3)
                if (n > 0) { wx.batteryVoltage = v / 10.0; consumed = 1 + n }
                else { val s = skipWxField(data.substring(i + 1), 3); if (s > 0) consumed = 1 + s }
            }
            '#' -> {
                val s = skipWxField(data.substring(i + 1), 3)
                if (s > 0) consumed = 1 + s
                else {
                    val (_, n) = parseWxInt(data.substring(i + 1), 3)
                    if (n > 0) consumed = 1 + n
                }
            }
        }

        if (consumed > 0) {
            i += consumed
        } else {
            val remaining = data.substring(i).trim()
            if (isSoftwareID(remaining)) {
                wx.software = remaining
            } else {
                wx.commentAfterWx = remaining
            }
            return
        }
    }
}

internal fun isSoftwareID(s: String): Boolean {
    if (s.length < 3 || s.length > 5) return false
    return s.all { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() || it == '-' || it == '_' }
}

private fun skipWxField(s: String, maxLen: Int): Int {
    if (s.length < maxLen) return 0
    for (i in 0 until maxLen) {
        if (s[i] != '.' && s[i] != ' ') return 0
    }
    return maxLen
}

private fun parseWxInt(s: String, maxDigits: Int): Pair<Int, Int> {
    if (s.length < maxDigits) return Pair(0, 0)
    val field = s.substring(0, maxDigits)
    if (field.all { it == '.' || it == ' ' }) return Pair(0, 0)
    val v = field.trim().toIntOrNull() ?: return Pair(0, 0)
    return Pair(v, maxDigits)
}

private fun parseWxSignedInt(s: String, maxDigits: Int): Pair<Int, Int> {
    if (s.length < maxDigits) return Pair(0, 0)
    val field = s.substring(0, maxDigits)
    if (field.all { it == '.' || it == ' ' }) return Pair(0, 0)
    val v = field.trim().toIntOrNull() ?: return Pair(0, 0)
    return Pair(v, maxDigits)
}

// ── ULTW weather ($ULTW packets) ──────────────────────────────────────────────

internal fun Packet.parseULTW(options: ParseOptions) {
    type = PacketType.WX

    val body = bodyAsString().drop(5) // skip '$ULTW'
    val vals = parseULTWFields(body)
    if (vals.isEmpty()) fail(ParseError.WX_INV, "ULTW weather report has no data")

    val wx = Weather()

    ultwShift(vals)?.let { wx.windGust = ultwWindSpeed(it) }
    ultwShift(vals)?.let { wx.windDirection = ultwDirection(it) }
    ultwShift(vals)?.let { wx.temp = ultwTemp(it) }
    ultwShift(vals)?.let { v -> wx.rainMidnight = round(v * 0.254 * 10) / 10 }
    val pres = ultwShift(vals)
    if (pres != null && pres >= 10) wx.pressure = pres / 10.0
    ultwShift(vals); ultwShift(vals); ultwShift(vals) // skip baro delta, corr lsw, corr msw
    ultwShift(vals)?.let { v ->
        val h = v / 10
        if (h in 1..100) wx.humidity = h
    }
    ultwShift(vals); ultwShift(vals) // skip date, time
    ultwShift(vals)?.let { v -> wx.rainMidnight = round(v * 0.254 * 10) / 10 }
    ultwShift(vals)?.let { wx.windSpeed = ultwWindSpeed(it) }

    this.wx = wx
}

// ── ULTW logging (!! packets) ─────────────────────────────────────────────────

internal fun Packet.parseULTWLogging(options: ParseOptions) {
    type = PacketType.WX

    val body = bodyAsString().drop(2) // skip '!!'
    val vals = parseULTWFields(body)
    if (vals.isEmpty()) fail(ParseError.WX_INV, "ULTW logging weather report has no data")

    val wx = Weather()

    ultwShift(vals)?.let { wx.windSpeed = ultwWindSpeed(it) }
    ultwShift(vals)?.let { wx.windDirection = ultwDirection(it) }
    ultwShift(vals)?.let { wx.temp = ultwTemp(it) }
    ultwShift(vals)?.let { v -> wx.rainMidnight = round(v * 0.254 * 10) / 10 }
    val pres = ultwShift(vals)
    if (pres != null && pres >= 10) wx.pressure = pres / 10.0
    ultwShift(vals)?.let { wx.tempIn = ultwTemp(it) }
    ultwShift(vals)?.let { v ->
        val h = v / 10
        if (h in 1..100) wx.humidity = h
    }
    ultwShift(vals)?.let { v ->
        val h = v / 10
        if (h in 1..100) wx.humidityIn = h
    }
    ultwShift(vals); ultwShift(vals) // skip date, time
    ultwShift(vals)?.let { v -> wx.rainMidnight = round(v * 0.254 * 10) / 10 }
    ultwShift(vals)?.let { wx.windSpeed = ultwWindSpeed(it) }

    if (wx.tempIn != null && wx.temp == null) wx.temp = wx.tempIn

    this.wx = wx
}

// ── ULTW helpers ──────────────────────────────────────────────────────────────

private fun parseULTWFields(s: String): MutableList<Int?> {
    val vals = mutableListOf<Int?>()
    var data = s
    while (data.length >= 4) {
        val field = data.substring(0, 4)
        data = data.substring(4)
        if (field == "----") {
            vals.add(null)
            continue
        }
        val v64 = field.toULongOrNull(16) ?: break
        val v = if (v64 < 32768u) v64.toInt() else v64.toInt() - 65536
        vals.add(v)
    }
    return vals
}

private fun ultwShift(vals: MutableList<Int?>): Int? {
    if (vals.isEmpty()) return null
    return vals.removeAt(0)
}

private fun ultwWindSpeed(v: Int): Double = round(v / 36.0 * 10) / 10

private fun ultwDirection(v: Int): Double = round((v and 0xFF) * 1.41176)

private fun ultwTemp(v: Int): Double {
    val f = v / 10.0
    val c = (f - 32.0) / 1.8
    return round(c * 10) / 10
}
