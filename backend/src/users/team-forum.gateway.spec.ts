import { TeamForumGateway } from './team-forum.gateway';

describe('TeamForumGateway.broadcastTopicRead', () => {
  it('emits topic:read to topic room', () => {
    const emit = jest.fn();
    const gateway = new TeamForumGateway({} as any, {} as any, {} as any, {} as any);
    gateway.server = {
      to: jest.fn().mockReturnValue({ emit }),
    } as any;

    gateway.broadcastTopicRead(
      'team1',
      'topic1',
      'user1',
      'msg1',
    );

    expect(gateway.server.to).toHaveBeenCalledWith('forum:team1:topic1');
    expect(emit).toHaveBeenCalledWith('topic:read', {
      teamId: 'team1',
      topicId: 'topic1',
      userId: 'user1',
      messageId: 'msg1',
    });
  });
});
