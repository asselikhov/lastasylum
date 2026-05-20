import { Module, forwardRef } from '@nestjs/common';
import { AdminAlliancesController } from './admin-alliances.controller';
import { AdminGameIdentitiesController } from './admin-game-identities.controller';
import { AdminStickerAccessController } from './admin-sticker-access.controller';
import { AdminTeamsController } from './admin-teams.controller';
import { UsersModule } from './users.module';

@Module({
  imports: [forwardRef(() => UsersModule)],
  controllers: [
    AdminAlliancesController,
    AdminStickerAccessController,
    AdminTeamsController,
    AdminGameIdentitiesController,
  ],
})
export class AdminModule {}
