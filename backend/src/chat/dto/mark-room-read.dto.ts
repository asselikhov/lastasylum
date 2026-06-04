import { IsMongoId } from 'class-validator';

/** POST /chat/rooms/:roomId/read */
export class MarkRoomReadDto {
  @IsMongoId()
  messageId: string;
}
