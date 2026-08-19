/**
 * 文件：CustomSetAddModulesPage.kt
 *
 * 作用：从其他集添加模块到当前集的页面。
 *
 * 主要功能：
 * 1. 按书源分组展示所有模块
 * 2. 通过开关将模块分配到当前集或从当前集移除
 * 3. 对无限流模块进行互斥控制：每个集最多只能有一个无限流模块
 *
 * 该页面用于在自定义集之间共享模块，避免重复创建。
 */
package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard

/**
 * 从其他集添加模块到当前集
 *
 * 该页面按书源分组展示所有模块，用户可通过开关将模块分配到当前集或从当前集移除。
 * 对于无限流模块，每个集最多只能包含一个，若当前集已有无限流模块，则其他无限流模块会被禁用。
 *
 * @param targetSetId 目标集 ID，即当前正在编辑的集
 * @param allModules 所有模块的 UI 数据列表
 * @param onAssignModule 分配模块到指定集的回调，参数为（模块 ID，目标集 ID），目标集 ID 为 null 表示移出集
 * @param onBack 返回上一页的回调
 */
@Composable
fun CustomSetAddModulesPage(
    targetSetId: String,
    allModules: List<HomepageModuleManageUi>,
    onAssignModule: (String, String?) -> Unit,
    onBack: () -> Unit,
) {
    // 显示源集模块（书源集 customSetId 为 null，订阅源集 customSetId 为 null 或 rss_ 前缀）
    val sourceModules = remember(allModules) {
        allModules.filter { module ->
            // 排除已在自定义集中的模块
            module.customSetId == null ||
            module.customSetId?.let { cid -> HomepageViewModel.isSourceSetId(cid) } == true
        }
    }
    // 按书源分组展示
    val groupedModules = remember(sourceModules) {
        sourceModules.groupBy { it.sourceUrl }
    }
    // 构建目标集中已有模块的索引，用于快速查找副本（通过 sourceUrl + moduleKey 匹配）
    val targetSetModuleMap = remember(targetSetId, allModules) {
        allModules
            .filter { it.customSetId == targetSetId }
            .associateBy { it.sourceUrl to it.moduleKey }
    }
    // 检查当前集是否已有无限流模块
    val hasInfiniteModule = remember(targetSetId, allModules) {
        allModules.any {
            it.customSetId == targetSetId &&
                    HomepageViewModel.isInfinite(it.type, it.layoutConfig)
        }
    }
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupedModules.forEach { (sourceUrl, modules) ->
                // 书源分组标题：显示书源名称和URL
                item(key = "header_$sourceUrl") {
                    // 从第一个模块获取书源名称
                    val sourceName = modules.firstOrNull()?.sourceName ?: sourceUrl
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        // 书源名称
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 书源 URL
                        Text(
                            text = sourceUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 模块列表
                items(modules, key = { it.id }) { module ->
                    // 通过 sourceUrl + moduleKey 查找目标集中的副本
                    val copyInTargetSet = targetSetModuleMap[module.sourceUrl to module.moduleKey]
                    // 判断该模块是否已在当前集中（副本存在即表示已添加）
                    val isInTargetSet = copyInTargetSet != null
                    // 判断该模块是否为无限流模块
                    val isInfinite = HomepageViewModel.isInfinite(module.type, module.layoutConfig)
                    // 无限流模块若当前集已有无限流模块则禁用（已在当前集中的除外）
                    val isEnabled = !isInfinite || !hasInfiniteModule || isInTargetSet
                    // 根据模块类型 key 获取对应的枚举值，用于显示类型标题
                    val moduleType = HomepageModuleType.fromKey(module.type)

                    // 乐观更新状态：用户点击后立即反映，异步操作完成后同步
                    var pendingToggle by remember(module.id) { mutableStateOf<Boolean?>(null) }
                    val effectiveChecked = pendingToggle ?: isInTargetSet
                    // 当实际状态追上乐观状态时，清除待处理标记
                    LaunchedEffect(isInTargetSet, pendingToggle) {
                        if (pendingToggle != null && isInTargetSet == pendingToggle) {
                            pendingToggle = null
                        }
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左侧：模块标题和类型标签
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = module.title.ifBlank { module.originalTitle.ifBlank { stringResource(R.string.homepage_unnamed_module) } },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextCard(
                                        text = stringResource(moduleType.titleRes),
                                        textStyle = MaterialTheme.typography.labelSmall
                                    )
                                    TextCard(
                                        text = if (module.sourceType == "rss") "订阅源" else "书源",
                                        textStyle = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                            // 分配开关：开启表示加入当前集，关闭表示移出当前集
                            Switch(
                                checked = effectiveChecked,
                                enabled = isEnabled,
                                onCheckedChange = { checked ->
                                    pendingToggle = checked
                                    // 添加时传源模块ID（创建副本），移除时传目标集中副本的ID（删除副本）
                                    val moduleId = if (checked) module.id else copyInTargetSet?.id ?: module.id
                                    onAssignModule(
                                        moduleId,
                                        if (checked) targetSetId else null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}
