package com.lastasylum.alliance.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer

/** Участники альянса для выбора в планировщике маршрутов. */
object RoutePlannerAllianceMembers {
    fun load(context: Context): List<AllianceMember> {
        val fromCache = AllianceRosterCache.peek()
        if (fromCache.isNotEmpty()) return fromCache.sortedBy { it.name.lowercase() }
        return AllianceRosterCache.parse(
            AppContainer.from(context).userSettingsPreferences.getAllianceRosterJson(),
        ).sortedBy { it.name.lowercase() }
    }
}

@Composable
fun RoutePlannerMemberPickerList(
    members: List<AllianceMember>,
    selectedMemberId: String?,
    onSelect: (String) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 160,
) {
    if (members.isEmpty()) {
        Text(
            text = emptyText,
            color = ComposeColor(0xFF78909C),
            fontSize = 12.sp,
        )
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp.dp),
    ) {
        items(members, key = { it.id }) { member ->
            val selected = member.id == selectedMemberId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) ComposeColor(0xFF3949AB) else ComposeColor(0xFF1A222E))
                    .border(
                        1.dp,
                        if (selected) ComposeColor(0xFF7986CB) else ComposeColor(0x33445566),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(member.id) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = member.name,
                    color = if (selected) ComposeColor.White else ComposeColor(0xFFB0BEC5),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

fun routePlannerMemberEmptyText(context: Context): String =
    context.getString(R.string.overlay_route_assign_no_members)
