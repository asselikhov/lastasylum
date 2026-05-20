import { AllianceRole } from './enums/alliance-role.enum';
import {
  isAppAdminRole,
  normalizeAllianceRole,
} from './alliance-role.util';

describe('alliance-role.util', () => {
  it('normalizes legacy R* values to account roles', () => {
    expect(normalizeAllianceRole('R2')).toBe(AllianceRole.MEMBER);
    expect(normalizeAllianceRole('R3')).toBe(AllianceRole.OFFICER);
    expect(normalizeAllianceRole('R4')).toBe(AllianceRole.MODERATOR);
    expect(normalizeAllianceRole('R5')).toBe(AllianceRole.ADMIN);
  });

  it('keeps new role names', () => {
    expect(normalizeAllianceRole('MEMBER')).toBe(AllianceRole.MEMBER);
    expect(normalizeAllianceRole('admin')).toBe(AllianceRole.ADMIN);
  });

  it('isAppAdmin only for ADMIN (and legacy R5)', () => {
    expect(isAppAdminRole(AllianceRole.ADMIN)).toBe(true);
    expect(isAppAdminRole('R5')).toBe(true);
    expect(isAppAdminRole(AllianceRole.MEMBER)).toBe(false);
    expect(isAppAdminRole('R4')).toBe(false);
  });
});
