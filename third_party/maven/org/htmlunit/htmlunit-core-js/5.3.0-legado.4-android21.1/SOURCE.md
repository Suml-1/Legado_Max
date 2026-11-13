# htmlunit-core-js 5.3.0-legado.4-android21.1

This Android artifact derives from the following public source revisions:

- Packaging: `mgz0227/htmlunit-core-js@3eb5071cdca4a357119d1063a53d5f2d47984ed2`
- Rhino: `mgz0227/htmlunit-rhino-fork@76460c0312bfd351df6f2bb11168102cdb54170a`

The original JAR is `htmlunit-core-js-5.3.0-legado.4.jar`, SHA-256:

```text
d720f34285515025e4ccc80a5b92aed42cb26c03a7715ca53583d2c74ae51df7
```

For Android API 21 compatibility, only the seven compiled `SlotMapOwner`
classes are replaced. The pinned source's unused `ThreadedAccess` helper uses
`VarHandle.compareAndExchange`, which D8 rejects below API 26. The replacement
performs the same compare-and-exchange while synchronized on the owner.

Modified source and reproduction notes:
`third_party/patches/htmlunit-core-js/5.3.0-legado.4-android21.1/`.

SHA-256 of the patched source:

```text
a3cd54152645d28eb30e63911f9cf9e2f5925612103548e9cc96acddca1f976e
```

SHA-256 of `htmlunit-core-js-5.3.0-legado.4-android21.1.jar`:

```text
56f4c96756548f490955d87fb0b46e3288fb33a4cd80497962ca6e77bd61ea26
```

License texts and notices are bundled under
`app/src/main/assets/licenses/htmlunit-core-js/5.3.0-legado.4-android21.1/`.
