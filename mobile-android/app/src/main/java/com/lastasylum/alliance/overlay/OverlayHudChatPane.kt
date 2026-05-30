package com.lastasylum.alliance.overlay

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastasylum.alliance.ui.chat.ChatChromePaneState
import com.lastasylum.alliance.ui.chat.ChatComposerPaneState
import com.lastasylum.alliance.ui.chat.ChatListPaneState
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.screens.ChatScreen

@Composable
internal fun OverlayHudChatPane(
    listPane: ChatListPaneState,
    chromePane: ChatChromePaneState,
    composerPane: ChatComposerPaneState,
    typingPeers: Map<String, String>,
    draftMessage: String,
    pickedImageUris: List<Uri>,
    otherReadUptoMessageId: String?,
    vm: ChatViewModel,
) {
    val listDerived by vm.listDerived.collectAsStateWithLifecycle()
    ChatScreen(
        listPane = listPane,
        chromePane = chromePane,
        composerPane = composerPane,
        listDerived = listDerived,
        typingPeers = typingPeers,
        draftMessage = draftMessage,
        pickedImageUris = pickedImageUris,
        otherReadUptoMessageId = otherReadUptoMessageId,
        compactOverlayMode = true,
        onMessageListScrollInProgress = vm::setMessageListScrollInProgress,
        onSelectRoom = vm::selectRoom,
        onClearError = vm::clearError,
        onLoadOlder = vm::loadOlderMessages,
        onDraftChange = vm::setDraftMessage,
        onSendDraft = vm::sendDraftMessage,
        onSendStickerPayload = vm::sendMessage,
        onPickImages = { uris, append -> vm.onImagesPicked(uris, append) },
        onRemovePickedImage = vm::removePickedImage,
        onClearPickedImages = vm::clearPickedImages,
        onReplyToMessage = vm::beginReplyToMessage,
        onClearReply = vm::clearReplyToMessage,
        onOpenMessageActions = vm::openMessageActions,
        onDismissMessageActions = {
            vm.dismissMessageActions()
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        },
        onRequestDeleteMessage = vm::requestDeleteMessage,
        onDismissDeleteMessage = vm::dismissDeleteMessage,
        onConfirmDeleteMessage = vm::confirmDeleteMessage,
        onBeginMessageSelection = vm::beginMessageSelection,
        onToggleMessageSelection = vm::toggleMessageSelection,
        onClearMessageSelection = vm::clearMessageSelection,
        onRequestBulkDelete = vm::requestBulkDelete,
        onDismissBulkDeleteConfirm = vm::dismissBulkDeleteConfirm,
        onConfirmDeleteSelectedMessages = vm::confirmDeleteSelectedMessages,
        onRetrySendFailure = vm::retrySendFailure,
        onDismissSendFailure = vm::dismissSendFailure,
        onEditMessage = vm::editMessage,
        onForwardMessage = vm::forwardMessage,
        onToggleReaction = vm::toggleReaction,
        onScrollToLatest = vm::scrollToLatestMessages,
        onJumpToQuotedMessage = vm::jumpToQuotedMessage,
        onConsumeScrollToMessage = vm::consumeScrollToMessage,
        onClearHighlightMessage = vm::clearHighlightMessage,
        onConsumeTransientNotice = vm::consumeTransientNotice,
        messageListKey = vm::messageListCompositionKey,
    )
}
