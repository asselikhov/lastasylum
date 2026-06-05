import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class ForwardMessageDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  roomId!: string;
}
