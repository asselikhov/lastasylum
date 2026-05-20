import { Module, forwardRef } from '@nestjs/common';
import { ChatModule } from '../chat/chat.module';
import { MongooseModule } from '@nestjs/mongoose';
import { TeamForumSocketModule } from './team-forum-socket.module';
import { AdminModule } from './admin.module';
import { ForumModule } from './forum.module';
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
  AllianceStickerRoleGrant,
  AllianceStickerRoleGrantSchema,
} from './schemas/alliance-sticker-role-grant.schema';
import {
  AllianceStickerUserGrant,
  AllianceStickerUserGrantSchema,
} from './schemas/alliance-sticker-user-grant.schema';
import { StickerAccessService } from './sticker-access.service';

@Module({
  imports: [
    StorageModule,
    forwardRef(() => ChatModule),
    forwardRef(() => TeamForumSocketModule),
    forwardRef(() => AdminModule),
    ForumModule,
    MongooseModule.forFeature([
      { name: User.name, schema: UserSchema },
      { name: AllianceRegistry.name, schema: AllianceRegistrySchema },
      { name: PlayerTeam.name, schema: PlayerTeamSchema },
      { name: TeamJoinRequest.name, schema: TeamJoinRequestSchema },
      { name: TeamNews.name, schema: TeamNewsSchema },
      { name: TeamNewsAttachment.name, schema: TeamNewsAttachmentSchema },
      { name: AllianceStickerRoleGrant.name, schema: AllianceStickerRoleGrantSchema },
      {
        name: AllianceStickerUserGrant.name,
        schema: AllianceStickerUserGrantSchema,
      },
    ]),
  ],
  controllers: [UsersController, TeamsController],
  providers: [
    UsersService,
    GameIdentitiesService,
    AllianceRegistryService,
    TeamsService,
    TeamNewsService,
    TeamNewsAttachmentsService,
    StickerAccessService,
  ],
  exports: [
    UsersService,
    GameIdentitiesService,
    AllianceRegistryService,
    TeamsService,
    TeamNewsService,
    TeamNewsAttachmentsService,
    MongooseModule,
    ForumModule,
    StickerAccessService,
  ],
})
export class UsersModule {}
