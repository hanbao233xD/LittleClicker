package com.example.littleclicker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.littleclicker.autoclick.AutoClickCoordinator
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ScriptManageScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val drafts by AutoClickCoordinator.scriptDrafts.collectAsState()
    var draftName by remember { mutableStateOf("") }
    var selectedDraftId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(text = "脚本草稿管理", style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold)
        }
        item {
            Text(
                text = "本阶段仅支持脚本草稿保存与读取，动作编辑后续开发。",
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
        item {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { M3Text("草稿名称") }
            )
        }
        item {
            Button(
                onClick = {
                    val result = AutoClickCoordinator.createScriptDraft(draftName)
                    result.onSuccess { draft ->
                        selectedDraftId = draft.id
                        draftName = draft.name
                        Toast.makeText(context, "草稿已保存", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("新建并保存")
            }
        }
        item {
            Button(
                onClick = {
                    val draftId = selectedDraftId
                    if (draftId == null) {
                        Toast.makeText(context, "请先选择一个草稿", Toast.LENGTH_SHORT).show()
                    } else {
                        val result = AutoClickCoordinator.saveScriptDraft(draftId, draftName)
                        result.onSuccess {
                            Toast.makeText(context, "草稿已更新", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, it.message ?: "更新失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("覆盖保存已选草稿")
            }
        }
        item {
            Button(
                onClick = { AutoClickCoordinator.refreshScriptDrafts() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("刷新草稿列表")
            }
        }

        if (drafts.isEmpty()) {
            item {
                Text("暂无草稿", color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        } else {
            items(drafts, key = { it.id }) { draft ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    colors = CardDefaults.defaultColors(
                        color = if (selectedDraftId == draft.id) Color(0xFFEAF2FF) else Color.White,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                    ),
                    onClick = {
                        selectedDraftId = draft.id
                        draftName = draft.name
                    },
                    insideMargin = PaddingValues(0.dp)
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(text = draft.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "id: ${draft.id}",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Text(
                            text = "更新时间: ${formatDateTime(draft.updatedAt)}",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }
        }
    }
}
