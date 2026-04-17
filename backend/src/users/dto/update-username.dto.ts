import { IsString, MinLength } from 'class-validator';

export class UpdateUsernameDto {
  @IsString()
  @MinLength(3)
  username: string;
}
