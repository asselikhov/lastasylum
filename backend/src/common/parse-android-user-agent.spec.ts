import { parseAndroidUserAgent } from './parse-android-user-agent';

describe('parseAndroidUserAgent', () => {
  it('parses SquadRelay Android user agent', () => {
    expect(
      parseAndroidUserAgent('SquadRelay-Android/1.4.2 (42)'),
    ).toEqual({
      versionName: '1.4.2',
      versionCode: 42,
    });
  });

  it('is case-insensitive on prefix', () => {
    expect(
      parseAndroidUserAgent('squadrelay-android/2.0.0 (100)'),
    ).toEqual({
      versionName: '2.0.0',
      versionCode: 100,
    });
  });

  it('returns null for missing or invalid user agent', () => {
    expect(parseAndroidUserAgent(undefined)).toBeNull();
    expect(parseAndroidUserAgent('')).toBeNull();
    expect(parseAndroidUserAgent('Mozilla/5.0')).toBeNull();
    expect(parseAndroidUserAgent('SquadRelay-Android/1.0')).toBeNull();
  });
});
