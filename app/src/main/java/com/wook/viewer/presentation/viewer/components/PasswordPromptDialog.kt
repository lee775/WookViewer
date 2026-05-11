package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.wook.viewer.R

/**
 * 암호화된 문서의 비밀번호 입력 다이얼로그.
 *
 * @param wrongPasswordAttempt 직전 시도가 비밀번호 불일치였는지 (true면 빨간색 안내 표시)
 * @param canUnlock 잠금 해제 시도가 가능한 포맷인지 (현재 PDF만 true).
 *                  false면 입력란 비활성 + "이 형식은 아직 지원 안 됨" 안내만 표시.
 */
@Composable
fun PasswordPromptDialog(
    wrongPasswordAttempt: Boolean,
    canUnlock: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (canUnlock) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val submit = {
        if (canUnlock && password.isNotEmpty()) {
            onSubmit(password)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        if (canUnlock) R.string.password_dialog_message
                        else R.string.password_dialog_unsupported
                    )
                )
                if (canUnlock) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        isError = wrongPasswordAttempt,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        label = { Text(stringResource(R.string.password_dialog_field_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    if (wrongPasswordAttempt) {
                        Text(
                            text = stringResource(R.string.password_dialog_wrong),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (canUnlock) {
                TextButton(
                    onClick = submit,
                    enabled = password.isNotEmpty()
                ) {
                    Text(stringResource(R.string.password_dialog_submit))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
        dismissButton = if (canUnlock) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } }
        } else null
    )
}
