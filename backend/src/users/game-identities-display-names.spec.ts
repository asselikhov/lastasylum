import { GameIdentitiesService } from './game-identities.service';

describe('GameIdentitiesService public display names', () => {
  const service = new GameIdentitiesService({} as never, {} as never);

  it('looksLikeAccountEmail detects email-like login', () => {
    expect(service.looksLikeAccountEmail('user@example.com')).toBe(true);
    expect(service.looksLikeAccountEmail('  User@Example.COM  ')).toBe(true);
  });

  it('looksLikeAccountEmail ignores nicknames', () => {
    expect(service.looksLikeAccountEmail('Pilot')).toBe(false);
    expect(service.looksLikeAccountEmail('not@email')).toBe(false);
  });

  it('coalesceDisplayName keeps non-email stored value', () => {
    expect(service.coalesceDisplayName('StoredNick', 'ResolvedNick')).toBe(
      'StoredNick',
    );
  });

  it('coalesceDisplayName replaces email stored with resolved nickname', () => {
    expect(
      service.coalesceDisplayName('user@example.com', 'GameNick'),
    ).toBe('GameNick');
  });

  it('coalesceDisplayName falls back to Союзник when both are email-like', () => {
    expect(service.coalesceDisplayName('a@b.c', 'x@y.z')).toBe('Союзник');
  });
});
