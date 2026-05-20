import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { normalizeAllianceRole } from '../alliance-role.util';
import { ROLES_KEY } from '../decorators/roles.decorator';
import { AllianceRole } from '../enums/alliance-role.enum';

type UserWithRole = {
  role: AllianceRole | string;
};

const rolePriority: Record<AllianceRole, number> = {
  [AllianceRole.MEMBER]: 1,
  [AllianceRole.OFFICER]: 2,
  [AllianceRole.MODERATOR]: 3,
  [AllianceRole.ADMIN]: 4,
};

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredRoles = this.reflector.getAllAndOverride<AllianceRole[]>(
      ROLES_KEY,
      [context.getHandler(), context.getClass()],
    );

    if (!requiredRoles || requiredRoles.length === 0) {
      return true;
    }

    const request = context
      .switchToHttp()
      .getRequest<{ user?: UserWithRole }>();
    const user = request.user;

    if (!user?.role) {
      throw new UnauthorizedException('User role is missing in access token');
    }

    const userScore = rolePriority[normalizeAllianceRole(user.role)];
    const minimumRequiredScore = Math.min(
      ...requiredRoles.map((role) => rolePriority[role]),
    );

    return userScore >= minimumRequiredScore;
  }
}
