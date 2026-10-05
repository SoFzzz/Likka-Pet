package com.likkapet.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likkapet.R
import com.likkapet.presentation.components.AppIcon
import com.likkapet.presentation.components.AppIconLoader
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.LikkaTopBar
import com.likkapet.presentation.components.NoAppIcons
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing

/** Everything "Añadir app" can ask for; the screen forwards them, no logic in the composable. */
data class AddAppActions(
    val onBackClick: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onAddClick: (String) -> Unit = {},
)

/** Settings › "Añadir app" (design system §2.5, RF-S06): settings voice, no irony. */
@Composable
fun AddAppScreen(
    viewModel: AddAppViewModel,
    iconLoader: AppIconLoader,
    onBackClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AddAppContent(
        state = state,
        iconLoader = iconLoader,
        actions =
            AddAppActions(
                onBackClick = onBackClick,
                onQueryChange = viewModel::onQueryChange,
                onClearQuery = viewModel::onClearQuery,
                onAddClick = viewModel::onAddClick,
            ),
    )
}

@Composable
fun AddAppContent(
    state: AddAppUiState,
    actions: AddAppActions,
    modifier: Modifier = Modifier,
    iconLoader: AppIconLoader = NoAppIcons,
) {
    Column(modifier = modifier.screenInsets()) {
        LikkaTopBar(title = stringResource(R.string.add_app_title), onBackClick = actions.onBackClick)
        SearchField(query = state.query, onQueryChange = actions.onQueryChange, onClearQuery = actions.onClearQuery)
        Text(
            text = stringResource(R.string.add_app_privacy_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = LikkaSpacing.s),
        )
        when (val list = state.list) {
            AddAppListState.Loading -> LoadingState()
            AddAppListState.NoResults -> NoResultsState(onClearQuery = actions.onClearQuery)
            is AddAppListState.Results -> AppList(list, iconLoader, actions.onAddClick)
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.add_app_search)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.add_app_clear_search))
                }
            }
        },
    )
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.add_app_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun NoResultsState(onClearQuery: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = LikkaSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
    ) {
        Text(
            text = stringResource(R.string.add_app_no_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onClearQuery, modifier = Modifier.heightIn(min = LikkaComponentSize.minTouchTarget)) {
            Text(text = stringResource(R.string.add_app_clear_search), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AppList(
    list: AddAppListState.Results,
    iconLoader: AppIconLoader,
    onAddClick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (list.isEverythingAdded) {
            item {
                Text(
                    text = stringResource(R.string.add_app_all_added),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = LikkaSpacing.s),
                )
            }
        }
        items(list.apps, key = { it.packageName }) { app ->
            AddableAppRow(app = app, iconLoader = iconLoader, onAddClick = { onAddClick(app.packageName) })
        }
    }
}

/** The whole row is the target of "Añadir"; an added app shows a check plus the word, never only one of them. */
@Composable
private fun AddableAppRow(
    app: AddableAppUi,
    iconLoader: AppIconLoader,
    onAddClick: () -> Unit,
) {
    val addDescription = stringResource(R.string.add_app_action_description, app.label)
    val rowModifier =
        if (app.isAdded) {
            Modifier.semantics(mergeDescendants = true) {}
        } else {
            Modifier.clickable(onClick = onAddClick).clearAndSetSemantics {
                contentDescription = addDescription
                role = Role.Button
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = LikkaComponentSize.settingsRowHeight).then(rowModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        AppIcon(packageName = app.packageName, loader = iconLoader)
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (app.isAdded) AddedMark() else AddLabel()
    }
}

@Composable
private fun AddLabel() {
    Text(
        text = stringResource(R.string.add_app_action),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = LikkaSpacing.s),
    )
}

@Composable
private fun AddedMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.xs)) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(LikkaComponentSize.iconChip),
        )
        Text(
            text = stringResource(R.string.add_app_added),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val previewApps =
    listOf(
        AddableAppUi("com.android.chrome", "Chrome", isAdded = false),
        AddableAppUi("com.google.android.gm", "Gmail", isAdded = false),
        AddableAppUi("com.netflix.mediaclient", "Netflix", isAdded = true),
        AddableAppUi("com.spotify.music", "Spotify", isAdded = false),
    )

@LikkaThemePreviews
@Composable
private fun AddAppListPreview() {
    LikkaPreview {
        AddAppContent(AddAppUiState("", AddAppListState.Results(previewApps, isEverythingAdded = false)), AddAppActions())
    }
}

@LikkaThemePreviews
@Composable
private fun AddAppLoadingPreview() {
    LikkaPreview { AddAppContent(AddAppUiState("", AddAppListState.Loading), AddAppActions()) }
}

@LikkaThemePreviews
@Composable
private fun AddAppNoResultsPreview() {
    LikkaPreview { AddAppContent(AddAppUiState("zzz", AddAppListState.NoResults), AddAppActions()) }
}
