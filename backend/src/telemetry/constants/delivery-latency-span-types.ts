/** Wire values from Android LatencySpanType — keep in sync with mobile client. */
export const DELIVERY_LATENCY_SPAN_TYPES = [
  'chat_send_to_socket',
  'chat_send_to_http_ack',
  'chat_read_receipt',
  'forum_send_to_socket',
  'overlay_strip_ingest',
] as const;

export type DeliveryLatencySpanType =
  (typeof DELIVERY_LATENCY_SPAN_TYPES)[number];

export const DELIVERY_LATENCY_SPAN_TYPE_SET = new Set<string>(
  DELIVERY_LATENCY_SPAN_TYPES,
);
