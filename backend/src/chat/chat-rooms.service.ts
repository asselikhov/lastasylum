import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/global-chat-alliance-id';
import { ChatRoom, ChatRoomDocument } from './schemas/chat-room.schema';
import { Message, MessageDocument } from './schemas/message.schema';

@Injectable()
export class ChatRoomsService {
  constructor(
    @InjectModel(ChatRoom.name) private readonly roomModel: Model<ChatRoomDocument>,
    @InjectModel(Message.name) private readonly messageModel: Model<MessageDocument>,
  ) {}

  listForAlliance(allianceId: string) {
    return this.roomModel
      .find({ allianceId, archivedAt: null })
      .sort({ sortOrder: 1, title: 1 })
      .lean()
      .exec();
  }

  /**
   * Global "Общий" + this alliance's rooms (excludes legacy per-alliance "Общий" rows).
   */
  async listRoomsVisibleToUser(userAllianceName: string) {
    await this.ensureGlobalGeneralRoom();
    await this.ensureAllianceHubRoom(userAllianceName);
    const [globalRooms, allianceRooms] = await Promise.all([
      this.roomModel
        .find({ allianceId: GLOBAL_CHAT_ALLIANCE_ID, archivedAt: null })
        .sort({ sortOrder: 1, title: 1 })
        .lean()
        .exec(),
      this.roomModel
        .find({
          allianceId: userAllianceName,
          archivedAt: null,
          title: { $ne: 'Общий' },
        })
        .sort({ sortOrder: 1, title: 1 })
        .lean()
        .exec(),
    ]);
    return [...globalRooms, ...allianceRooms];
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
      return existing._id as Types.ObjectId;
    }
    const created = await this.roomModel.create({
      allianceId,
      title: 'Общий',
      sortOrder: 0,
      archivedAt: null,
    });
    return created._id as Types.ObjectId;
  }

  /** Cross-alliance lobby: one "Общий" room for everyone. */
  async ensureGlobalGeneralRoom(): Promise<void> {
    const existing = await this.roomModel
      .findOne({
        allianceId: GLOBAL_CHAT_ALLIANCE_ID,
        title: 'Общий',
        archivedAt: null,
      })
      .exec();
    if (existing) return;
    await this.roomModel.create({
      allianceId: GLOBAL_CHAT_ALLIANCE_ID,
      title: 'Общий',
      sortOrder: 0,
      archivedAt: null,
    });
  }

  /** Internal chat for one alliance (title = alliance key). */
  async ensureAllianceHubRoom(allianceId: string): Promise<void> {
    const existing = await this.roomModel
      .findOne({
        allianceId,
        title: allianceId,
        archivedAt: null,
      })
      .exec();
    if (existing) return;
    await this.roomModel.create({
      allianceId,
      title: allianceId,
      sortOrder: 1,
      archivedAt: null,
    });
  }
}
