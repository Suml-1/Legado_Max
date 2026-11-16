package io.legado.app.help.plugin

import java.io.DataInputStream
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipFile

/**
 * Checks the DEX container and entry ABI without defining or initializing any class.
 * ART still owns instruction verification and runtime failures when the user chooses to run a compiled package.
 * Format: https://source.android.com/docs/core/runtime/dex-format (standard DEX 035–040).
 */
internal object CompiledPackageInspector {
    private const val MAX_DEX_BYTES = 64 * 1024 * 1024
    private val dexName = Regex("classes(?:[0-9]+)?\\.dex")

    fun validate(archive: ZipFile, entryClass: String, apiDescriptor: String, apiNamespace: String) {
        val entries = archive.entries().asSequence().filter { dexName.matches(it.name) }.toList()
        require(entries.isNotEmpty() && entries.map { it.name }.toSet().size == entries.size) { "编译包缺少或重复编译代码" }
        val expected = (1..entries.size).map { if (it == 1) "classes.dex" else "classes$it.dex" }.toSet()
        require(entries.map { it.name }.toSet() == expected) { "编译包 DEX 编号无效" }
        val definitions = mutableMapOf<String, Definition>()
        val references = mutableSetOf<String>()
        var total = 0L
        for (entry in entries) {
            require(!entry.isDirectory && entry.size in 112..MAX_DEX_BYTES.toLong()) { "编译包 DEX 大小无效" }
            total += entry.size
            require(total <= 128L * 1024 * 1024) { "编译包 DEX 内容过大" }
            val bytes = ByteArray(entry.size.toInt())
            DataInputStream(archive.getInputStream(entry)).use {
                it.readFully(bytes)
                require(it.read() == -1) { "编译包 DEX 长度不一致" }
            }
            val parsed = Reader(bytes).read()
            for ((name, definition) in parsed.first) {
                require(definitions.put(name, definition) == null) { "编译包 DEX 含重复类定义" }
            }
            references.addAll(parsed.second)
        }
        require(definitions.keys.none { it.startsWith(apiNamespace) }) { "编译包重复包含宿主接口" }
        require(references.none {
            val name = it.trimStart('[')
            name.startsWith("Lio/legado/app/") && name !in definitions
        }) { "编译包依赖宿主内部类型" }
        val entry = "L" + entryClass.replace('.', '/') + ";"
        val definition = definitions[entry] ?: error("编译包入口类不存在")
        require(definition.flags and 0x601 == 1) { "编译包入口必须为公开具体类" }
        require(definition.publicConstructor) { "编译包入口缺少公开无参构造" }
        for (item in definitions.values) {
            item.superclass?.let { parent ->
                require(parent != apiDescriptor && definitions[parent]?.let { it.flags and 0x200 == 0 } != false) { "编译包 DEX 父类类型无效" }
            }
            for (parent in item.interfaces) {
                require(definitions[parent]?.let { it.flags and 0x200 != 0 } != false) { "编译包 DEX 接口类型无效" }
            }
        }
        // Walk all local inheritance graphs iteratively, including cross-DEX edges.
        val completed = mutableSetOf<String>()
        for (name in definitions.keys) {
            if (name in completed) continue
            val active = mutableSetOf(name)
            val stack = ArrayDeque<Pair<String, Iterator<String>>>()
            stack.addLast(name to definitions.getValue(name).parents().iterator())
            while (stack.isNotEmpty()) {
                val (current, parents) = stack.last()
                if (!parents.hasNext()) {
                    stack.removeLast()
                    active.remove(current)
                    completed.add(current)
                    continue
                }
                val parent = parents.next()
                require(parent !in active) { "编译包 DEX 继承关系循环" }
                if (parent !in completed && parent in definitions) {
                    active.add(parent)
                    stack.addLast(parent to definitions.getValue(parent).parents().iterator())
                }
            }
        }
        val reachable = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(entry)
        while (pending.isNotEmpty()) {
            val name = pending.removeLast()
            if (reachable.add(name)) definitions[name]?.parents()?.forEach(pending::addLast)
        }
        require(apiDescriptor in reachable) { "编译包入口未实现指定接口" }
    }

    private data class Definition(
        val flags: Int,
        val superclass: String?,
        val interfaces: List<String>,
        val publicConstructor: Boolean,
    ) {
        fun parents(): Sequence<String> = sequence {
            superclass?.let { yield(it) }
            yieldAll(interfaces)
        }
    }

    private data class Section(val count: Int, val start: Int, val end: Int)
    private data class Proto(val result: String, val parameters: Int, val words: Int)
    private data class Method(val owner: Int, val proto: Int, val name: String)
    private data class Code(val incoming: Int)

    private class Reader(private val bytes: ByteArray) {
        private val sections = mutableMapOf<Int, Section>()
        private val strings = mutableMapOf<Int, String>()
        private val lists = mutableMapOf<Int, List<Int>>()
        private val codes = mutableMapOf<Int, Code>()
        private val interfaceLists = mutableMapOf<Int, List<String>>()
        private var work = minOf(bytes.size.toLong() * 8, 32L * 1024 * 1024)
        private var stringsCount = 0
        private var stringsAt = 0
        private var fieldsCount = 0
        private var fieldsAt = 0
        private lateinit var types: List<String>
        private lateinit var protos: List<Proto>
        private lateinit var methods: List<Method>

        private fun spend(count: Int) {
            work -= count.toLong()
            require(work >= 0) { "编译包 DEX 元数据过于复杂" }
        }

        private fun bounds(offset: Int, length: Long, end: Int = bytes.size) {
            require(offset >= 0 && length >= 0 && offset.toLong() + length <= end.toLong()) { "编译包 DEX 数据越界" }
        }

        private fun u16(offset: Int): Int {
            bounds(offset, 2)
            return (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        }

        private fun u32(offset: Int): Long {
            bounds(offset, 4)
            return (0..3).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 255L) shl (i * 8)) }
        }

        private fun number(offset: Int): Int {
            val value = u32(offset)
            require(value <= Int.MAX_VALUE) { "编译包 DEX 数值越界" }
            return value.toInt()
        }

        private fun data(type: Int, offset: Int, minimum: Int = 1, alignment: Int = 1): Section {
            val section = sections[type] ?: error("编译包 DEX 缺少数据区")
            require(offset >= section.start && offset % alignment == 0) { "编译包 DEX 数据偏移无效" }
            bounds(offset, minimum.toLong(), section.end)
            return section
        }

        fun read(): Pair<Map<String, Definition>, List<String>> {
            require(bytes.size >= 112 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10)) &&
                bytes[7] == 0.toByte() && String(bytes, 4, 3, Charsets.US_ASCII) in setOf("035", "037", "038", "039", "040")
            ) { "编译包 DEX 版本无效" }
            require(number(32) == bytes.size && number(36) == 112 && u32(40) == 0x12345678L) { "编译包 DEX 文件头无效" }
            val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }
            require(adler.value == u32(8)) { "编译包 DEX 校验和错误" }
            val sha = MessageDigest.getInstance("SHA-1").apply { update(bytes, 32, bytes.size - 32) }.digest()
            require(sha.contentEquals(bytes.copyOfRange(12, 32))) { "编译包 DEX 签名校验失败" }
            val linkSize = number(44)
            val linkAt = number(48)
            require((linkSize == 0) == (linkAt == 0)) { "编译包 DEX 链接区无效" }
            if (linkSize != 0) { require(linkAt >= 112); bounds(linkAt, linkSize.toLong()) }
            val dataSize = number(104)
            val dataAt = number(108)
            require(dataAt >= 112 && dataAt % 4 == 0 && dataSize > 0 && dataSize % 4 == 0 &&
                dataAt.toLong() + dataSize == bytes.size.toLong()) { "编译包 DEX 数据区无效" }
            val mapAt = number(52)
            require(mapAt >= dataAt && mapAt % 4 == 0) { "编译包 DEX 索引偏移无效" }
            val mapCount = number(mapAt)
            require(mapCount in 1..64) { "编译包 DEX 索引无效" }
            bounds(mapAt + 4, mapCount.toLong() * 12)
            val minimum = mapOf(0 to 112, 1 to 4, 2 to 4, 3 to 12, 4 to 8, 5 to 8, 6 to 32,
                7 to 4, 8 to 8, 0x1000 to 4, 0x1001 to 4, 0x1002 to 4, 0x1003 to 4,
                0x2000 to 4, 0x2001 to 16, 0x2002 to 2, 0x2003 to 1, 0x2004 to 2,
                0x2005 to 1, 0x2006 to 16, 0xf000 to 4)
            var previous = -1
            repeat(mapCount) { index ->
                val at = mapAt + 4 + index * 12
                val type = u16(at)
                val count = number(at + 4)
                val start = number(at + 8)
                val end = if (index + 1 < mapCount) number(at + 20) else bytes.size
                require(type in minimum && u16(at + 2) == 0 && count > 0 && start > previous &&
                    start < end && type !in sections) { "编译包 DEX 索引项无效" }
                if (type >= 0x1000) require(start >= dataAt) { "编译包 DEX 数据区重叠" }
                if (type < 0x2000 || type == 0x2001 || type == 0x2006) require(start % 4 == 0) { "编译包 DEX 对齐无效" }
                bounds(start, count.toLong() * minimum.getValue(type), end)
                sections[type] = Section(count, start, end)
                previous = start
            }
            require(sections[0]?.let { it.start == 0 && it.count == 1 } == true &&
                sections[0x1000]?.let { it.start == mapAt && it.count == 1 && mapAt.toLong() + 4 + mapCount * 12 <= it.end } == true
            ) { "编译包 DEX 索引与文件头不一致" }
            for (type in 1..6) {
                val count = number(56 + (type - 1) * 8)
                val offset = number(60 + (type - 1) * 8)
                val section = sections[type]
                require(if (count == 0) offset == 0 && section == null
                    else section != null && section.count == count && section.start == offset && offset >= 112 &&
                        offset.toLong() + count.toLong() * minimum.getValue(type) <= dataAt
                ) { "编译包 DEX 表与索引不一致" }
                if (type in 2..6) require(count <= if (type == 4 || type == 5) 65536 else 65535) { "编译包 DEX 表过大" }
            }
            stringsCount = number(56)
            stringsAt = number(60)
            require((stringsCount == 0 && sections[0x2002] == null) || sections[0x2002]?.count == stringsCount) { "编译包 DEX 字符串索引无效" }
            repeat(stringsCount) { data(0x2002, number(stringsAt + it * 4)) }
            val typeSection = sections[2]
            var lastString = -1
            types = List(typeSection?.count ?: 0) {
                val stringId = number(typeSection!!.start + it * 4)
                require(stringId > lastString) { "编译包 DEX 类型索引顺序无效" }
                lastString = stringId
                string(stringId).also { value -> require(validType(value)) { "编译包 DEX 类型描述无效" } }
            }
            val protoSection = sections[3]
            protos = List(protoSection?.count ?: 0) {
                val at = protoSection!!.start + it * 12
                val shorty = string(number(at))
                val result = type(number(at + 4))
                val parameters = typeList(number(at + 8))
                spend(parameters.size)
                require(shorty.length == parameters.size + 1 && shorty[0] == shortType(result)) { "编译包 DEX 方法签名无效" }
                var words = 0
                parameters.forEachIndexed { index, value ->
                    val parameter = type(value)
                    require(parameter != "V" && shorty[index + 1] == shortType(parameter)) { "编译包 DEX 参数类型无效" }
                    words += if (parameter == "J" || parameter == "D") 2 else 1
                }
                Proto(result, parameters.size, words)
            }
            val fieldSection = sections[4]
            fieldsCount = fieldSection?.count ?: 0
            fieldsAt = fieldSection?.start ?: 0
            repeat(fieldsCount) {
                val at = fieldsAt + it * 8
                require(classType(type(u16(at))) && type(u16(at + 2)) != "V" && number(at + 4) < stringsCount) { "编译包 DEX 字段索引无效" }
            }
            val methodSection = sections[5]
            methods = List(methodSection?.count ?: 0) {
                val at = methodSection!!.start + it * 8
                val owner = u16(at)
                val proto = u16(at + 2)
                require((classType(type(owner)) || type(owner).startsWith('[')) && proto in protos.indices) { "编译包 DEX 方法索引无效" }
                Method(owner, proto, string(number(at + 4)))
            }
            val definitions = mutableMapOf<String, Definition>()
            val classSection = sections[6]
            repeat(classSection?.count ?: 0) {
                val at = classSection!!.start + it * 32
                val owner = number(at)
                val name = type(owner)
                require(classType(name) && name !in definitions) { "编译包 DEX 类定义无效" }
                val flags = number(at + 4)
                val parent = u32(at + 8).takeIf { value -> value != 0xffffffffL }?.let { value ->
                    require(value <= Int.MAX_VALUE)
                    type(value.toInt()).also { value -> require(classType(value)) }
                }
                val interfaceOffset = number(at + 12)
                val interfaces = interfaceLists.getOrPut(interfaceOffset) {
                    typeList(interfaceOffset).map { id -> type(id).also { value -> require(classType(value)) } }
                        .also { values -> require(values.toSet().size == values.size) { "编译包 DEX 接口重复" } }
                }
                spend(interfaces.size)
                require(u32(at + 16) == 0xffffffffL || u32(at + 16) < stringsCount) { "编译包 DEX 源文件索引无效" }
                number(at + 20).takeIf { value -> value != 0 }?.let { value -> data(0x2006, value, 16, 4) }
                number(at + 28).takeIf { value -> value != 0 }?.let { value -> data(0x2005, value) }
                val classDataAt = number(at + 24)
                val constructor = classDataAt != 0 && classData(classDataAt, owner)
                definitions[name] = Definition(flags, parent, interfaces, constructor)
            }
            return definitions to types
        }

        private fun validType(value: String): Boolean {
            val dimensions = value.takeWhile { it == '[' }.length
            if (dimensions > 255 || dimensions == value.length) return false
            val base = value.substring(dimensions)
            if (base.length == 1) return base[0] in "ZBSCIJFD" || dimensions == 0 && base == "V"
            return classType(base) && base.substring(1, base.lastIndex).split('/').all { part ->
                part.isNotEmpty() && part.none { it == '.' || it == ';' || it == '[' || Character.isISOControl(it) }
            }
        }

        private fun classType(value: String) = value.length > 2 && value.startsWith('L') && value.endsWith(';')
        private fun shortType(value: String) = if (value.startsWith('L') || value.startsWith('[')) 'L' else value[0]
        private fun type(index: Int): String {
            require(index in types.indices) { "编译包 DEX 类型索引越界" }
            return types[index]
        }

        private fun string(index: Int): String = strings.getOrPut(index) {
            require(index in 0 until stringsCount) { "编译包 DEX 字符串索引越界" }
            val at = number(stringsAt + index * 4)
            val cursor = Cursor(at, data(0x2002, at).end)
            val length = cursor.uleb()
            require(length <= cursor.end - cursor.position) { "编译包 DEX 字符串长度无效" }
            spend(length)
            val text = StringBuilder(minOf(length, 1024))
            repeat(length) {
                val first = cursor.byte()
                val character = when {
                    first in 1..127 -> first
                    first in 0xc0..0xdf -> {
                        val second = cursor.byte()
                        require(second and 0xc0 == 0x80)
                        ((first and 0x1f) shl 6 or (second and 0x3f)).also { require(it == 0 || it >= 128) }
                    }
                    first in 0xe0..0xef -> {
                        val second = cursor.byte()
                        val third = cursor.byte()
                        require(second and 0xc0 == 0x80 && third and 0xc0 == 0x80)
                        ((first and 15) shl 12 or ((second and 63) shl 6) or (third and 63)).also { require(it >= 2048) }
                    }
                    else -> error("编译包 DEX 字符串编码无效")
                }
                text.append(character.toChar())
            }
            require(cursor.byte() == 0) { "编译包 DEX 字符串未结束" }
            text.toString()
        }

        private fun typeList(offset: Int): List<Int> {
            if (offset == 0) return emptyList()
            return lists.getOrPut(offset) {
                val section = data(0x1001, offset, 4, 4)
                val count = number(offset)
                require(count <= 65535)
                bounds(offset + 4, count.toLong() * 2, section.end)
                spend(count)
                List(count) { u16(offset + 4 + it * 2).also { index -> require(index in types.indices) } }
            }
        }

        private fun classData(offset: Int, owner: Int): Boolean {
            val cursor = Cursor(offset, data(0x2000, offset, 4).end)
            val staticFields = cursor.uleb()
            val instanceFields = cursor.uleb()
            val directMethods = cursor.uleb()
            val virtualMethods = cursor.uleb()
            require(staticFields.toLong() + instanceFields <= fieldsCount &&
                directMethods.toLong() + virtualMethods <= methods.size) { "编译包 DEX 类成员数量无效" }
            val fieldIds = mutableSetOf<Int>()
            for (count in listOf(staticFields, instanceFields)) {
                var index = 0L
                repeat(count) { position ->
                    val delta = cursor.uleb()
                    require(position == 0 || delta > 0)
                    index += delta
                    require(index < fieldsCount && fieldIds.add(index.toInt()) && u16(fieldsAt + index.toInt() * 8) == owner) { "编译包 DEX 字段归属无效" }
                    cursor.uleb()
                }
            }
            var constructor = false
            val methodIds = mutableSetOf<Int>()
            for ((group, count) in listOf(directMethods, virtualMethods).withIndex()) {
                var index = 0L
                repeat(count) { position ->
                    val delta = cursor.uleb()
                    require(position == 0 || delta > 0)
                    index += delta
                    require(index < methods.size && methodIds.add(index.toInt())) { "编译包 DEX 方法索引无效" }
                    val method = methods[index.toInt()]
                    require(method.owner == owner) { "编译包 DEX 方法归属无效" }
                    val flags = cursor.uleb()
                    val codeAt = cursor.uleb()
                    val proto = protos[method.proto]
                    require((codeAt == 0) == (flags and 0x500 != 0)) { "编译包 DEX 方法代码缺失" }
                    if (codeAt != 0) {
                        val code = code(codeAt)
                        require(code.incoming == proto.words + if (flags and 8 == 0) 1 else 0) { "编译包 DEX 方法寄存器无效" }
                    }
                    if (method.name == "<init>") {
                        require(group == 0 && proto.result == "V" && flags and 0x10000 != 0 && flags and 0x508 == 0) { "编译包 DEX 构造方法无效" }
                        if (proto.parameters == 0 && flags and 7 == 1 && codeAt != 0) constructor = true
                    }
                }
            }
            return constructor
        }

        private fun code(offset: Int): Code = codes.getOrPut(offset) {
            val section = data(0x2001, offset, 16, 4)
            val registers = u16(offset)
            val incoming = u16(offset + 2)
            require(registers >= incoming) { "编译包 DEX 寄存器数量无效" }
            val tries = u16(offset + 6)
            number(offset + 8).takeIf { it != 0 }?.let { data(0x2003, it) }
            val instructions = number(offset + 12)
            require(instructions > 0) { "编译包 DEX 方法代码为空" }
            bounds(offset + 16, instructions.toLong() * 2, section.end)
            if (tries > 0) {
                val triesAt = offset + 16 + instructions * 2 + if (instructions % 2 == 1) 2 else 0
                bounds(triesAt, tries.toLong() * 8 + 1, section.end)
                val handlersAt = triesAt + tries * 8
                val cursor = Cursor(handlersAt, section.end)
                val handlers = cursor.uleb()
                require(handlers in 1..65535)
                val handlerOffsets = mutableSetOf<Int>()
                repeat(handlers) {
                    handlerOffsets.add(cursor.position - handlersAt)
                    val count = cursor.sleb()
                    require(count in -65535..65535)
                    repeat(kotlin.math.abs(count)) {
                        require(cursor.uleb() in types.indices && cursor.uleb() < instructions) { "编译包 DEX 异常处理无效" }
                    }
                    if (count <= 0) require(cursor.uleb() < instructions)
                }
                var previousEnd = 0L
                repeat(tries) {
                    val at = triesAt + it * 8
                    val start = u32(at)
                    val end = start + u16(at + 4)
                    require(start >= previousEnd && start < end && end <= instructions && u16(at + 6) in handlerOffsets) { "编译包 DEX 异常处理范围无效" }
                    previousEnd = end
                }
            }
            Code(incoming)
        }

        private inner class Cursor(var position: Int, val end: Int) {
            fun byte(): Int {
                bounds(position, 1, end)
                spend(1)
                return bytes[position++].toInt() and 255
            }
            fun uleb(): Int {
                var value = 0L
                repeat(5) { index ->
                    val next = byte()
                    if (index == 4) require(next and 0xf0 == 0) { "编译包 DEX ULEB128 溢出" }
                    value = value or ((next and 127).toLong() shl (index * 7))
                    if (next and 128 == 0) {
                        require(value <= Int.MAX_VALUE) { "编译包 DEX 数值过大" }
                        return value.toInt()
                    }
                }
                error("编译包 DEX ULEB128 无效")
            }
            fun sleb(): Int {
                var value = 0L
                repeat(5) { index ->
                    val next = byte()
                    value = value or ((next and 127).toLong() shl (index * 7))
                    if (next and 128 == 0) {
                        val bits = (index + 1) * 7
                        if (next and 64 != 0) value = value or (-1L shl bits)
                        require(value in Int.MIN_VALUE..Int.MAX_VALUE)
                        return value.toInt()
                    }
                }
                error("编译包 DEX SLEB128 无效")
            }
        }
    }
}

