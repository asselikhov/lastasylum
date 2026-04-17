import { SetMetadata } from '@nestjs/common';
import { AllianceRole } from '../enums/alliance-role.enum';

export const ROLES_KEY = 'roles';
export const Roles = (...roles: AllianceRole[]) =>
  SetMetadata(ROLES_KEY, roles);
