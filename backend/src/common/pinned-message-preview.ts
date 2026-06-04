import { Types } from 'mongoose';
import { parseStickerOnlyMessage } from '../chat/sticker-wire.util';

/** Compact pinned message snapshot for room/topic list and pin bar UI. */
export type PinnedMessagePreview = {
  id: string;
  text: string;
  senderUsername: string;
  senderTeamTag: string | null;
  senderServerNumber: number | null;
  createdAt: string;
  editedAt: string | null;
  hasImage: boolean;
  isSticker: boolean;
};

export function buildPinnedPreviewFromChatMessage(msg: {
  _id: Types.ObjectId | string;
  text?: string;
  senderUsername: string;
  senderTeamTag?: string | null;
  senderServerNumber?: number | null;
  createdAt?: Date | null;
  editedAt?: Date | null;
  attachments?: { kind: string }[];
}): PinnedMessagePreview {
  const text = (msg.text ?? '').trim();
  return {
    id: String(msg._id),
    text,
    senderUsername: msg.senderUsername,
    senderTeamTag: msg.senderTeamTag?.trim() || null,
    senderServerNumber:
      typeof msg.senderServerNumber === 'number' ? msg.senderServerNumber : null,
    createdAt: msg.createdAt?.toISOString() ?? new Date().toISOString(),
    editedAt: msg.editedAt?.toISOString() ?? null,
    hasImage: (msg.attachments ?? []).some((a) => a.kind === 'image'),
    isSticker: parseStickerOnlyMessage(text) != null,
  };
}

export function buildPinnedPreviewFromForumMessage(msg: {
  _id: Types.ObjectId | string;
  text?: string;
  senderUsername: string;
  senderTeamTag?: string | null;
  senderServerNumber?: number | null;
  createdAt?: Date | null;
  editedAt?: Date | null;
  imageFileId?: Types.ObjectId | null;
  imageFileIds?: Types.ObjectId[];
}): PinnedMessagePreview {
  const text = (msg.text ?? '').trim();
  const hasImage =
    msg.imageFileId != null || (msg.imageFileIds?.length ?? 0) > 0;
  return {
    id: String(msg._id),
    text,
    senderUsername: msg.senderUsername,
    senderTeamTag: msg.senderTeamTag?.trim() || null,
    senderServerNumber:
      typeof msg.senderServerNumber === 'number' ? msg.senderServerNumber : null,
    createdAt: msg.createdAt?.toISOString() ?? new Date().toISOString(),
    editedAt: msg.editedAt?.toISOString() ?? null,
    hasImage,
    isSticker: parseStickerOnlyMessage(text) != null,
  };
}
