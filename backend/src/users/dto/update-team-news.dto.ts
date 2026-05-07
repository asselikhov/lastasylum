import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  ValidateNested,
} from 'class-validator';
import { TeamNewsPollInputDto } from './create-team-news.dto';

export class UpdateTeamNewsDto {
  @IsOptional()
  @IsString()
  @Length(1, 200)
  title?: string;

  @IsOptional()
  @IsString()
  @MaxLength(20000)
  body?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  imageFileIds?: string[];

  /** Передать null через отдельный флаг сложно в JSON — пустой poll удаляет опрос. */
  @IsOptional()
  @ValidateNested()
  @Type(() => TeamNewsPollInputDto)
  poll?: TeamNewsPollInputDto;

  /** Удалить опрос и голоса. */
  @IsOptional()
  @IsBoolean()
  clearPoll?: boolean;
}
