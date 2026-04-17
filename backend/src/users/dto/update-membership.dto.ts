import { IsEnum } from 'class-validator';
import { TeamMembershipStatus } from '../../common/enums/team-membership-status.enum';

export class UpdateMembershipDto {
  @IsEnum(TeamMembershipStatus)
  status: TeamMembershipStatus;
}
