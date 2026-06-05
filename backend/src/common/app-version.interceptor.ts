import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Observable } from 'rxjs';
import { UsersService } from '../users/users.service';

type AuthedRequest = {
  user?: { userId?: string };
  headers?: Record<string, string | string[] | undefined>;
};

@Injectable()
export class AppVersionInterceptor implements NestInterceptor {
  constructor(private readonly usersService: UsersService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const req = context.switchToHttp().getRequest<AuthedRequest>();
    const userId = req.user?.userId?.trim();
    if (userId) {
      const raw = req.headers?.['user-agent'];
      const userAgent =
        typeof raw === 'string' ? raw : Array.isArray(raw) ? raw[0] : undefined;
      if (userAgent) {
        void this.usersService.recordAppVersionFromUserAgent(userId, userAgent);
      }
    }
    return next.handle();
  }
}
