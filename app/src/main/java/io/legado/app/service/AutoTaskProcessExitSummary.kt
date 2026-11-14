package io.legado.app.service

/** Pure, bounded formatting for fields returned by Android's process-exit API. */
internal object AutoTaskProcessExitSummary {
    fun formatStart(sdk: Int, manufacturer: String, model: String, version: String, pid: Int): String =
        "sdk=$sdk manufacturer=${field(manufacturer)} model=${field(model)} version=${field(version)} pid=$pid"

    fun formatExit(
        timestamp: Long,
        pid: Int,
        reason: Int,
        reasonName: String,
        status: Int,
        importance: Int,
        pssKb: Long,
        rssKb: Long,
        sinceExitMs: Long,
    ): String = "timestamp=$timestamp pid=$pid reason=$reason reasonName=${field(reasonName)} " +
        "status=$status importance=$importance pssKb=$pssKb rssKb=$rssKb sinceExitMs=$sinceExitMs"

    private fun field(value: String): String = value.take(MAX_FIELD_LENGTH).map { char ->
        if (char.isISOControl() || char == '\u2028' || char == '\u2029') ' ' else char
    }.joinToString("")

    private const val MAX_FIELD_LENGTH = 80
}
