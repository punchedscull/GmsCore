package org.microg.vending.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.vending.R
import org.microg.vending.enterprise.App
import org.microg.vending.enterprise.AppState

@Composable
fun AppRow(app: App, state: AppState, install: () -> Unit, update: () -> Unit, uninstall: () -> Unit) {
    Row(
        Modifier.padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSpace = Modifier.size(48.dp)
        if (app.iconUrl != null) {
            AsyncImage(
                model = app.iconUrl,
                modifier = iconSpace,
                contentDescription = null,
            )
        } else {
            Spacer(iconSpace)
        }
        Text(app.displayName)

        Spacer(Modifier.weight(1f))
        if (state == AppState.NOT_COMPATIBLE) {
            Icon(Icons.Default.Warning, null, Modifier.padding(end=8.dp), tint = MaterialTheme.colorScheme.secondary)
            // TODO better UI
        }
        if (state == AppState.UPDATE_AVAILABLE || state == AppState.INSTALLED) {
            IconButton(uninstall) {
                Icon(Icons.Default.Delete, stringResource(R.string.vending_overview_row_action_uninstall), tint = MaterialTheme.colorScheme.secondary)
            }
        }
        if (state == AppState.UPDATE_AVAILABLE) {
            FilledIconButton(update, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Icon(painterResource(R.drawable.ic_update), stringResource(R.string.vending_overview_row_action_update), tint = MaterialTheme.colorScheme.secondary)
            }
        }
        if (state == AppState.NOT_INSTALLED) {
            FilledIconButton(install, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Icon(painterResource(R.drawable.ic_download), stringResource(R.string.vending_overview_row_action_install), tint = MaterialTheme.colorScheme.secondary)
            }
        }
        if (state == AppState.PENDING) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        }
    }

}

private val previewApp = App("org.mozilla.firefox", 0, "Firefox", null, null)
@Preview
@Composable
fun AppRowNotCompatiblePreview() {
    AppRow(previewApp, AppState.NOT_COMPATIBLE, {}, {}, {})
}

@Preview
@Composable
fun AppRowNotInstalledPreview() {
    AppRow(previewApp, AppState.NOT_INSTALLED, {}, {}, {})
}

@Preview
@Composable
fun AppRowUpdateablePreview() {
    AppRow(previewApp, AppState.UPDATE_AVAILABLE, {}, {}, {})
}

@Preview
@Composable
fun AppRowInstalledPreview() {
    AppRow(previewApp, AppState.INSTALLED, {}, {}, {})
}

@Preview
@Composable
fun AppRowPendingPreview() {
    AppRow(previewApp, AppState.PENDING, {}, {}, {})
}