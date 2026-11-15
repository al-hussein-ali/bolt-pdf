package com.velocity.pdf.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DismissibleSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        val dismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                data.dismiss()
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
        ) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                action = data.visuals.actionLabel?.let { label ->
                    {
                        TextButton(onClick = { data.performAction() }) {
                            Text(label)
                        }
                    }
                },
                dismissAction = {
                    IconButton(onClick = { data.dismiss() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.dismiss
                        )
                    }
                }
            ) {
                Text(data.visuals.message)
            }
        }
    }
}
