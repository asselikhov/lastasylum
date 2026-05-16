import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  ValidateIf,
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
  /** Без опроса обязателен (1–200); с опросом поле можно не передавать. */
  @ValidateIf((o) => !o.poll)
  @IsString()
  @Length(1, 200)
  title?: string;

  @ValidateIf((o) => !o.poll)
  @IsString()
  @Length(1, 20000)
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
