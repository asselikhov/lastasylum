import { Transform } from 'class-transformer';
import { IsNotEmpty, IsString } from 'class-validator';

/** Multipart body fields (file is handled by multer, not part of this DTO). */
export class UploadChatAttachmentDto {
  /** Some clients send duplicate form fields as an array — take the first string. */
  @Transform(({ value }) => (Array.isArray(value) ? value[0] : value))
  @IsString()
  @IsNotEmpty()
  roomId!: string;
}
