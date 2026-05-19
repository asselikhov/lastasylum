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
  GLOBAL_CHAT_ALLIANCE_ID,
  GLOBAL_CHAT_ROOM_TITLE,
} from '../common/constants/global-chat-alliance-id';
import { UsersService } from '../users/users.service';
import { PlayerTeam, PlayerTeamDocument } from '../users/schemas/player-team.schema';
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

  /**
   * «Мир» for everyone; team hub (display name) + «Рейд» only when [user.playerTeamId] is set.
   */
  async listRoomsVisibleToUser(
    user: Pick<User, 'allianceName' | 'playerTeamId'>,
  ) {
    await this.ensureGlobalGeneralRoom();
    const globalRooms = await this.roomModel
      .find({ allianceId: GLOBAL_CHAT_ALLIANCE_ID, archivedAt: null })
      .sort({ sortOrder: 1, title: 1 })
      .lean()
      .exec();

    const teamId = user.playerTeamId?.toString();
    if (!teamId) {
      return globalRooms;
    }

    const chatScope = playerTeamChatAllianceId(teamId);
    await this.ensureAllianceChatRoomsForScope(chatScope);
    const teamRooms = await this.roomModel
      .find({
        allianceId: chatScope,
        archivedAt: null,
        title: { $nin: ['Общий', 'Общая'] },
      })
      .sort({ sortOrder: 1, title: 1 })
      .lean()
      .exec();

    return [...globalRooms, ...teamRooms];
  }

  /** Ensure hub + raid exist for a chat scope (call when someone joins a player team). */
  async ensureAllianceChatRoomsForScope(
    chatScope: string,
    hubTitleHint?: string,
  ): Promise<void> {
    const hubTitle =
      hubTitleHint?.trim() ||
      (await this.resolveHubTitleForScope(chatScope));
    await this.ensureAllianceHubRoom(chatScope, hubTitle);
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

  /** Cross-alliance lobby: one «Мир» room for everyone (legacy titles are renamed). */
  async ensureGlobalGeneralRoom(): Promise<void> {
    const current = await this.roomModel
      .findOne({
        allianceId: GLOBAL_CHAT_ALLIANCE_ID,
        archivedAt: null,
      })
      .exec();
    if (!current) {
      await this.roomModel.create({
        allianceId: GLOBAL_CHAT_ALLIANCE_ID,
        title: GLOBAL_CHAT_ROOM_TITLE,
        sortOrder: 0,
        archivedAt: null,
      });
      return;
    }
    if (
      current.title === 'Общая' ||
      current.title === 'Союз' ||
      current.title !== GLOBAL_CHAT_ROOM_TITLE
    ) {
      current.title = GLOBAL_CHAT_ROOM_TITLE;
      await current.save();
    }
  }

  /**
   * Alliance-only room (sortOrder 1): title follows team display name from members, else alliance key.
   */
  async ensureAllianceHubRoom(
    allianceId: string,
    displayTitleOverride?: string,
  ): Promise<void> {
    const displayTitle =
      displayTitleOverride?.trim() ||
      (await this.resolveHubTitleForScope(allianceId));
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
    if (hub.sortOrder !== 1) {
      hub.sortOrder = 1;
    }
    if (hub.title !== displayTitle) {
      hub.title = displayTitle;
    }
    await hub.save();
  }

  /** Alliance «Рейд» room (sortOrder 2), same access as hub. */
  async ensureAllianceRaidRoom(allianceId: string): Promise<void> {
    let raid = await this.roomModel
      .findOne({ allianceId, title: 'Рейд', archivedAt: null })
      .exec();
    if (!raid) {
      await this.roomModel.create({
        allianceId,
        title: 'Рейд',
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
