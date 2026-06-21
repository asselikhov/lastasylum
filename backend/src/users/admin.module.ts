import { Module, forwardRef } from '@nestjs/common';
import { ChatModule } from '../chat/chat.module';
import { AdminAlliancesController } from './admin-alliances.controller';
import { AdminGameIdentitiesController } from './admin-game-identities.controller';
import { AdminUserFeaturesController } from './admin-user-features.controller';
import { AdminStickerAccessController } from './admin-sticker-access.controller';
import { AdminTeamsController } from './admin-teams.controller';
import { UsersModule } from './users.module';

@Module({
  imports: [forwardRef(() => UsersModule), forwardRef(() => ChatModule)],
  controllers: [
    AdminAlliancesController,
    AdminStickerAccessController,
    AdminTeamsController,
    AdminGameIdentitiesController,
    AdminUserFeaturesController,
  ],
})
export class AdminModule {}
