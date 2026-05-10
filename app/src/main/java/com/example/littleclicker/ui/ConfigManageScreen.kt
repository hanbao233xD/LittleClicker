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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar
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
    val topBarColor = pageGradient.first()
    val cardContainerColor = MiuixTheme.colorScheme.surfaceContainer
    val activeCardColor = if (isDarkTheme) Color(0xFF1F2A3B) else Color(0xFFEAF2FF)
    val successColor = if (isDarkTheme) Color(0xFF7AD7A1) else Color(0xFF1F8B4C)
    var saveAsName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var editingProfileName by remember { mutableStateOf("") }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = "配置管理",
                largeTitle = "配置管理",
                color = topBarColor,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = pageGradient
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
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
                        MiuixTextField(
                            value = profile.name,
                            onValueChange = { AutoClickCoordinator.updateProfileName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = "配置名称"
                        )
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
                        MiuixTextField(
                            value = saveAsName,
                            onValueChange = { saveAsName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = "新配置名称（留空自动编号）"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val result = AutoClickCoordinator.saveAsEmptyProfile(saveAsName)
                                    result.onSuccess { saved ->
                                        saveAsName = saved.name
                                        Toast.makeText(context, "已保存为空配置：${saved.name}", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, it.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("新建空配置")
                            }
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
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("保存为新配置", color = Color.White)
                            }
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
                    val isEditingName = editingProfileId == item.id
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEditingName) "编辑配置名称" else item.name,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (isEditingName) {
                                            editingProfileId = null
                                            editingProfileName = ""
                                        } else {
                                            editingProfileId = item.id
                                            editingProfileName = item.name
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "编辑配置名称"
                                    )
                                }
                            }
                            if (isEditingName) {
                                MiuixTextField(
                                    value = editingProfileName,
                                    onValueChange = { editingProfileName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "配置名称"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val result = AutoClickCoordinator.renameProfile(item.id, editingProfileName)
                                            result.onSuccess {
                                                editingProfileId = null
                                                editingProfileName = ""
                                                Toast.makeText(context, "已重命名为：${it.name}", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, it.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("保存名称")
                                    }
                                    Button(
                                        onClick = {
                                            editingProfileId = null
                                            editingProfileName = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("取消")
                                    }
                                }
                            }
                            Text("id: ${item.id}", color = MiuixTheme.colorScheme.onBackgroundVariant)
                            Text(
                                "更新时间：${formatDateTime(item.updatedAt)}",
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                            Text(
                                "点位数：${item.points.size}",
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
}
