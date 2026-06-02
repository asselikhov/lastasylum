import {
  overlayPersonalAckEvent,
  overlayPersonalDeliveryEvent,
  resolveOverlayPersonalReplyToLogId,
} from './overlay-reaction-send-policy';

describe('overlay-reaction-send-policy', () => {
  it('regular send rejects replyToLogId', () => {
    expect(() =>
      resolveOverlayPersonalReplyToLogId('regular', '6641c45bbbee8f2a06e9f111'),
    ).toThrow(/not allowed on overlay:reaction/);
  });

  it('regular send allows missing replyToLogId', () => {
    expect(resolveOverlayPersonalReplyToLogId('regular', undefined)).toBeNull();
    expect(resolveOverlayPersonalReplyToLogId('regular', '  ')).toBeNull();
  });

  it('reply send requires replyToLogId', () => {
    expect(() => resolveOverlayPersonalReplyToLogId('reply', '')).toThrow(
      /replyToLogId is required/,
    );
  });

  it('reply send returns trimmed replyToLogId', () => {
    expect(
      resolveOverlayPersonalReplyToLogId('reply', ' 6641c45bbbee8f2a06e9f111 '),
    ).toBe('6641c45bbbee8f2a06e9f111');
  });

  it('maps delivery and ack events by mode', () => {
    expect(overlayPersonalDeliveryEvent('regular')).toBe('overlay:reaction');
    expect(overlayPersonalDeliveryEvent('reply')).toBe('overlay:reaction:reply');
    expect(overlayPersonalAckEvent('regular')).toBe('overlay:reaction:sent');
    expect(overlayPersonalAckEvent('reply')).toBe('overlay:reaction:reply:sent');
  });
});
