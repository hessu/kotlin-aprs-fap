package fi.aprs.fap

import kotlin.math.*

/** Calculates the great-circle distance in kilometers between two points in decimal degrees. */
fun distance(lat0: Double, lon0: Double, lat1: Double, lon1: Double): Double {
    val lat0r = lat0 * PI / 180.0
    val lon0r = lon0 * PI / 180.0
    val lat1r = lat1 * PI / 180.0
    val lon1r = lon1 * PI / 180.0

    val dlon = lon1r - lon0r
    val dlat = lat1r - lat0r

    val a = sin(dlat / 2).pow(2) + cos(lat0r) * cos(lat1r) * sin(dlon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return 6366.71 * c
}

/** Calculates the initial bearing in degrees from point 0 to point 1 (both in decimal degrees). */
fun direction(lat0: Double, lon0: Double, lat1: Double, lon1: Double): Double {
    val lat0r = lat0 * PI / 180.0
    val lon0r = lon0 * PI / 180.0
    val lat1r = lat1 * PI / 180.0
    val lon1r = lon1 * PI / 180.0

    val dlon = lon1r - lon0r

    var dir = atan2(
        sin(dlon) * cos(lat1r),
        cos(lat0r) * sin(lat1r) - sin(lat0r) * cos(lat1r) * cos(dlon)
    ) * 180.0 / PI

    if (dir < 0) dir += 360.0

    return dir
}
