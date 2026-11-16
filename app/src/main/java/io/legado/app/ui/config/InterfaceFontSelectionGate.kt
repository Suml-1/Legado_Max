package io.legado.app.ui.config

/** Only the latest explicit selection may publish preferences; decoding stays outside this lock. */
internal class InterfaceFontSelectionGate {
    private var request = 0L

    @Synchronized fun nextRequest(): Long = ++request
    @Synchronized fun invalidate() { request++ }
    @Synchronized fun isCurrent(id: Long): Boolean = id == request

    @Synchronized fun commitIfCurrent(id: Long, action: () -> Unit): Boolean {
        if (id != request) return false
        action()
        return true
    }
}
