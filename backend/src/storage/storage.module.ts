import { Module } from '@nestjs/common';
import { R2Service } from '../chat/r2.service';

/** Общий доступ к R2 без циклического импорта Users ↔ Chat. */
@Module({
  providers: [R2Service],
  exports: [R2Service],
})
export class StorageModule {}
