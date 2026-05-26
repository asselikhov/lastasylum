import {
  ConflictException,
  forwardRef,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import {
  ALLIANCE_HUB_ROOM_TITLE,
  ALLIANCE_RAID_ROOM_TITLE,
  GLOBAL_CHAT_ALLIANCE_ID,
  GLOBAL_CHAT_ROOM_TITLE,
  formatServerChatRoomTitle,
  serverChatAllianceId,
} from '../common/constants/chat-room-constants';
import { UsersService } from '../users/users.service';
import { resolveUserActiveServerNumber } from './chat-user-server';
import {
  PlayerTeam,
  PlayerTeamDocument,
} from '../users/schemas/player-team.schema';
import {
  isPlayerTeamChatScope,
  parsePlayerTeamIdFromChatScope,
  playerTeamChatAllianceId,
} from './chat-alliance-scope';
import type { User } from '../users/schemas/user.schema';
import { ChatRoom, ChatRoomDocument } from './schemas/chat-room.schema';
import { Message, MessageDocument } from './schemas/message.schema';

@Injectable()
export class ChatRoomsService {
  constructor(
    @InjectModel(ChatRoom.name)
    private readonly roomModel: Model<ChatRoomDocument>,
    @InjectModel(Message.name)
    private readonly messageModel: Model<MessageDocument>,
    @InjectModel(PlayerTeam.name)
    private readonly playerTeamModel: Model<PlayerTeamDocument>,
    @Inject(forwardRef(() => UsersService))
    private readonly usersService: UsersService,
  ) {}

  listForAlliance(allianceId: string) {
    return this.roomModel
      .find({ allianceId, archivedAt: null })
      .sort({ sortOrder: 1, title: 1 })
      .lean()
      .exec();
  }

  /** All non-archived room ids (admin history wipe unread fan-out). */
  async listActiveRoomIds(): Promise<string[]> {
    const rows = await this.roomModel
      .find({ archivedAt: null })
      .select('_id')
      .lean()
      .exec();
    return rows.map((row) => row._id.toString());
  }

  /** R5 admin: chat rooms scoped to a player team (`pt:<teamId>`). */
  async listForPlayerTeamAdmin(teamId: string) {
    if (!Types.ObjectId.isValid(teamId)) {
      return [];
    }
    const chatScope = playerTeamChatAllianceId(teamId);
    await this.ensureAllianceChatRoomsForScope(chatScope);
    return this.roomModel
      .find({ allianceId: chatScope, archivedAt: null })
      .sort({ sortOrder: 1, title: 1 })
      .lean()
      .exec();
  }

  /**
   * «Межсерв» + комната сервера (#n) for everyone; «Альянс» + «Рейд» when [user.playerTeamId] is set.
   */
  async listRoomsVisibleToUser(
    user: Pick<
      User,
      | 'allianceName'
      | 'playerTeamId'
      | 'gameIdentities'
      | 'activeGameIdentityId'
    >,
  ) {
    const publicRooms = await this.listPublicRoomsForUser(user);

    const teamId = user.playerTeamId?.toString();
    if (!teamId) {
      return publicRooms;
    }

    const chatScope = playerTeamChatAllianceId(teamId);
    await this.ensureAllianceChatRoomsForScope(chatScope);
    const teamRooms = await this.roomModel
      .find({
        allianceId: chatScope,
        archivedAt: null,
        title: { $in: [ALLIANCE_HUB_ROOM_TITLE, ALLIANCE_RAID_ROOM_TITLE] },
      })
      .sort({ sortOrder: 1, title: 1 })
      .lean()
      .exec();

    return [...publicRooms, ...teamRooms];
  }

  /** «Межсерв» and active-server room visible to every active chat user. */
  private async listPublicRoomsForUser(
    user: Pick<User, 'gameIdentities' | 'activeGameIdentityId'>,
  ) {
    await this.ensureGlobalGeneralRoom();
    const globalRoom =
      (await this.roomModel
        .findOne({
          allianceId: GLOBAL_CHAT_ALLIANCE_ID,
          title: GLOBAL_CHAT_ROOM_TITLE,
          archivedAt: null,
        })
        .lean()
        .exec()) ??
      (await this.roomModel
        .findOne({ allianceId: GLOBAL_CHAT_ALLIANCE_ID, archivedAt: null })
        .sort({ sortOrder: 1 })
        .lean()
        .exec());

    const publicRooms = globalRoom ? [globalRoom] : [];

    const serverNumber = resolveUserActiveServerNumber(user);
    if (serverNumber == null) {
      return publicRooms;
    }

    await this.ensureServerRoom(serverNumber);
    const serverRoom = await this.roomModel
      .findOne({
        allianceId: serverChatAllianceId(serverNumber),
        archivedAt: null,
      })
      .lean()
      .exec();

    if (!serverRoom) {
      return publicRooms;
    }
    return [...publicRooms, serverRoom];
  }

  /** Ensure hub + raid exist for a chat scope (call when someone joins a player team). */
  async ensureAllianceChatRoomsForScope(
    chatScope: string,
    _hubTitleHint?: string,
  ): Promise<void> {
    await this.ensureAllianceHubRoom(chatScope);
    await this.ensureAllianceRaidRoom(chatScope);
  }

  private async resolveHubTitleForScope(chatScope: string): Promise<string> {
    const teamId = parsePlayerTeamIdFromChatScope(chatScope);
    if (teamId) {
      const team = await this.playerTeamModel
        .findById(teamId)
        .select('displayName')
        .lean<{ displayName?: string }>()
        .exec();
      const t = team?.displayName?.trim();
      if (t) return t.slice(0, 64);
    }
    if (!isPlayerTeamChatScope(chatScope)) {
      return this.usersService.resolveAllianceChatHubTitle(chatScope);
    }
    return chatScope;
  }

  findById(roomId: string) {
    if (!Types.ObjectId.isValid(roomId)) {
      return Promise.resolve(null);
    }
    return this.roomModel.findById(roomId).exec();
  }

  async createRoom(allianceId: string, title: string, sortOrder?: number) {
    const trimmed = title.trim();
    if (!trimmed) {
      throw new ConflictException('Title is required');
    }
    let nextOrder = sortOrder;
    if (nextOrder === undefined || nextOrder === null) {
      const max = await this.roomModel
        .findOne({ allianceId })
        .sort({ sortOrder: -1 })
        .select('sortOrder')
        .lean()
        .exec();
      nextOrder = max ? max.sortOrder + 1 : 0;
    }
    return this.roomModel.create({
      allianceId,
      title: trimmed,
      sortOrder: nextOrder,
      archivedAt: null,
    });
  }

  async updateRoom(
    roomId: string,
    allianceId: string,
    patch: { title?: string; sortOrder?: number; archived?: boolean },
  ) {
    const room = await this.roomModel.findById(roomId).exec();
    if (!room || room.allianceId !== allianceId) {
      throw new NotFoundException('Room not found');
    }
    if (patch.title !== undefined) {
      const t = patch.title.trim();
      if (!t) {
        throw new ConflictException('Title is required');
      }
      room.title = t;
    }
    if (patch.sortOrder !== undefined) {
      room.sortOrder = patch.sortOrder;
    }
    if (patch.archived === true) {
      room.archivedAt = new Date();
    } else if (patch.archived === false) {
      room.archivedAt = null;
    }
    await room.save();
    return room;
  }

  async deleteRoom(roomId: string, allianceId: string) {
    const room = await this.roomModel.findById(roomId).exec();
    if (!room || room.allianceId !== allianceId) {
      throw new NotFoundException('Room not found');
    }
    const count = await this.messageModel.countDocuments({
      roomId: new Types.ObjectId(roomId),
    });
    if (count > 0) {
      throw new ConflictException('Room has messages and cannot be deleted');
    }
    await this.roomModel.deleteOne({ _id: roomId, allianceId }).exec();
  }

  async ensureDefaultGeneralRoom(allianceId: string): Promise<Types.ObjectId> {
    const existing = await this.roomModel
      .findOne({ allianceId, title: 'Общий', archivedAt: null })
      .exec();
    if (existing) {
      return existing._id;
    }
    const created = await this.roomModel.create({
      allianceId,
      title: 'Общий',
      sortOrder: 0,
      archivedAt: null,
    });
    return created._id;
  }

  /** Cross-server lobby: one «Межсерв» room (legacy duplicates archived). */
  async ensureGlobalGeneralRoom(): Promise<void> {
    const legacyGlobalTitles = ['Мир', 'Общая', 'Союз'];
    const globals = await this.roomModel
      .find({ allianceId: GLOBAL_CHAT_ALLIANCE_ID, archivedAt: null })
      .sort({ sortOrder: 1, createdAt: 1 })
      .exec();
    const hub =
      globals.find((r) => r.title === GLOBAL_CHAT_ROOM_TITLE) ??
      globals.find((r) => legacyGlobalTitles.includes(r.title)) ??
      globals[0];
    if (!hub) {
      await this.roomModel.create({
        allianceId: GLOBAL_CHAT_ALLIANCE_ID,
        title: GLOBAL_CHAT_ROOM_TITLE,
        sortOrder: 0,
        archivedAt: null,
      });
      return;
    }
    if (legacyGlobalTitles.includes(hub.title)) {
      hub.title = GLOBAL_CHAT_ROOM_TITLE;
    }
    if (hub.sortOrder !== 0) {
      hub.sortOrder = 0;
    }
    await hub.save();
    const now = new Date();
    for (const extra of globals) {
      if (extra._id.equals(hub._id)) continue;
      extra.archivedAt = now;
      await extra.save();
    }
  }

  /** Same-server lobby: one `#<n>` room per game server. */
  async ensureServerRoom(serverNumber: number): Promise<void> {
    const server = Math.max(1, Math.floor(serverNumber));
    const allianceId = serverChatAllianceId(server);
    const title = formatServerChatRoomTitle(server);
    const current = await this.roomModel
      .findOne({ allianceId, archivedAt: null })
      .exec();
    if (!current) {
      await this.roomModel.create({
        allianceId,
        title,
        sortOrder: 0,
        archivedAt: null,
      });
      return;
    }
    if (current.title !== title) {
      current.title = title;
      await current.save();
    }
  }

  /**
   * Overlay team voice: hub room for the user's player team (`pt:<teamId>`).
   * Clients may pass the sentinel `team` on the voice socket instead of a Mongo room id.
   */
  async findTeamVoiceRoomIdForUser(
    user: Pick<User, 'playerTeamId'>,
  ): Promise<string> {
    const teamId = user.playerTeamId?.toString().trim() ?? '';
    if (!teamId || !Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Player team required for voice');
    }
    const allianceId = playerTeamChatAllianceId(teamId);
    await this.ensureAllianceChatRoomsForScope(allianceId);
    const hub = await this.roomModel
      .findOne({ allianceId, sortOrder: 1, archivedAt: null })
      .select('_id')
      .lean<{ _id: Types.ObjectId }>()
      .exec();
    if (!hub?._id) {
      throw new NotFoundException('Team voice channel is not available');
    }
    return hub._id.toString();
  }

  /** Team hub (sortOrder 1): fixed title «Альянс». */
  async ensureAllianceHubRoom(allianceId: string): Promise<void> {
    const displayTitle = ALLIANCE_HUB_ROOM_TITLE;
    let hub = await this.roomModel
      .findOne({ allianceId, sortOrder: 1, archivedAt: null })
      .exec();
    if (!hub) {
      hub = await this.roomModel
        .findOne({ allianceId, title: allianceId, archivedAt: null })
        .exec();
    }
    if (!hub) {
      await this.roomModel.create({
        allianceId,
        title: displayTitle,
        sortOrder: 1,
        archivedAt: null,
      });
      return;
    }
    let hubDirty = false;
    if (hub.sortOrder !== 1) {
      hub.sortOrder = 1;
      hubDirty = true;
    }
    if (hub.title !== displayTitle) {
      hub.title = displayTitle;
      hubDirty = true;
    }
    if (hubDirty) {
      await hub.save();
    }
  }

  /** Alliance «Рейд» room (sortOrder 2), same access as hub. */
  async ensureAllianceRaidRoom(allianceId: string): Promise<void> {
    const raid = await this.roomModel
      .findOne({
        allianceId,
        title: ALLIANCE_RAID_ROOM_TITLE,
        archivedAt: null,
      })
      .exec();
    if (!raid) {
      await this.roomModel.create({
        allianceId,
        title: ALLIANCE_RAID_ROOM_TITLE,
        sortOrder: 2,
        archivedAt: null,
      });
      return;
    }
    if (raid.sortOrder !== 2) {
      raid.sortOrder = 2;
      await raid.save();
    }
  }
}
