import { IsNotEmpty, IsString } from 'class-validator';

/** Multipart body fields (file is handled by multer, not part of this DTO). */
export class UploadChatAttachmentDto {
  @IsString()
  @IsNotEmpty()
  roomId!: string;
}
