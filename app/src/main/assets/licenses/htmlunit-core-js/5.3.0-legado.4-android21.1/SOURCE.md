# htmlunit-core-js 5.3.0-legado.4-android21.1

This binary is built from the following public source revisions:

- Packaging: `mgz0227/htmlunit-core-js@3eb5071cdca4a357119d1063a53d5f2d47984ed2`
- Rhino: `mgz0227/htmlunit-rhino-fork@76460c0312bfd351df6f2bb11168102cdb54170a`

`rhinoDiff.txt` records the Rhino changes from upstream merge base
`46d0904a3b4a1adc014a0d53d66a91b699a548de` to the pinned fork revision.

Build command:

```shell
mvn --batch-mode -T 1 -U clean install -Dmaven.test.skip=true -Dgpg.skip=true -Dmaven.javadoc.skip=true -Dmaven.compiler.showWarnings=false
```

The Android artifact starts from `htmlunit-core-js-5.3.0-legado.4.jar` and
rebuilds only `SlotMapOwner` from the pinned Rhino source. Its unused
`ThreadedAccess` VarHandle compare-and-exchange is replaced with an equivalent
owner-synchronized compare-and-exchange so D8 can target API 21. The modified
source is shipped under
`third_party/patches/htmlunit-core-js/5.3.0-legado.4-android21.1/`.

SHA-256 of the original `htmlunit-core-js-5.3.0-legado.4.jar`:

```text
d720f34285515025e4ccc80a5b92aed42cb26c03a7715ca53583d2c74ae51df7
```

SHA-256 of `htmlunit-core-js-5.3.0-legado.4-android21.1.jar`:

```text
56f4c96756548f490955d87fb0b46e3288fb33a4cd80497962ca6e77bd61ea26
```

Licensing records from both source revisions are bundled in this APK asset
directory and preserved in the source tree:

- The packaging revision declares Apache License 2.0 in its POM, while its
  `LICENSE.txt` states that the generated JavaScript engine is provided under
  MPL 2.0.
- The Rhino revision is MPL 2.0 and includes additional third-party notices.
- `LICENSE-core-js.txt`, `LICENSE.txt`, `LICENSE-APACHE-2.0.txt`, `NOTICE.txt`,
  `NOTICE-tools.txt`, and `rhinoDiff.txt` contain those original records,
  referenced license texts, and source changes. The JAR itself does not embed
  them; Android packaging includes this directory unchanged in the APK assets.

The corresponding upstream and modified sources remain available from the
exact revisions and source-tree path above.
