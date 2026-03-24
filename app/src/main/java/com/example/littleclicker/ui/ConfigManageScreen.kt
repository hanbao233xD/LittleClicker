package com.example.littleclicker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.littleclicker.autoclick.AutoClickCoordinator
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ConfigManageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val profile by AutoClickCoordinator.profile.collectAsState()
    val profiles by AutoClickCoordinator.profiles.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val pageGradient = if (isDarkTheme) {
        listOf(Color(0xFF101219), Color(0xFF171B26))
    } else {
        listOf(Color(0xFFF7F9FF), Color(0xFFF1F5FF))
    }
    val cardContainerColor = MiuixTheme.colorScheme.surfaceContainer
    val activeCardColor = if (isDarkTheme) Color(0xFF1F2A3B) else Color(0xFFEAF2FF)
    val successColor = if (isDarkTheme) Color(0xFF7AD7A1) else Color(0xFF1F8B4C)
    var saveAsName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AutoClickCoordinator.initialize(context)
                AutoClickCoordinator.refreshProfiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = pageGradient
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "配置管理", style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold)
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "可保存当前配置、另存为新配置，并从本地配置列表中加载或复制。",
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = cardContainerColor,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("当前配置编辑")
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = { AutoClickCoordinator.updateProfileName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { M3Text("配置名称") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("全局循环次数：${profile.cycleCount}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TinyButton("-") { AutoClickCoordinator.updateCycleCount(profile.cycleCount - 1) }
                            TinyButton("+") { AutoClickCoordinator.updateCycleCount(profile.cycleCount + 1) }
                        }
                    }
                    Button(
                        onClick = {
                            val result = AutoClickCoordinator.saveProfile()
                            val tip = if (result.isSuccess) {
                                "当前配置已保存到本地"
                            } else {
                                "保存失败：${result.exceptionOrNull()?.message}"
                            }
                            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存当前配置")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = cardContainerColor,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("另存为新配置")
                    OutlinedTextField(
                        value = saveAsName,
                        onValueChange = { saveAsName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { M3Text("新配置名称（留空自动编号）") }
                    )
                    Button(
                        onClick = {
                            val result = AutoClickCoordinator.saveAsNewProfile(saveAsName)
                            result.onSuccess { saved ->
                                saveAsName = saved.name
                                Toast.makeText(context, "已另存为：${saved.name}", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "另存失败", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存为新配置")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("本地配置列表", fontWeight = FontWeight.Bold)
                Button(onClick = { AutoClickCoordinator.refreshProfiles() }) {
                    Text("刷新")
                }
            }
        }

        if (profiles.isEmpty()) {
            item {
                Text(
                    text = "暂无配置，请先保存当前配置。",
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        } else {
            items(profiles, key = { it.id }) { item ->
                val isActive = item.id == profile.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    colors = CardDefaults.defaultColors(
                        color = if (isActive) activeCardColor else cardContainerColor,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("id: ${item.id}", color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Text(
                            "更新时间：${formatDateTime(item.updatedAt)}",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Text(
                            "点位数：${item.points.size}，循环：${item.cycleCount}",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isActive) "当前使用中" else "未加载",
                                color = if (isActive) successColor else MiuixTheme.colorScheme.onBackgroundVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val result = AutoClickCoordinator.loadProfile(item.id)
                                        result.onSuccess {
                                            pendingDeleteId = null
                                            Toast.makeText(context, "已加载配置：${it.name}", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, it.message ?: "加载失败", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isActive
                                ) {
                                    Text("加载")
                                }
                                Button(
                                    onClick = {
                                        val result = AutoClickCoordinator.duplicateProfile(item.id)
                                        result.onSuccess { copied ->
                                            pendingDeleteId = null
                                            Toast.makeText(
                                                context,
                                                "已复制：${copied.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure {
                                            Toast.makeText(context, it.message ?: "复制失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("复制")
                                }
                                val isPendingDelete = pendingDeleteId == item.id
                                Button(
                                    onClick = {
                                        if (!isPendingDelete) {
                                            pendingDeleteId = item.id
                                            Toast.makeText(context, "再次点击删除可确认", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        val result = AutoClickCoordinator.deleteProfile(item.id)
                                        result.onSuccess { activeAfterDelete ->
                                            pendingDeleteId = null
                                            Toast.makeText(
                                                context,
                                                "已删除，当前配置：${activeAfterDelete.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure {
                                            pendingDeleteId = null
                                            Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(if (isPendingDelete) "确认删除" else "删除")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TinyButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(34.dp)
            .width(42.dp)
    ) {
        Text(text)
    }
}
