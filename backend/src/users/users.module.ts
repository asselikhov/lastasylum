import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
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
import { TeamsController } from './teams.controller';
import { TeamsService } from './teams.service';
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

@Module({
  imports: [
    StorageModule,
    MongooseModule.forFeature([
      { name: User.name, schema: UserSchema },
      { name: AllianceRegistry.name, schema: AllianceRegistrySchema },
      { name: PlayerTeam.name, schema: PlayerTeamSchema },
      { name: TeamJoinRequest.name, schema: TeamJoinRequestSchema },
      { name: TeamNews.name, schema: TeamNewsSchema },
      { name: TeamNewsAttachment.name, schema: TeamNewsAttachmentSchema },
    ]),
  ],
  controllers: [UsersController, AdminAlliancesController, TeamsController],
  providers: [
    UsersService,
    AllianceRegistryService,
    TeamsService,
    TeamNewsService,
    TeamNewsAttachmentsService,
  ],
  exports: [UsersService, MongooseModule],
})
export class UsersModule {}
