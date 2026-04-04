package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun WorkspaceScreen(
    onNavigate: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    WorkspaceOverviewTab(
        onNavigate = onNavigate,
        viewModel = viewModel
    )
}