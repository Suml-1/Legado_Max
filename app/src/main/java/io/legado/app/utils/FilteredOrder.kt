package io.legado.app.utils

/** 把过滤子集的最新排序合并回完整列表，未出现在子集里的条目保持原相对顺序。 */
internal fun <T, K> mergeFilteredOrder(
    allItems: List<T>,
    orderedItems: List<T>,
    keyOf: (T) -> K
): List<T> {
    val currentItemsByKey = allItems.associateBy(keyOf)
    val ordered = orderedItems
        .mapNotNull { currentItemsByKey[keyOf(it)] }
        .distinctBy(keyOf)
    val orderedKeys = ordered.mapTo(hashSetOf(), keyOf)
    val orderedIterator = ordered.iterator()
    return allItems.map { item ->
        if (keyOf(item) in orderedKeys && orderedIterator.hasNext()) {
            orderedIterator.next()
        } else {
            item
        }
    }
}
