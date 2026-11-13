# Android 21 compatibility patch

Upstream `SlotMapOwner.ThreadedAccess` uses `VarHandle.compareAndExchange` and
explicitly notes that the class prevents dexing below API 26. This application
does not enable Rhino thread-safe objects, but D8 still validates the unused
class.

The bundled Android artifact replaces only that helper with an
owner-synchronized compare-and-exchange. All JavaScript parsing, execution,
Java interop and regular-expression implementation classes remain byte-for-byte
from `htmlunit-core-js 5.3.0-legado.4`.

Modified source:
`third_party/patches/htmlunit-core-js/5.3.0-legado.4-android21.1/SlotMapOwner.java`.
