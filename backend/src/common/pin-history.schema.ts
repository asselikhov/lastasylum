import { Schema as MongooseSchema } from 'mongoose';

export const PinHistoryEntrySchema = new MongooseSchema(
  {
    messageId: {
      type: MongooseSchema.Types.ObjectId,
      required: true,
    },
    pinnedAt: { type: Date, required: true },
    pinnedByUserId: { type: String, required: true },
  },
  { _id: false },
);
