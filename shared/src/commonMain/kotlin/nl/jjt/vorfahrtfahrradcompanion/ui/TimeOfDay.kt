package nl.jjt.vorfahrtfahrradcompanion.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** [this] as `HH:mm` where the rider is — what an instant looks like when it is shown to a person. */
fun Instant.timeOfDay(): String {
    val time = toLocalDateTime(TimeZone.currentSystemDefault()).time
    return "${time.hour.pad()}:${time.minute.pad()}"
}

private fun Int.pad() = toString().padStart(2, '0')
