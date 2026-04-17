import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ROLES_KEY } from '../decorators/roles.decorator';
import { AllianceRole } from '../enums/alliance-role.enum';

type UserWithRole = {
  role: AllianceRole;
};

const rolePriority: Record<AllianceRole, number> = {
  [AllianceRole.R2]: 1,
  [AllianceRole.R3]: 2,
  [AllianceRole.R4]: 3,
  [AllianceRole.R5]: 4,
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

    const userScore = rolePriority[user.role];
    const minimumRequiredScore = Math.min(
      ...requiredRoles.map((role) => rolePriority[role]),
    );

    return userScore >= minimumRequiredScore;
  }
}
