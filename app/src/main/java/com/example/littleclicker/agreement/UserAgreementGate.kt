package com.example.littleclicker.agreement

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.OnBackInvokedOverlayInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.littleclicker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CONFIRM_WAIT_SECONDS = 5

@Composable
internal fun UserAgreementGate(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onAccepted: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var accepted by remember(context) { mutableStateOf(UserAgreementStore.isAccepted(context)) }
    var remainingSeconds by remember { mutableIntStateOf(CONFIRM_WAIT_SECONDS) }
    var isSaving by remember { mutableStateOf(false) }
    val showAgreement = !accepted
    val navigationEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
    val agreementText = remember(context) {
        context.resources.openRawResource(R.raw.user_agreement)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }
    }

    content()

    LaunchedEffect(showAgreement) {
        if (!showAgreement) return@LaunchedEffect
        remainingSeconds = CONFIRM_WAIT_SECONDS
        while (remainingSeconds > 0) {
            delay(1_000L)
            remainingSeconds--
        }
    }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventOwner,
    ) {
        WindowDialog(
            show = showAgreement,
            modifier = modifier,
            title = stringResource(R.string.user_agreement_title),
            summary = stringResource(R.string.user_agreement_summary),
            onDismissRequest = { /* 用户必须通过协议按钮或返回键明确选择 */ },
        ) {
            DialogBackHandler(
                navigationEventDispatcher = navigationEventOwner.navigationEventDispatcher,
                onBack = onCancel,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = agreementText,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.user_agreement_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(
                    text = if (remainingSeconds > 0) {
                        stringResource(R.string.user_agreement_confirm_countdown, remainingSeconds)
                    } else {
                        stringResource(R.string.user_agreement_confirm)
                    },
                    onClick = {
                        if (remainingSeconds > 0 || isSaving) return@TextButton
                        isSaving = true
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                UserAgreementStore.markAccepted(context)
                            }
                            result.onSuccess {
                                accepted = true
                                onAccepted()
                            }.onFailure {
                                isSaving = false
                                Toast.makeText(
                                    context,
                                    R.string.user_agreement_save_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = remainingSeconds == 0 && !isSaving,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun DialogBackHandler(
    navigationEventDispatcher: NavigationEventDispatcher,
    onBack: () -> Unit,
) {
    val dialogRootView = LocalView.current.rootView
    val currentOnBack by rememberUpdatedState(onBack)

    DisposableEffect(dialogRootView, navigationEventDispatcher) {
        val previousFocusableInTouchMode = dialogRootView.isFocusableInTouchMode
        val navigationInput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dialogRootView.findOnBackInvokedDispatcher()?.let { platformDispatcher ->
                OnBackInvokedOverlayInput(platformDispatcher).also(navigationEventDispatcher::addInput)
            }
        } else {
            null
        }
        val listener = View.OnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return@OnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_UP) {
                currentOnBack()
            }
            true
        }

        dialogRootView.isFocusableInTouchMode = true
        dialogRootView.requestFocus()
        dialogRootView.setOnKeyListener(listener)

        onDispose {
            navigationInput?.let(navigationEventDispatcher::removeInput)
            dialogRootView.setOnKeyListener(null)
            dialogRootView.isFocusableInTouchMode = previousFocusableInTouchMode
        }
    }
}
