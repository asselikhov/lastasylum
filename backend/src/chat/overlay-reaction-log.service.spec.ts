import { OverlayReactionLogService } from './overlay-reaction-log.service';

describe('OverlayReactionLogService visibility', () => {
  const service = Object.create(
    OverlayReactionLogService.prototype,
  ) as OverlayReactionLogService;

  const visibilityFilter = (
    service as unknown as { visibilityFilter: (userId: string) => Record<string, unknown> }
  ).visibilityFilter.bind(service);

  it('includes broadcast for any viewer', () => {
    const filter = visibilityFilter('user-a');
    expect(filter).toEqual({
      $or: [
        { visibility: 'broadcast' },
        {
          visibility: 'personal',
          $or: [{ senderUserId: 'user-a' }, { targetUserId: 'user-a' }],
        },
      ],
    });
  });
});
