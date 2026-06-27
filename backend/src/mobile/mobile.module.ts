import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { MobileController } from './mobile.controller';
import { GamePatchService } from './game-patch.service';

@Module({
  imports: [AuthModule],
  controllers: [MobileController],
  providers: [GamePatchService],
})
export class MobileModule {}
