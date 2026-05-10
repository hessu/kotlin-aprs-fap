package fi.aprs.fap

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
