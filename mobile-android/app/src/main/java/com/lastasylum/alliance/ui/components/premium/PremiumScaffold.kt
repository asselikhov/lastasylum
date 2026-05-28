package com.lastasylum.alliance.ui.components.premium

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun PremiumListScaffold(
    titleContent: @Composable () -> Unit,
    searchContent: (@Composable () -> Unit)?,
    filtersContent: (@Composable () -> Unit)?,
    floatingAction: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            PremiumGlassBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SquadRelayDimens.contentPaddingHorizontal,
                        vertical = 8.dp,
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    titleContent()
                    if (searchContent != null) {
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            searchContent()
                        }
                    }
                    if (filtersContent != null) {
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            filtersContent()
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
        }
        if (floatingAction != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = androidx.compose.ui.Alignment.BottomEnd,
            ) {
                floatingAction()
            }
        }
    }
}
