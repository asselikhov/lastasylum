import { IsInt, IsString, Max, Min } from 'class-validator';

export class MuteUserDto {
  @IsString()
  userId: string;

  @IsInt()
  @Min(1)
  @Max(1440)
  minutes: number;
}
