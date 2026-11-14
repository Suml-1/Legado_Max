# JavaScript Packages 使用指南
> ⚠️ 注意：文档可能不精准，如有错误或缺少，请提交issue或Pull Request

> 在书源规则中使用 `@js` `<js>` `{{}}` 可使用 JavaScript 调用 Java 类和方法

## 重要说明

**注意** **`java`** **变量指向已经被阅读修改，如果想要调用** **`java.*`** **下的包，请使用** **`Packages.java.*`**

在书源规则中，`java` 变量已经被重新定义为当前类对象，用于调用阅读 App 内置的方法。因此，要访问标准的 Java 类，必须使用 `Packages` 前缀。

## 可以使用的 Packages

### 1. Java 标准库（部分可用）

#### 字符串和基础类型

```javascript
Packages.java.lang.String
Packages.java.lang.Integer
Packages.java.lang.Long
Packages.java.lang.Double
Packages.java.lang.Boolean
Packages.java.lang.Math
Packages.java.lang.Thread  // 可用于 sleep延时 等操作
Packages.java.util.ArrayList
Packages.java.util.HashMap
Packages.java.util.Collections
Packages.java.util.Arrays
Packages.java.util.regex.Pattern
Packages.java.util.regex.Matcher
```

#### IO 流（注意：File 相关被屏蔽）

```javascript
Packages.java.io.ByteArrayInputStream
Packages.java.io.ByteArrayOutputStream
Packages.java.io.InputStream
Packages.java.io.OutputStream
```

#### 网络相关

```javascript
Packages.java.net.URLEncoder
Packages.java.net.URLDecoder
Packages.java.net.HttpURLConnection
```

#### 加密相关

```javascript
Packages.javax.crypto.Mac
Packages.javax.crypto.Cipher
Packages.javax.crypto.spec.SecretKeySpec
Packages.javax.crypto.spec.IvParameterSpec
```

### 2. 第三方库

#### Jsoup HTML 解析器

```javascript
Packages.org.jsoup.Jsoup
Packages.org.jsoup.nodes.Element
Packages.org.jsoup.nodes.Document
Packages.org.jsoup.select.Elements
Packages.org.jsoup.Connection
```

#### JsonPath JSON 解析器

```javascript
Packages.com.jayway.jsonpath.JsonPath
Packages.com.jayway.jsonpath.Configuration
Packages.com.jayway.jsonpath.Option
```

### 3. Android 工具类

```javascript
Packages.android.util.Base64
Packages.android.text.TextUtils
```

### 4. Legado 内部类（部分可用）

```javascript
Packages.io.legado.app.api.ReturnData
Packages.io.legado.app.data.entities.Book
Packages.io.legado.app.data.entities.BookChapter
```

## 被屏蔽的 Packages（不可使用）

为了安全，阅读会屏蔽部分 Java 类调用，详见 [RhinoClassShutter](https://github.com/gedoor/legado/blob/master/modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt)

### 1. 文件系统相关

```javascript
Packages.java.io.File              // ❌ 禁止
Packages.java.io.FileInputStream   // ❌ 禁止
Packages.java.io.FileOutputStream  // ❌ 禁止
Packages.java.nio.file.Files       // ❌ 禁止
Packages.java.nio.file.Paths       // ❌ 禁止
```

### 2. 系统执行相关

```javascript
Packages.java.lang.Runtime         // ❌ 禁止
Packages.java.lang.ProcessBuilder  // ❌ 禁止
Packages.java.lang.ClassLoader     // ❌ 禁止
Packages.java.lang.ProcessImpl     // ❌ 禁止
```

### 3. 反射相关

```javascript
Packages.java.lang.reflect         // ❌ 禁止
Packages.java.lang.invoke          // ❌ 禁止
```

### 4. Android 敏感类

```javascript
Packages.android.content.Intent    // ❌ 禁止
Packages.android.os.Looper         // ❌ 禁止
Packages.android.os.Process        // ❌ 禁止
Packages.android.database          // ❌ 禁止
Packages.dalvik.system             // ❌ 禁止
Packages.android.provider.Settings // ❌ 禁止
```

### 5. 数据库相关

```javascript
Packages.androidx.room             // ❌ 禁止
Packages.io.legado.app.data.dao    // ❌ 禁止
```

### 6. Rhino 引擎相关

```javascript
Packages.org.htmlunit.corejs       // ❌ 禁止（当前引擎实现，禁止脚本直接构造运行时）
Packages.org.mozilla               // ❌ 禁止（旧引擎包名，仍在屏蔽名单内）
Packages.com.script                // ❌ 禁止
```

### 7. 其他被屏蔽的类

```javascript
Packages.java.security.AccessController  // ❌ 禁止
Packages.sun.misc.Unsafe                 // ❌ 禁止
Packages.cn.hutool.core.io               // ❌ 禁止
Packages.cn.hutool.core.bean             // ❌ 禁止
```

## 使用方式

### 方式一：直接使用 Packages.类名

```javascript
var input = new Packages.java.io.ByteArrayInputStream(data)
var output = new Packages.java.io.ByteArrayOutputStream()
var encoded = Packages.java.net.URLEncoder.encode(text, "UTF-8")
```

### 方式二：使用 JavaImporter 批量导入

```javascript
var aly = new JavaImporter(
  Packages.org.jsoup.Jsoup,
  Packages.org.jsoup.nodes.Element,
  Packages.org.jsoup.select.Elements
);
with (aly) {
  // 在这里可以直接使用 Jsoup、Element、Elements 等
  var doc = Jsoup.parse(html);
  var links = doc.select("a[href]");
}
```

## 实际使用示例

### 示例 1：使用 Jsoup 解析 HTML

```javascript
var aly = new JavaImporter(
  Packages.org.jsoup.Jsoup,
  Packages.org.jsoup.nodes.Element
);
with (aly) {
  var doc = Jsoup.parse(html);
  var links = doc.select("a[href]");
  for (var i = 0; i < links.size(); i++) {
    var link = links.get(i);
    var href = link.attr("href");
    var text = link.text();
  }
}
```

### 示例 2：使用加密算法（HMAC-SHA1）

```javascript
var aly = new JavaImporter(
  Packages.javax.crypto.Mac,
  Packages.javax.crypto.spec.SecretKeySpec,
  Packages.android.util.Base64
);
with (aly) {
  var mac = Mac.getInstance('HmacSHA1');
  mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA1"));
  var signData = mac.doFinal(data.getBytes("UTF-8"));
  var signBase64 = Base64.encodeToString(signData, Base64.NO_WRAP);
}
```

### 示例 3：使用 JsonPath 解析 JSON

```javascript
var aly = new JavaImporter(Packages.com.jayway.jsonpath);
with (aly) {
  var jsonPath = JsonPath.using(
    Configuration.builder()
      .options(Option.SUPPRESS_EXCEPTIONS)
      .build()
  ).parse(jsonString);
  var value = jsonPath.read('$.data.value');
  var items = jsonPath.read('$.data.items[*]');
}
```

### 示例 4：使用 IO 流处理数据（图片解密）

```javascript
function decodeImage(data, key) {
  var input = new Packages.java.io.ByteArrayInputStream(data);
  var output = new Packages.java.io.ByteArrayOutputStream();
  var byte;
  while ((byte = input.read()) != -1) {
    output.write(byte ^ key);
  }
  return output.toByteArray();
}

var decodedImage = decodeImage(result, key);
```

### 示例 5：使用正则表达式

```javascript
var pattern = Packages.java.util.regex.Pattern.compile("\\d+");
var matcher = pattern.matcher(text);
while (matcher.find()) {
  var number = matcher.group();
}
```

### 示例 6：使用集合类

```javascript
var list = new Packages.java.util.ArrayList();
list.add("item1");
list.add("item2");
var size = list.size();

var map = new Packages.java.util.HashMap();
map.put("key1", "value1");
var value = map.get("key1");

// 使用 Collections 工具类
Packages.java.util.Collections.sort(list);
Packages.java.util.Collections.reverse(list);
```

**实际应用：反转 Jsoup Elements 顺序**

```javascript
// Jsoup 的 Elements 实现了 List 接口，可以直接使用 Collections.reverse()
var html = java.ajax(url);
var doc = Packages.org.jsoup.Jsoup.parse(html);
var elements = doc.select("a.link");  // Elements 实现了 List 接口

// 直接反转元素顺序
Packages.java.util.Collections.reverse(elements);

// 现在遍历时顺序已反转
for (var i = 0; i < elements.size(); i++) {
  var element = elements.get(i);
  // 处理元素
}
```

**关键说明：**

1. **Jsoup Elements 实现了 List 接口**：Jsoup 的 `Elements` 类实现了 `java.util.List<Element>` 接口
2. **可以直接使用 Collections 工具类**：因为实现了 List 接口，所以可以直接使用 `Collections.sort()`、`Collections.reverse()` 等方法
3. **无需转换**：不需要先将 Elements 转换成 ArrayList，可以直接操作 Elements

### 示例 7：使用 Base64 编码解码

```javascript
// Android Base64
var encoded = Packages.android.util.Base64.encodeToString(
  data.getBytes("UTF-8"), 
  Packages.android.util.Base64.NO_WRAP
);
var decoded = Packages.android.util.Base64.decode(
  encoded, 
  Packages.android.util.Base64.NO_WRAP
);
```

### 示例 8：URL 编码解码

```javascript
var encoded = Packages.java.net.URLEncoder.encode(text, "UTF-8");
var decoded = Packages.java.net.URLDecoder.decode(encoded, "UTF-8");
```

### 示例 9：使用 Thread.sleep() 延时

```javascript
// 在需要等待的场景中使用 sleep
// 例如：等待浏览器加载完成、等待网络请求等

// 简单延时 100 毫秒
Packages.java.lang.Thread.sleep(100);

// 实际应用示例：打开浏览器后等待一段时间再获取 Cookie
java.startBrowserAwait(url, '登录');
Packages.java.lang.Thread.sleep(50);  // 等待 50 毫秒
var ck = cookie.getCookie(url) + '';

// 在循环中使用延时
for (var i = 0; i < 10; i++) {
  // 执行某些操作
  doSomething();
  // 每次循环后等待 200 毫秒
  Packages.java.lang.Thread.sleep(200);
}
```

## 注意事项

1. **安全限制**：为了安全，阅读会屏蔽部分 Java 类调用，如果尝试使用被屏蔽的类会抛出异常
2. **性能考虑**：频繁创建 Java 对象会影响性能，尽量复用对象
3. **异常处理**：建议使用 try-catch 包裹可能出错的代码
   ```javascript
   try {
     var result = someJavaOperation();
   } catch (e) {
     java.log("Error: " + e);
   }
   ```
4. **变量声明**：注意使用 `const` 声明的变量不支持块级作用域，在循环里使用会出现值不变的问题，请改用 `var` 声明
5. **内存管理**：大对象使用完毕后及时置为 null，帮助垃圾回收
6. **编码问题**：处理字符串时注意指定编码，通常使用 "UTF-8"

## 相关文档

- [jsHelp.md](jsHelp.md) - JavaScript 变量和函数
- [ruleHelp.md](ruleHelp.md) - 规则帮助
- [RhinoClassShutter](https://github.com/gedoor/legado/blob/master/modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt) - 类访问控制源码

