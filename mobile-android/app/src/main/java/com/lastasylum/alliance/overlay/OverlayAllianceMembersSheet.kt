package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private object AllianceTableTokens {
    val colApp = 30.dp
    val colName = 152.dp
    val colPower = 70.dp
    val colLevel = 38.dp
    val colCastle = 44.dp
    val colRank = 42.dp
    val colKills = 62.dp
    val colSeen = 52.dp
    val rowPaddingH = 8.dp

    val total = colApp + colName + colPower + colLevel + colCastle +
        colRank + colKills + colSeen + rowPaddingH * 2

    val headerFill = Color(0xFF101722)
    val rowEven = Color(0xFF141C28)
    val rowOdd = Color(0xFF111824)
    val divider = Color(0x1FFFFFFF)
    val coordFill = PremiumColors.accentCyan.copy(alpha = 0.16f)
    val coordText = PremiumColors.accentCyan
    val appOn = PremiumColors.accentCyan
    val appOff = Color(0xFF3A4658)
}

/**
 * Окно «Участники альянса»: компактная профессиональная таблица всего ростера из игры.
 * Тап по координатам — перелёт к соалийцу на карте, у каждого участника бейдж наличия приложения.
 */
@Composable
fun OverlayAllianceMembersSheet(
    onDismissRequest: () -> Unit,
    onCloseAllOverlays: () -> Unit = onDismissRequest,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val tokens = OverlayOnlineMemberTokens

    var roster by remember { mutableStateOf<List<AllianceMember>>(emptyList()) }
    var appNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            AllianceRosterCache.peek().takeIf { it.isNotEmpty() }
                ?: AllianceRosterCache.parse(
                    AppContainer.from(context).userSettingsPreferences.getAllianceRosterJson(),
                ).also { if (it.isNotEmpty()) AllianceRosterCache.update(it) }
        }
        roster = loaded.sortedWith(
            compareByDescending<AllianceMember> { it.power }.thenBy { it.name.lowercase() },
        )
        appNames = OverlayTeamContextCache.peekCachedTeam()
            ?.members
            ?.map { it.username.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    val filtered = remember(roster, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) roster else roster.filter { it.name.lowercase().contains(q) }
    }

    val sheetHeight = (configuration.screenHeightDp * 0.74f).roundToInt().dp

    OverlayAwareBottomSheet(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = sheetHeight),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.overlay_alliance_members_title),
                            fontFamily = tokens.InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = tokens.titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.overlay_alliance_members_count,
                                    roster.size,
                                ),
                                fontFamily = tokens.InterFamily,
                                fontSize = 11.sp,
                                color = tokens.metaColor,
                            )
                            Spacer(Modifier.width(10.dp))
                            AppBadgeDot(hasApp = true, size = 9.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.overlay_alliance_members_legend_app),
                                fontFamily = tokens.InterFamily,
                                fontSize = 11.sp,
                                color = tokens.metaColor,
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.overlay_history_close_cd),
                            tint = tokens.metaColor,
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = tokens.mutedColor,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.overlay_alliance_members_search),
                            fontFamily = tokens.InterFamily,
                            fontSize = 13.sp,
                            color = tokens.mutedColor,
                        )
                    },
                    textStyle = tokens.titleStyle.copy(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.borderLive,
                        unfocusedBorderColor = tokens.borderDefault,
                        focusedTextColor = tokens.titleColor,
                        unfocusedTextColor = tokens.titleColor,
                        cursorColor = tokens.borderLive,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )

                Spacer(Modifier.height(6.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_alliance_members_empty),
                            fontFamily = tokens.InterFamily,
                            fontSize = 13.sp,
                            color = tokens.mutedColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                } else {
                    val hScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll),
                    ) {
                        AllianceTableHeader()
                        LazyColumn(
                            modifier = Modifier
                                .width(AllianceTableTokens.total)
                                .fillMaxHeight(),
                        ) {
                            itemsIndexed(filtered, key = { _, m -> m.id }) { index, m ->
                                AllianceMemberRow(
                                    member = m,
                                    even = index % 2 == 0,
                                    hasApp = appNames.contains(m.name.lowercase()),
                                    onFly = {
                                        onCloseAllOverlays()
                                        GameMapNavigator.open(
                                            context = context,
                                            x = m.x,
                                            y = m.y,
                                            serverNumber = m.sid.takeIf { it > 0 },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllianceTableHeader() {
    val tokens = OverlayOnlineMemberTokens
    Row(
        modifier = Modifier
            .width(AllianceTableTokens.total)
            .background(AllianceTableTokens.headerFill)
            .padding(horizontal = AllianceTableTokens.rowPaddingH, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("", AllianceTableTokens.colApp, TextAlign.Center)
        HeaderCell(stringResource(R.string.overlay_alliance_col_name), AllianceTableTokens.colName, TextAlign.Start)
        HeaderCell(stringResource(R.string.overlay_alliance_col_power), AllianceTableTokens.colPower, TextAlign.End)
        HeaderCell(stringResource(R.string.overlay_alliance_col_level), AllianceTableTokens.colLevel, TextAlign.End)
        HeaderCell(stringResource(R.string.overlay_alliance_col_castle), AllianceTableTokens.colCastle, TextAlign.End)
        HeaderCell(stringResource(R.string.overlay_alliance_col_rank), AllianceTableTokens.colRank, TextAlign.End)
        HeaderCell(stringResource(R.string.overlay_alliance_col_kills), AllianceTableTokens.colKills, TextAlign.End)
        HeaderCell(stringResource(R.string.overlay_alliance_col_seen), AllianceTableTokens.colSeen, TextAlign.End)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign) {
    Text(
        text = text,
        fontFamily = OverlayOnlineMemberTokens.InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        color = OverlayOnlineMemberTokens.mutedColor,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun AllianceMemberRow(
    member: AllianceMember,
    even: Boolean,
    hasApp: Boolean,
    onFly: () -> Unit,
) {
    val tokens = OverlayOnlineMemberTokens
    Column {
        Row(
            modifier = Modifier
                .width(AllianceTableTokens.total)
                .background(if (even) AllianceTableTokens.rowEven else AllianceTableTokens.rowOdd)
                .padding(horizontal = AllianceTableTokens.rowPaddingH, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(AllianceTableTokens.colApp),
                contentAlignment = Alignment.Center,
            ) {
                AppBadgeDot(hasApp = hasApp, size = 10.dp)
            }
            Column(
                modifier = Modifier.width(AllianceTableTokens.colName),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = member.name,
                    fontFamily = tokens.InterFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = tokens.titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.hasCoords) {
                    Text(
                        text = formatCoords(member),
                        fontFamily = tokens.InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = AllianceTableTokens.coordText,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onFly,
                            )
                            .background(AllianceTableTokens.coordFill, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                } else {
                    Text(
                        text = "—",
                        fontFamily = tokens.InterFamily,
                        fontSize = 10.sp,
                        color = tokens.mutedColor,
                    )
                }
            }
            ValueCell(formatCompact(member.power), AllianceTableTokens.colPower, TextAlign.End, strong = true)
            ValueCell(member.level.takeIf { it > 0 }?.toString() ?: "—", AllianceTableTokens.colLevel, TextAlign.End)
            ValueCell(member.castle.takeIf { it > 0 }?.toString() ?: "—", AllianceTableTokens.colCastle, TextAlign.End)
            ValueCell(member.rank.takeIf { it > 0 }?.toString() ?: "—", AllianceTableTokens.colRank, TextAlign.End)
            ValueCell(formatCompact(member.kills), AllianceTableTokens.colKills, TextAlign.End)
            ValueCell(formatLastSeen(member.logoutMs), AllianceTableTokens.colSeen, TextAlign.End)
        }
        Box(
            modifier = Modifier
                .width(AllianceTableTokens.total)
                .height(1.dp)
                .background(AllianceTableTokens.divider),
        )
    }
}

@Composable
private fun ValueCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign,
    strong: Boolean = false,
) {
    Text(
        text = text,
        fontFamily = OverlayOnlineMemberTokens.InterFamily,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        color = if (strong) OverlayOnlineMemberTokens.titleColor else OverlayOnlineMemberTokens.metaColor,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun AppBadgeDot(hasApp: Boolean, size: androidx.compose.ui.unit.Dp) {
    if (hasApp) {
        Box(
            modifier = Modifier
                .size(size)
                .background(AllianceTableTokens.appOn, CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .border(1.dp, AllianceTableTokens.appOff, CircleShape),
        )
    }
}

private fun formatCoords(member: AllianceMember): String {
    val sPart = member.sid.takeIf { it > 0 }?.let { "S:$it " } ?: ""
    return "${sPart}X:${member.x} Y:${member.y}"
}

private fun formatCompact(value: Long): String {
    if (value <= 0L) return "—"
    return when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatLastSeen(logoutMs: Long): String {
    if (logoutMs <= 0L) return "—"
    val diff = System.currentTimeMillis() - logoutMs
    if (diff < 60_000L) return "сейчас"
    val minutes = diff / 60_000L
    if (minutes < 60L) return "${minutes}м"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}ч"
    val days = hours / 24L
    return "${days}д"
}
