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
  /** Relative API path for first image attachment (chat/forum). */
  imageThumbnailUrl: string | null;
  /** Username of the officer who pinned (when known). */
  pinnedByUsername: string | null;
};

function firstChatImageThumbnailUrl(
  attachments?: { kind: string; fileId?: Types.ObjectId | string }[],
): string | null {
  const img = (attachments ?? []).find((a) => a.kind === 'image');
  const fileId = img?.fileId?.toString()?.trim();
  if (!fileId) return null;
  return `/chat/attachments/${fileId}`;
}

function firstForumImageThumbnailUrl(msg: {
  teamId: Types.ObjectId | string;
  imageFileId?: Types.ObjectId | null;
  imageFileIds?: Types.ObjectId[];
}): string | null {
  const teamIdStr = String(msg.teamId);
  const album = Array.isArray(msg.imageFileIds) ? msg.imageFileIds : [];
  const firstAlbum = album[0]?.toString()?.trim();
  if (firstAlbum) {
    return `/teams/${teamIdStr}/news/attachments/${firstAlbum}`;
  }
  const legacy = msg.imageFileId?.toString()?.trim();
  if (legacy) {
    return `/teams/${teamIdStr}/news/attachments/${legacy}`;
  }
  return null;
}

/** Placeholder when the pinned message row is missing (deleted / not loaded). */
export function buildStubPinnedPreview(
  messageId: string,
  pinnedByUsername: string | null = null,
): PinnedMessagePreview {
  const id = messageId.trim();
  return {
    id,
    text: '',
    senderUsername: '',
    senderTeamTag: null,
    senderServerNumber: null,
    createdAt: new Date(0).toISOString(),
    editedAt: null,
    hasImage: false,
    isSticker: false,
    imageThumbnailUrl: null,
    pinnedByUsername: pinnedByUsername?.trim() || null,
  };
}

export function enrichPinnedPreview(
  preview: PinnedMessagePreview,
  pinnedByUsername: string | null | undefined,
): PinnedMessagePreview {
  const name = pinnedByUsername?.trim();
  return {
    ...preview,
    pinnedByUsername: name || null,
  };
}

export function buildPinnedPreviewFromChatMessage(msg: {
  _id: Types.ObjectId | string;
  text?: string;
  senderUsername: string;
  senderTeamTag?: string | null;
  senderServerNumber?: number | null;
  createdAt?: Date | null;
  editedAt?: Date | null;
  attachments?: { kind: string; fileId?: Types.ObjectId | string }[];
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
    imageThumbnailUrl: firstChatImageThumbnailUrl(msg.attachments),
    pinnedByUsername: null,
  };
}

export function buildPinnedPreviewFromForumMessage(msg: {
  _id: Types.ObjectId | string;
  teamId: Types.ObjectId | string;
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
    imageThumbnailUrl: firstForumImageThumbnailUrl(msg),
    pinnedByUsername: null,
  };
}
