/*
 * Copyright (c) 2026. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.redesign.settings.ui.plus

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.plus.apps.AppEntry
import com.protonvpn.android.redesign.base.ui.DIALOG_CONTENT_PADDING
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun PlusAppListScreen(
    type: PlusAppListType,
    onClose: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: PlusAppListViewModel = hiltViewModel(),
) {
    LaunchedEffect(type) {
        viewModel.initType(type)
    }

    val scope = rememberCoroutineScope()
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val otherListPackages by viewModel.otherListPackages.collectAsStateWithLifecycle()
    val (selectedApps, unselectedApps) = viewModel.filteredApps.collectAsStateWithLifecycle().value
    val query by viewModel.query.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChanges.collectAsStateWithLifecycle()
    val enforcerStatusText by viewModel.enforcerStatusText.collectAsStateWithLifecycle()

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val handleBack = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = hasChanges) {
        showDiscardDialog = true
    }

    val title = when (type) {
        PlusAppListType.Blacklist -> stringResource(R.string.plus_internet_blacklist_title)
        PlusAppListType.Whitelist -> stringResource(R.string.plus_vpn_whitelist_title)
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = { Text(title, style = ProtonTheme.typography.body1Bold) },
                navigationIcon = { TopAppBarBackIcon(onClick = handleBack) },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.save()
                                onSaved()
                                onClose()
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Text(
                            stringResource(R.string.plus_save),
                            style = ProtonTheme.typography.body1Bold,
                            color = if (hasChanges) ProtonTheme.colors.notificationSuccess else ProtonTheme.colors.textHint
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .largeScreenContentPadding()
        ) {
            // Header Toggle Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = ProtonTheme.typography.body1Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.setEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ProtonTheme.colors.backgroundNorm,
                                checkedTrackColor = ProtonTheme.colors.notificationSuccess
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (type) {
                            PlusAppListType.Blacklist -> stringResource(R.string.plus_blacklist_description)
                            PlusAppListType.Whitelist -> stringResource(R.string.plus_whitelist_description)
                        },
                        style = ProtonTheme.typography.defaultWeak
                    )

                    if (type == PlusAppListType.Whitelist) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.plus_whitelist_empty_note),
                            style = ProtonTheme.typography.body2Regular,
                            color = ProtonTheme.colors.textAccent
                        )
                    }

                    if (type == PlusAppListType.Blacklist) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ProtonTheme.colors.backgroundSecondary)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = enforcerStatusText,
                                    style = ProtonTheme.typography.body2Regular,
                                    color = ProtonTheme.colors.textNorm
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.releaseAllBlocks() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = ProtonTheme.colors.textAccent
                                    )
                                ) {
                                    Text(
                                        stringResource(R.string.plus_release_all_blocks),
                                        style = ProtonTheme.typography.body2Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = { Text(stringResource(R.string.plus_search_hint), style = ProtonTheme.typography.defaultWeak) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_proton_magnifier),
                            contentDescription = null,
                            tint = ProtonTheme.colors.textHint
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_proton_cross),
                                    contentDescription = null,
                                    tint = ProtonTheme.colors.textHint
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProtonTheme.colors.textAccent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = ProtonTheme.colors.backgroundSecondary,
                        unfocusedContainerColor = ProtonTheme.colors.backgroundSecondary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Selected Apps Header
            if (selectedApps.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.plus_selected_apps, selectedApps.size),
                        style = ProtonTheme.typography.body1Bold,
                        color = ProtonTheme.colors.textAccent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(selectedApps, key = { "selected_${it.packageName}" }) { app ->
                    AppRow(
                        app = app,
                        isSelected = true,
                        isInOtherList = false,
                        otherListLabel = "",
                        viewModel = viewModel,
                        onClick = { viewModel.toggleApp(app.packageName) }
                    )
                }
            }

            // System Apps Expander
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleShowSystemApps() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showSystemApps) stringResource(R.string.plus_hide_system_apps) else stringResource(R.string.plus_show_system_apps),
                        style = ProtonTheme.typography.body2Medium,
                        color = ProtonTheme.colors.textAccent,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painter = painterResource(
                            if (showSystemApps) CoreR.drawable.ic_proton_chevron_up else CoreR.drawable.ic_proton_chevron_down
                        ),
                        contentDescription = null,
                        tint = ProtonTheme.colors.textAccent
                    )
                }
            }

            // Other Apps Section
            if (unselectedApps.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.plus_other_apps),
                        style = ProtonTheme.typography.body1Bold,
                        color = ProtonTheme.colors.textHint,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(unselectedApps, key = { "unselected_${it.packageName}" }) { app ->
                    val inOther = app.packageName in otherListPackages
                    val otherLabel = when (type) {
                        PlusAppListType.Blacklist -> stringResource(R.string.plus_in_other_list_whitelist)
                        PlusAppListType.Whitelist -> stringResource(R.string.plus_in_other_list_blacklist)
                    }
                    AppRow(
                        app = app,
                        isSelected = false,
                        isInOtherList = inOther,
                        otherListLabel = otherLabel,
                        viewModel = viewModel,
                        onClick = {
                            if (!inOther) {
                                viewModel.toggleApp(app.packageName)
                            }
                        }
                    )
                }
            }

            if (isLoading && selectedApps.isEmpty() && unselectedApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ProtonTheme.colors.textAccent)
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        ProtonBasicAlert(
            onDismissRequest = { showDiscardDialog = false }
        ) {
            Column(modifier = Modifier.padding(DIALOG_CONTENT_PADDING)) {
                Text(
                    text = "Discard changes",
                    style = ProtonTheme.typography.body1Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Discard unsaved changes?",
                    style = ProtonTheme.typography.body1Regular
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text(stringResource(R.string.cancel), color = ProtonTheme.colors.textNorm)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showDiscardDialog = false
                        viewModel.discard()
                        onClose()
                    }) {
                        Text("Discard", color = ProtonTheme.colors.notificationError)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    isSelected: Boolean,
    isInOtherList: Boolean,
    otherListLabel: String,
    viewModel: PlusAppListViewModel,
    onClick: () -> Unit
) {
    val iconState = produceState<Drawable?>(initialValue = null, app.packageName) {
        value = viewModel.getIcon(app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isInOtherList, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = iconState.value
        if (icon != null) {
            val bitmap = remember(icon) { icon.toBitmap(96, 96).asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProtonTheme.colors.backgroundSecondary)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = ProtonTheme.typography.body1Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isInOtherList) ProtonTheme.colors.textHint else ProtonTheme.colors.textNorm
            )
            val subText = if (isInOtherList) otherListLabel else app.packageName
            Text(
                text = subText,
                style = ProtonTheme.typography.defaultWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isInOtherList) ProtonTheme.colors.notificationError else ProtonTheme.colors.textHint
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Checkbox(
            checked = isSelected,
            onCheckedChange = { if (!isInOtherList) onClick() },
            enabled = !isInOtherList,
            colors = CheckboxDefaults.colors(
                checkedColor = ProtonTheme.colors.notificationSuccess,
                checkmarkColor = ProtonTheme.colors.backgroundNorm,
                disabledCheckedColor = ProtonTheme.colors.textHint
            )
        )
    }
}
