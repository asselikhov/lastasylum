import { Module, forwardRef } from '@nestjs/common';
import { APP_INTERCEPTOR } from '@nestjs/core';
import { AppVersionInterceptor } from '../common/app-version.interceptor';
import { ChatModule } from '../chat/chat.module';
import { MongooseModule } from '@nestjs/mongoose';
import { TeamForumSocketModule } from './team-forum-socket.module';
import { TeamPresenceSocketModule } from './team-presence-socket.module';
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
import { TeamNews, TeamNewsSchema } from './schemas/team-news.schema';
import {
  TeamNewsAttachment,
  TeamNewsAttachmentSchema,
} from './schemas/team-news-attachment.schema';
import {
  TeamNewsReadState,
  TeamNewsReadStateSchema,
} from './schemas/team-news-read-state.schema';
import { TeamNewsService } from './team-news.service';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { TeamRoutePlannerService } from './team-route-planner.service';
import {
  TeamRoutePlanner,
  TeamRoutePlannerSchema,
} from './schemas/team-route-planner.schema';
import {
  AllianceStickerRoleGrant,
  AllianceStickerRoleGrantSchema,
} from './schemas/alliance-sticker-role-grant.schema';
import {
  AllianceStickerUserGrant,
  AllianceStickerUserGrantSchema,
} from './schemas/alliance-sticker-user-grant.schema';
import { StickerAccessService } from './sticker-access.service';
import { UserAvatarService } from './user-avatar.service';

@Module({
  imports: [
    StorageModule,
    forwardRef(() => ChatModule),
    forwardRef(() => TeamForumSocketModule),
    forwardRef(() => TeamPresenceSocketModule),
    forwardRef(() => AdminModule),
    ForumModule,
    MongooseModule.forFeature([
      { name: User.name, schema: UserSchema },
      { name: AllianceRegistry.name, schema: AllianceRegistrySchema },
      { name: PlayerTeam.name, schema: PlayerTeamSchema },
      { name: TeamJoinRequest.name, schema: TeamJoinRequestSchema },
      { name: TeamNews.name, schema: TeamNewsSchema },
      { name: TeamNewsAttachment.name, schema: TeamNewsAttachmentSchema },
      { name: TeamNewsReadState.name, schema: TeamNewsReadStateSchema },
      { name: TeamRoutePlanner.name, schema: TeamRoutePlannerSchema },
      {
        name: AllianceStickerRoleGrant.name,
        schema: AllianceStickerRoleGrantSchema,
      },
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
    TeamRoutePlannerService,
    StickerAccessService,
    UserAvatarService,
    {
      provide: APP_INTERCEPTOR,
      useClass: AppVersionInterceptor,
    },
  ],
  exports: [
    UsersService,
    GameIdentitiesService,
    AllianceRegistryService,
    TeamsService,
    TeamNewsService,
    TeamNewsAttachmentsService,
    TeamRoutePlannerService,
    MongooseModule,
    ForumModule,
    StickerAccessService,
  ],
})
export class UsersModule {}
