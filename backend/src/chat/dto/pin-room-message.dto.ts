import { IsMongoId, IsOptional, ValidateIf } from 'class-validator';

/** PUT /chat/rooms/:roomId/pin — pin (messageId) or unpin (null / omitted). */
export class PinRoomMessageDto {
  @IsOptional()
  @ValidateIf((_, value) => value != null)
  @IsMongoId()
  messageId?: string | null;
}
