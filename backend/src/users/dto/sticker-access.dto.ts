import { IsArray, IsObject, IsOptional, IsString } from 'class-validator';
import { AllianceRole } from '../../common/enums/alliance-role.enum';

/** Replace all sticker ACL rows for one alliance (roles ∪ users per pack). */
export class PutAllianceStickerAccessDto {
  @IsObject()
  @IsOptional()
  roleGrants?: Record<string, AllianceRole[]>;

  @IsObject()
  @IsOptional()
  /** packKey -> user ids */
  userGrants?: Record<string, string[]>;
}

/** Replace sticker user grants for one member (does not touch role grants or other users). */
export class PatchUserStickerAccessDto {
  @IsArray()
  @IsString({ each: true })
  packKeys: string[];
}
