package com.example.littleclicker.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.littleclicker.BuildConfig
import com.example.littleclicker.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val version = BuildConfig.VERSION_NAME
    val aboutItems = listOf(
        "官网",
        "QQ群",
        "检查更新",
        "隐私政策",
        "免责声明"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = appName,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = appName, style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Version $version", color = MiuixTheme.colorScheme.onBackgroundVariant)
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer
            )
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(aboutItems) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (item) {
                                    "官网" -> openExternalUrl(
                                        context = context,
                                        url = "https://littlecold.cn/",
                                        failureHint = "官网链接打开失败"
                                    )

                                    "QQ群" -> openExternalUrl(
                                        context = context,
                                        url = "https://qm.qq.com/q/vTyFd6Fsti",
                                        failureHint = "QQ群链接打开失败"
                                    )

                                    else -> Toast.makeText(context, "$item 功能待接入", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item, fontWeight = FontWeight.Medium)
                        Text(">", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            }
        }
    }
}

private fun openExternalUrl(
    context: android.content.Context,
    url: String,
    failureHint: String,
) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        val message = if (it is ActivityNotFoundException) {
            failureHint
        } else {
            "$failureHint：${it.message ?: "未知错误"}"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
