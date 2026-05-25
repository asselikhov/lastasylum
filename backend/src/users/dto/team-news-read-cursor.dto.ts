import { IsISO8601, IsOptional } from 'class-validator';

export class TeamNewsReadCursorDto {
  @IsOptional()
  @IsISO8601()
  lastSeenCreatedAt?: string | null;
}

export class AdvanceTeamNewsReadCursorDto {
  @IsISO8601()
  createdAt: string;
}
