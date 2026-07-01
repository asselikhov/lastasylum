import { IsArray, IsInt, IsOptional, Min } from 'class-validator';

export class PutTeamRoutePlannerDto {
  @IsArray()
  routes!: unknown[];

  /** Client-side revision; server stores its own updatedAtMs. */
  @IsOptional()
  @IsInt()
  @Min(0)
  clientUpdatedAtMs?: number;
}
