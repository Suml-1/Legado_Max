# Rhino JavaScript 引擎缺陷记录

> 本文档记录 **Mozilla Rhino 1.8.1（旧引擎）** 在 Legado 环境中存在的已知缺陷，供开发者参考和规避。不一定完全正确，有错误请提交 issue

> 本分支自 2026-11-13 起引擎已切换为 [HtmlUnit Rhino 分支（htmlunit-core-js 5.3.0-legado.4）](https://github.com/mgx0227/htmlunit-rhino-fork)。各缺陷在新引擎上的实测结论见下方「总结」表：标注**仍存在**的项仍需按规避方案编写，其余可以放心使用现代 ES6+ 写法。

---

## 缺陷一：循环中 const 定义的过程变量不能释放

### 缺陷描述

在 `for` 循环中使用 `const` 声明变量时，Rhino 引擎不会在每次迭代后释放该变量的内存，导致变量持续累积，无法被垃圾回收。

### 问题代码

```javascript
for (var i = 0; i < 5; i++) {
    const x = i * 2;
    java.log(x);
}
```

### 正常输出（预期，htmlunit-core-js引擎和quickjs引擎实测可以正常输出）

```
0
2
4
6
8
```

### 错误输出（实际）

```
0
0
0
0
0
```

> 注：Rhino 的  const  实现基于早期 ES 草案，是函数作用域而非块作用域，循环体不会为  const  创建新的块级绑定，导致每次迭代复用同一个变量绑定。来自Rhino github仓库 issue

### 规避方案

使用 `let` 替代 `const`，或将变量声明移至循环外部：

```javascript
// 方案一：使用 let
for (var i = 0; i < 5; i++) {
    let x = i * 2;
    java.log(x);
}

// 方案二：外部声明
let x;
for (var i = 0; i < 5; i++) {
    x = i * 2;
    java.log(x);
}
```

---

## 缺陷二：const 变量可被重新赋值

### 缺陷描述

`const` 声明的变量应当不可重新赋值，但 Rhino 引擎在某些情况下允许对 `const` 变量进行重新赋值，违反了 ES6 规范。

### 问题代码

```javascript
const PI = 3.14159;
PI = 3.14;  // 应当报错
java.log(PI);
```

### 正常输出（预期）

```
TypeError: Assignment to constant variable.
```

### 错误输出（实际）

```
3.14
```

> Rhino 引擎未正确实现 `const` 的不可变性，导致常量被静默修改。

### 规避方案

使用 `Object.freeze()` 或避免使用 `const`：

```javascript
const PI = Object.freeze({ value: 3.14159 });
// PI.value = 3.14;  // 严格模式下会报错
java.log(PI.value);
```

---

## 缺陷三：let 变量存在变量提升问题

### 缺陷描述

`let` 声明的变量应当存在暂时性死区（TDZ），在声明前访问应报错。但 Rhino 引擎对 `let` 的变量提升处理不符合规范，可能导致意外行为。

### 问题代码

```javascript
function test() {
    java.log(x);  // 应当报错
    let x = 10;
}
test();
```

### 正常输出（预期）

```
ReferenceError: Cannot access 'x' before initialization
```

### 错误输出（实际）

```
undefined
```

> Rhino 引擎将 `let` 变量提升并初始化为 `undefined`，而非保持暂时性死区状态。

### 规避方案

始终在函数或块级作用域顶部声明 `let` 变量：

```javascript
function test() {
    let x = 10;
    java.log(x);  // 正确输出: 10
}
test();
```

---

## 缺陷四：for...in 循环遍历顺序不固定

### 缺陷描述

`for...in` 循环遍历对象属性时，Rhino 引擎不保证属性遍历顺序与插入顺序一致，尤其在数字键和字符串键混合时。

### 问题代码

```javascript
const obj = {};
obj.a = 1;
obj.b = 2;
obj[0] = 3;
obj.c = 4;

for (let key in obj) {
    java.log(key + ': ' + obj[key]);
}
```

### 正常输出（预期）

```
a: 1
b: 2
0: 3
c: 4
```

### 错误输出（实际）

```
0: 3
a: 1
b: 2
c: 4
```

> 数字键 `0` 被提前遍历，与插入顺序不一致。

### 规避方案

使用 `Map` 或数组保证顺序：

```javascript
const map = new Map();
map.set('a', 1);
map.set('b', 2);
map.set(0, 3);
map.set('c', 4);

for (let [key, value] of map) {
    java.log(key + ': ' + value);
}
```

---

## 缺陷五：JSON.parse 对某些字符处理异常

### 缺陷描述

`JSON.parse` 在处理包含特殊 Unicode 字符或转义序列的字符串时，可能抛出异常或返回错误结果。

### 问题代码

```javascript
const json = '{"name": "测试\\u4e2d\\u6587"}';
const obj = JSON.parse(json);
java.log(obj.name);
```

### 正常输出（预期）

```
测试中文
```

### 错误输出（实际）

```
SyntaxError: Unexpected token u in JSON
```

> Rhino 的 JSON 解析器对 Unicode 转义序列支持不完整。

### 规避方案

手动处理 Unicode 转义：

```javascript
function parseUnicode(json) {
    return json.replace(/\\u([\d\w]{4})/gi, function(match, grp) {
        return String.fromCharCode(parseInt(grp, 16));
    });
}

const json = '{"name": "测试\\u4e2d\\u6587"}';
const obj = JSON.parse(parseUnicode(json));
java.log(obj.name);
```

---

## 缺陷六：Array.prototype.includes 方法不存在

### 缺陷描述

Rhino 引擎未实现 ES7 的 `Array.prototype.includes` 方法，调用时会抛出 `TypeError`。

### 问题代码

```javascript
const arr = [1, 2, 3, 4, 5];
java.log(arr.includes(3));
```

### 正常输出（预期）

```
true
```

### 错误输出（实际）

```
TypeError: arr.includes is not a function
```

### 规避方案

使用 `indexOf` 替代：

```javascript
const arr = [1, 2, 3, 4, 5];
java.log(arr.indexOf(3) !== -1);  // true
```

---

## 缺陷七：Promise 微任务队列异常

### 缺陷描述

Rhino 引擎对 `Promise` 的支持不完整，微任务队列的执行时机可能与规范不一致，导致异步代码执行顺序异常。

### 问题代码

```javascript
java.log('1');
new Promise((resolve) => {
    java.log('2');
    resolve();
}).then(() => {
    java.log('3');
});
java.log('4');
```

### 正常输出（预期）

```
1
2
4
3
```

### 错误输出（实际）

```
1
2
3
4
```

> Promise 的 `then` 回调被同步执行，而非作为微任务在同步代码之后执行。

### 规避方案

使用 `setTimeout` 模拟异步行为：

```javascript
java.log('1');
setTimeout(() => {
    new Promise((resolve) => {
        java.log('2');
        resolve();
    }).then(() => {
        java.log('3');
    });
    java.log('4');
}, 0);
```

---

## 缺陷八：模板字符串嵌套解析错误

### 缺陷描述

Rhino 引擎对模板字符串的嵌套解析存在问题，复杂表达式可能无法正确求值。

### 问题代码

```javascript
const name = 'World';
const msg = `Hello ${`${name}!`}`;
java.log(msg);
```

### 正常输出（预期）

```
Hello World!
```

### 错误输出（实际）

```
SyntaxError: Unexpected token
```

### 规避方案

避免嵌套模板字符串，使用变量中转：

```javascript
const name = 'World';
const inner = `${name}!`;
const msg = `Hello ${inner}`;
java.log(msg);
```

---

## 总结

| 缺陷 | 旧引擎严重程度 | 规避方案 | htmlunit-core-js 实测（2026-11-14） |
|------|----------|----------|----------|
| const 变量不能释放 | 中 | 使用 let 或外部声明 | 循环内 `const` 未报错；内存是否累积无法用脚本返回值判定，未复测 |
| const 可被重新赋值 | 高 | 使用 Object.freeze | **仍存在**：`const c = 1; c = 2` 不抛错，读到 2 |
| let 变量提升问题 | 中 | 顶部声明变量 | **仍存在**：`typeof y` 先于 `let y` 返回 `undefined`，未抛 TDZ 错误 |
| for...in 顺序不固定 | 低 | 使用 Map | 未见问题：`{b,2,a,1}` 遍历为 `12ba`（整数键升序 + 其余插入序，符合 ES2015+） |
| JSON.parse Unicode 异常 | 中 | 手动处理转义 | 已修复：`JSON.parse("\u4e2d")` 正常得到 0x4E2D |
| Array.includes 不存在 | 低 | 使用 indexOf | 已修复：`[1,2].includes(2)` 返回 `true` |
| Promise 微任务异常 | 高 | 使用 setTimeout | 未见问题：同步代码先于 `.then` 执行（`sp` 顺序正确） |
| 模板字符串嵌套错误 | 低 | 避免嵌套 | 已修复：`` `x${`y${a}z`}w` `` 得到 `xy1zw` |

> 附加实测：可选链 `?.` 与空值合并 `??` 可用（`({a:{b:2}})?.a?.b ?? 0` → `2`，`null ?? 5` → `5`）。

> 实测方式：注入一个 JS 单文件书源，让 `search()` 把每项自检结果拼成书名返回，在 R8 release 包的搜索结果列表里直接读取（2026-11-14）。

---

*文档更新时间：2026-11-14（引擎切换后复测）*
*适用版本：Legado Max（正文缺陷部分对应旧引擎 Mozilla Rhino 1.8.1；「实测」列对应 htmlunit-core-js 5.3.0-legado.4）*