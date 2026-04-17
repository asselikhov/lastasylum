import { IsEnum, IsString } from 'class-validator';
import { AllianceRole } from '../../common/enums/alliance-role.enum';

export class UpdateRoleDto {
  @IsString()
  userId: string;

  @IsEnum(AllianceRole)
  role: AllianceRole;
}
