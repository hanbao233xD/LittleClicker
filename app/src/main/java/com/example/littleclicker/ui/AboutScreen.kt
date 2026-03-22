package com.example.littleclicker.ui

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
    val items = listOf("检查更新", "隐私政策", "免责声明")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
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
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "$item 功能待接入", Toast.LENGTH_SHORT).show()
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
