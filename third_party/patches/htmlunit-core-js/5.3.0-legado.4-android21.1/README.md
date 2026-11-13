# HtmlUnit Core JS Android 21 patch

`SlotMapOwner.java` comes from
`mgz0227/htmlunit-rhino-fork@76460c0312bfd351df6f2bb11168102cdb54170a`,
relocated to `org.htmlunit.corejs.javascript` like the packaged engine.

The only behavioral edit is inside `ThreadedAccess.checkAndReplaceMap`: the
API-26 `VarHandle.compareAndExchange` call is implemented with synchronization
on the owner. Rhino's HtmlUnit configuration does not select thread-safe slot
maps, so this preserves the dormant compatibility path without changing normal
script execution.

Rebuild with JDK 17 by compiling this source against the original `.4` JAR,
then replacing every generated `SlotMapOwner*.class` entry in a copy of that
JAR. The Maven artifact version must remain
`5.3.0-legado.4-android21.1`; verify the final SHA-256 against `SOURCE.md`.
