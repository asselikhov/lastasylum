package com.lastasylum.alliance.overlay

import android.net.Uri
import androidx.compose.runtime.Composable
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.chat.ChatVoicePhase
import com.lastasylum.alliance.ui.screens.ChatScreen

@Composable
internal fun OverlayHudChatPane(
    chatState: ChatState,
    typingPeers: Map<String, String>,
    draftMessage: String,
    pickedImageUris: List<Uri>,
    chatVoicePhase: ChatVoicePhase,
    otherReadUptoMessageId: String?,
    vm: ChatViewModel,
) {
    ChatScreen(
        state = chatState,
        typingPeers = typingPeers,
        draftMessage = draftMessage,
        pickedImageUris = pickedImageUris,
        chatVoicePhase = chatVoicePhase,
        otherReadUptoMessageId = otherReadUptoMessageId,
        compactOverlayMode = false,
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
        onDismissMessageActions = vm::dismissMessageActions,
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
        onChatVoiceHoldStart = vm::startChatVoiceInput,
        onChatVoiceHoldEnd = vm::stopChatVoiceInput,
        onEditMessage = vm::editMessage,
        onForwardMessage = vm::forwardMessage,
        onToggleReaction = vm::toggleReaction,
    )
}
