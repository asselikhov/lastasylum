import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { PutTeamRoutePlannerDto } from './dto/put-team-route-planner.dto';
import {
  TeamRoutePlanner,
  TeamRoutePlannerDocument,
} from './schemas/team-route-planner.schema';
import { TeamsService } from './teams.service';

export type TeamRoutePlannerSnapshot = {
  routes: unknown[];
  updatedAtMs: number;
  updatedByUserId: string;
};

const MAX_ROUTES = 200;
const MAX_POINTS_PER_ROUTE = 500;
const MAX_NAME_LEN = 64;

@Injectable()
export class TeamRoutePlannerService {
  constructor(
    @InjectModel(TeamRoutePlanner.name)
    private readonly model: Model<TeamRoutePlannerDocument>,
    private readonly teams: TeamsService,
  ) {}

  async getSnapshot(
    teamId: string,
    userId: string,
  ): Promise<TeamRoutePlannerSnapshot> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const doc = await this.findDoc(teamId);
    if (!doc) {
      return { routes: [], updatedAtMs: 0, updatedByUserId: '' };
    }
    return this.toSnapshot(doc);
  }

  async replaceRoutes(
    teamId: string,
    userId: string,
    dto: PutTeamRoutePlannerDto,
  ): Promise<TeamRoutePlannerSnapshot> {
    await this.teams.assertSquadOfficerOrThrow(teamId, userId);
    const routes = this.validateRoutes(dto.routes);
    const now = Date.now();
    const teamOid = new Types.ObjectId(teamId);
    const doc = await this.model
      .findOneAndUpdate(
        { teamId: teamOid },
        {
          $set: {
            routesJson: JSON.stringify(routes),
            updatedAtMs: now,
            updatedByUserId: userId.trim(),
          },
        },
        { upsert: true, new: true, setDefaultsOnInsert: true },
      )
      .exec();
    if (!doc) {
      throw new NotFoundException('Team route planner not found');
    }
    return this.toSnapshot(doc);
  }

  private async findDoc(
    teamId: string,
  ): Promise<TeamRoutePlannerDocument | null> {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    return this.model.findOne({ teamId: new Types.ObjectId(teamId) }).exec();
  }

  private toSnapshot(doc: TeamRoutePlannerDocument): TeamRoutePlannerSnapshot {
    let routes: unknown[] = [];
    try {
      const parsed = JSON.parse(doc.routesJson || '[]');
      routes = Array.isArray(parsed) ? parsed : [];
    } catch {
      routes = [];
    }
    return {
      routes,
      updatedAtMs: doc.updatedAtMs ?? 0,
      updatedByUserId: doc.updatedByUserId ?? '',
    };
  }

  private validateRoutes(raw: unknown): unknown[] {
    if (!Array.isArray(raw)) {
      throw new BadRequestException('routes must be an array');
    }
    if (raw.length > MAX_ROUTES) {
      throw new BadRequestException(`max ${MAX_ROUTES} routes`);
    }
    for (const route of raw) {
      this.validateRoute(route);
    }
    return raw;
  }

  private validateRoute(route: unknown): void {
    if (!route || typeof route !== 'object') {
      throw new BadRequestException('invalid route');
    }
    const r = route as Record<string, unknown>;
    const id = String(r.id ?? '').trim();
    const name = String(r.name ?? '').trim();
    const type = String(r.type ?? '').trim().toLowerCase();
    if (!id || !name || name.length > MAX_NAME_LEN) {
      throw new BadRequestException('invalid route name');
    }
    if (type !== 'pvp' && type !== 'pve') {
      throw new BadRequestException('invalid route type');
    }
    const points = r.points;
    if (points != null) {
      if (!Array.isArray(points)) {
        throw new BadRequestException('invalid route points');
      }
      if (points.length > MAX_POINTS_PER_ROUTE) {
        throw new BadRequestException(`max ${MAX_POINTS_PER_ROUTE} points`);
      }
    }
  }
}
