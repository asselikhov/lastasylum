package com.lastasylum.alliance.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.game.GameChatNavigator
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private object AllianceRowTokens {
    val rowEven = Color(0xFF141C28)
    val rowOdd = Color(0xFF111824)
    val divider = Color(0x14FFFFFF)
    val coordFill = PremiumColors.accentCyan.copy(alpha = 0.16f)
    val coordText = PremiumColors.accentCyan
    val levelText = Color(0xFFFFC34D)
    val appOn = PremiumColors.accentCyan
    val appOff = Color(0xFF3A4658)
}

/** Критерий сортировки списка участников альянса. */
private enum class AllianceSort(val labelRes: Int) {
    ONLINE(R.string.overlay_alliance_sort_online),
    POWER(R.string.overlay_alliance_sort_power),
    KILLS(R.string.overlay_alliance_sort_kills),
    LEVEL(R.string.overlay_alliance_sort_level),
}

/**
 * Окно «Участники альянса»: компактный список всего ростера из игры в стиле комнаты «Рейд».
 * Уровень — перед ником, мощь и поверженные — иконками сразу после ника. Тап по координатам —
 * перелёт к соалийцу на карте, кнопка «Чат» — личные сообщения с игроком в игре, у каждого —
 * бейдж наличия приложения.
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
    var sort by remember { mutableStateOf(AllianceSort.POWER) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            AllianceRosterCache.peek().takeIf { it.isNotEmpty() }
                ?: AllianceRosterCache.parse(
                    AppContainer.from(context).userSettingsPreferences.getAllianceRosterJson(),
                ).also { if (it.isNotEmpty()) AllianceRosterCache.update(it) }
        }
        roster = loaded
        appNames = OverlayTeamContextCache.peekCachedTeam()
            ?.members
            ?.map { it.username.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    val visible = remember(roster, query, sort) {
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) roster else roster.filter { it.name.lowercase().contains(q) }
        val nowMs = System.currentTimeMillis()
        val primary = when (sort) {
            AllianceSort.ONLINE -> compareByDescending<AllianceMember> { isAllianceMemberOnline(it.logoutMs, nowMs) }
                .thenByDescending { it.logoutMs }
            AllianceSort.POWER -> compareByDescending<AllianceMember> { it.power }
            AllianceSort.KILLS -> compareByDescending<AllianceMember> { it.kills }
            AllianceSort.LEVEL -> compareByDescending<AllianceMember> { it.level }
        }
        base.sortedWith(primary.thenBy { it.name.lowercase() })
    }

    val listState = rememberLazyListState()
    LaunchedEffect(sort, query) {
        if (visible.isNotEmpty()) {
            listState.scrollToItem(0)
        }
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

                OverlayHudFilterSearchRow(
                    selectedFilter = sort,
                    filterOptions = AllianceSort.entries.toList(),
                    filterLabelFor = { stringResource(it.labelRes) },
                    onFilterSelect = { sort = it },
                    searchQuery = query,
                    onSearchQuery = { query = it },
                    searchHint = stringResource(R.string.overlay_online_search_hint),
                    searchClearContentDescription = stringResource(R.string.team_members_search_clear_cd),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )

                Spacer(Modifier.height(6.dp))

                if (visible.isEmpty()) {
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
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        itemsIndexed(visible, key = { _, m -> m.id }) { index, m ->
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
                                onChat = {
                                    onCloseAllOverlays()
                                    GameChatNavigator.openPrivateChat(
                                        context = context,
                                        playerId = m.id,
                                        playerName = m.name,
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

@Composable
private fun AllianceMemberRow(
    member: AllianceMember,
    even: Boolean,
    hasApp: Boolean,
    onFly: () -> Unit,
    onChat: () -> Unit,
) {
    val tokens = OverlayOnlineMemberTokens
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (even) AllianceRowTokens.rowEven else AllianceRowTokens.rowOdd)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            // Строка 1: бейдж приложения · Ур.N · Ник · ⚡Мощь · ⚔Поверженные
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadgeDot(hasApp = hasApp, size = 9.dp)
                Spacer(Modifier.width(7.dp))
                if (member.level > 0) {
                    Text(
                        text = "\u0423\u0440.${member.level}",
                        fontFamily = tokens.InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AllianceRowTokens.levelText,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = member.name,
                    fontFamily = tokens.InterFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = tokens.titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.power > 0) {
                    Spacer(Modifier.width(8.dp))
                    StatIcon(R.drawable.ic_overlay_game_power, formatCompact(member.power), strong = true)
                }
                if (member.kills > 0) {
                    Spacer(Modifier.width(8.dp))
                    StatIcon(R.drawable.ic_overlay_game_kills, formatCompact(member.kills))
                }
            }

            Spacer(Modifier.height(5.dp))

            // Строка 2: координаты (перелёт) · кнопка «Чат» · справа — был в сети
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.hasCoords) {
                    Text(
                        text = formatCoords(member),
                        fontFamily = tokens.InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = AllianceRowTokens.coordText,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onFly,
                            )
                            .background(AllianceRowTokens.coordFill, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                } else {
                    Text(
                        text = "\u2014",
                        fontFamily = tokens.InterFamily,
                        fontSize = 11.sp,
                        color = tokens.mutedColor,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onChat,
                        )
                        .background(AllianceRowTokens.coordFill, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = stringResource(R.string.overlay_alliance_chat_cd),
                        tint = AllianceRowTokens.coordText,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatLastSeen(member.logoutMs),
                    fontFamily = tokens.InterFamily,
                    fontSize = 10.5.sp,
                    color = tokens.mutedColor,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AllianceRowTokens.divider),
        )
    }
}

/** Иконка статистики (мощь/поверженные) + компактное значение, как в комнате «Рейд». */
@Composable
private fun StatIcon(iconRes: Int, value: String, strong: Boolean = false) {
    val tokens = OverlayOnlineMemberTokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = value,
            fontFamily = tokens.InterFamily,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp,
            color = if (strong) tokens.titleColor else tokens.metaColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun AppBadgeDot(hasApp: Boolean, size: androidx.compose.ui.unit.Dp) {
    if (hasApp) {
        Box(
            modifier = Modifier
                .size(size)
                .background(AllianceRowTokens.appOn, CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .border(1.dp, AllianceRowTokens.appOff, CircleShape),
        )
    }
}

private fun formatCoords(member: AllianceMember): String {
    val sPart = member.sid.takeIf { it > 0 }?.let { "S:$it " } ?: ""
    return "${sPart}X:${member.x} Y:${member.y}"
}

private fun formatCompact(value: Long): String {
    if (value <= 0L) return "\u2014"
    return when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun isAllianceMemberOnline(logoutMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (logoutMs <= 0L) return true
    return nowMs - logoutMs < 60_000L
}

private fun formatLastSeen(logoutMs: Long): String {
    if (logoutMs <= 0L) return "\u0432 \u0441\u0435\u0442\u0438"
    if (isAllianceMemberOnline(logoutMs)) return "\u0441\u0435\u0439\u0447\u0430\u0441"
    val diff = System.currentTimeMillis() - logoutMs
    val minutes = diff / 60_000L
    if (minutes < 60L) return "${minutes}\u043c"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}\u0447"
    val days = hours / 24L
    return "${days}\u0434"
}
