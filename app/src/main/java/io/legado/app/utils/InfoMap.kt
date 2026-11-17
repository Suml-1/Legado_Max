package io.legado.app.utils

import androidx.annotation.Keep
import io.legado.app.help.CacheManager

/**
 * 发现按钮信息
 */
@Keep
class InfoMap(val sourceUrl: String) : MutableMap<String, String> {
    private var actualMap: MutableMap<String, String>
    var needSave = false
    private var saveTime = 0

    init {
        val cache = CacheManager.get("infoMap_$sourceUrl")
        actualMap = GSON.fromJsonObject<MutableMap<String, String>>(cache).getOrNull() ?: mutableMapOf()
    }

    /**
     * time 保存时间 单位为秒
     */
    @JvmOverloads
    fun save(time: Int = 0, need: Boolean = true) {
        needSave = need
        saveTime = time
    }

    fun saveNow() {
        val json = GSON.toJson(actualMap)
        CacheManager.put("infoMap_$sourceUrl", json, saveTime)
        needSave = false
    }

    /**
     * 是否曾落盘过（即使内容为空）。用于区分「writer 从未执行持久化」与「持久化内容为空」：
     * 前者说明缓存命中时 infoMap 副作用缺失，需要重跑分类 JS 自愈；后者不用重跑。
     */
    fun hasPersisted(): Boolean = CacheManager.get("infoMap_$sourceUrl") != null

    fun get(): MutableMap<String, String> = actualMap

    fun set(value: Map<String, String>) {
        actualMap = value.toMutableMap()
    }

    override fun get(key: String) = actualMap[key]
    override fun put(key: String, value: String) = actualMap.put(key, value)
    override fun remove(key: String) = actualMap.remove(key)
    override fun putAll(from: Map<out String, String>) = actualMap.putAll(from)
    override fun containsKey(key: String) = actualMap.containsKey(key)
    override fun containsValue(value: String) = actualMap.containsValue(value)
    override val size get() = actualMap.size
    override val entries get() = actualMap.entries
    override val keys get() = actualMap.keys
    override val values get() = actualMap.values
    override fun isEmpty() = actualMap.isEmpty()
    override fun clear() = actualMap.clear()
}
