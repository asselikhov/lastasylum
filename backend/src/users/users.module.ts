import { Module, forwardRef } from '@nestjs/common';
import { ChatModule } from '../chat/chat.module';
import { MongooseModule } from '@nestjs/mongoose';
import { TeamForumSocketModule } from './team-forum-socket.module';
import { AdminAlliancesController } from './admin-alliances.controller';
import { AllianceRegistryService } from './alliance-registry.service';
import {
  AllianceRegistry,
  AllianceRegistrySchema,
} from './schemas/alliance-registry.schema';
import { PlayerTeam, PlayerTeamSchema } from './schemas/player-team.schema';
import {
  TeamJoinRequest,
  TeamJoinRequestSchema,
} from './schemas/team-join-request.schema';
import { User, UserSchema } from './schemas/user.schema';
import { AdminTeamsController } from './admin-teams.controller';
import { TeamsController } from './teams.controller';
import { TeamsService } from './teams.service';
import { GameIdentitiesService } from './game-identities.service';
import { UsersController } from './users.controller';
import { UsersService } from './users.service';

import { StorageModule } from '../storage/storage.module';
import {
  TeamNews,
  TeamNewsSchema,
} from './schemas/team-news.schema';
import {
  TeamNewsAttachment,
  TeamNewsAttachmentSchema,
} from './schemas/team-news-attachment.schema';
import { TeamNewsService } from './team-news.service';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import {
  TeamForumMessage,
  TeamForumMessageSchema,
} from './schemas/team-forum-message.schema';
import {
  TeamForumTopic,
  TeamForumTopicSchema,
} from './schemas/team-forum-topic.schema';
import {
  TeamForumTopicReadState,
  TeamForumTopicReadStateSchema,
} from './schemas/team-forum-topic-read-state.schema';
import { TeamForumService } from './team-forum.service';
import {
  AllianceStickerRoleGrant,
  AllianceStickerRoleGrantSchema,
} from './schemas/alliance-sticker-role-grant.schema';
import {
  AllianceStickerUserGrant,
  AllianceStickerUserGrantSchema,
} from './schemas/alliance-sticker-user-grant.schema';
import { AdminStickerAccessController } from './admin-sticker-access.controller';
import { StickerAccessService } from './sticker-access.service';

@Module({
  imports: [
    StorageModule,
    forwardRef(() => ChatModule),
    forwardRef(() => TeamForumSocketModule),
    MongooseModule.forFeature([
      { name: User.name, schema: UserSchema },
      { name: AllianceRegistry.name, schema: AllianceRegistrySchema },
      { name: PlayerTeam.name, schema: PlayerTeamSchema },
      { name: TeamJoinRequest.name, schema: TeamJoinRequestSchema },
      { name: TeamNews.name, schema: TeamNewsSchema },
      { name: TeamNewsAttachment.name, schema: TeamNewsAttachmentSchema },
      { name: TeamForumTopic.name, schema: TeamForumTopicSchema },
      { name: TeamForumMessage.name, schema: TeamForumMessageSchema },
      {
        name: TeamForumTopicReadState.name,
        schema: TeamForumTopicReadStateSchema,
      },
      { name: AllianceStickerRoleGrant.name, schema: AllianceStickerRoleGrantSchema },
      {
        name: AllianceStickerUserGrant.name,
        schema: AllianceStickerUserGrantSchema,
      },
    ]),
  ],
  controllers: [
    UsersController,
    AdminAlliancesController,
    AdminStickerAccessController,
    TeamsController,
    AdminTeamsController,
  ],
  providers: [
    UsersService,
    GameIdentitiesService,
    AllianceRegistryService,
    TeamsService,
    TeamNewsService,
    TeamNewsAttachmentsService,
    TeamForumService,
    StickerAccessService,
  ],
  exports: [
    UsersService,
    GameIdentitiesService,
    TeamsService,
    MongooseModule,
    TeamForumService,
    StickerAccessService,
  ],
})
export class UsersModule {}
