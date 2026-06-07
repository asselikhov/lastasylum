import { parseFirebaseServiceAccountJson } from './firebase-service-account.util';

describe('parseFirebaseServiceAccountJson', () => {
  const sample = {
    type: 'service_account',
    project_id: 'squadoverlay',
    private_key_id: 'abc',
    private_key: '-----BEGIN PRIVATE KEY-----\\nline\\n-----END PRIVATE KEY-----\\n',
    client_email: 'firebase-adminsdk@test.iam.gserviceaccount.com',
  };

  it('parses compact JSON', () => {
    const cred = parseFirebaseServiceAccountJson(JSON.stringify(sample));
    expect(cred.project_id).toBe('squadoverlay');
    expect(cred.private_key).toContain('\nline\n');
  });

  it('parses JSON wrapped in single quotes', () => {
    const cred = parseFirebaseServiceAccountJson(
      `'${JSON.stringify(sample)}'`,
    );
    expect(cred.client_email).toBe(sample.client_email);
  });
});
