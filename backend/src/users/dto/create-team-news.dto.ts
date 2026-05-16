import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  ValidateNested,
} from 'class-validator';

export class TeamNewsPollInputDto {
  @IsString()
  @Length(1, 500)
  question: string;

  @IsArray()
  @ArrayMinSize(2)
  @ArrayMaxSize(8)
  @IsString({ each: true })
  @Length(1, 200, { each: true })
  optionTexts: string[];
}

export class CreateTeamNewsDto {
  /** Необязателен, если есть опрос — подставится вопрос опроса. */
  @IsOptional()
  @IsString()
  @MaxLength(200)
  title?: string;

  /** Необязателен для поста «только опрос». */
  @IsOptional()
  @IsString()
  @MaxLength(20000)
  body?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  imageFileIds?: string[];

  @IsOptional()
  @ValidateNested()
  @Type(() => TeamNewsPollInputDto)
  poll?: TeamNewsPollInputDto;
}
