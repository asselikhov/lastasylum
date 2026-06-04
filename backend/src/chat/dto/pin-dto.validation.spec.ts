import { BadRequestException, ValidationPipe } from '@nestjs/common';
import { PinRoomMessageDto } from './pin-room-message.dto';
import { MarkRoomReadDto } from './mark-room-read.dto';
import { PinTeamForumTopicMessageDto } from '../../users/dto/team-forum.dto';

/** Mirrors app ValidationPipe in main.ts — catches forbidNonWhitelisted regressions. */
const pipe = new ValidationPipe({
  whitelist: true,
  forbidNonWhitelisted: true,
  transform: true,
});

const messageId = '507f1f77bcf86cd799439014';

async function transformBody<T>(
  metatype: new () => T,
  body: unknown,
): Promise<T> {
  return pipe.transform(body, { type: 'body', metatype }) as Promise<T>;
}

describe('Pin DTO validation (ValidationPipe)', () => {
  it('PinRoomMessageDto accepts messageId for pin', async () => {
    const dto = await transformBody(PinRoomMessageDto, { messageId });
    expect(dto.messageId).toBe(messageId);
  });

  it('PinRoomMessageDto accepts null messageId for unpin', async () => {
    const dto = await transformBody(PinRoomMessageDto, { messageId: null });
    expect(dto.messageId).toBeNull();
  });

  it('PinRoomMessageDto accepts omitted messageId for unpin', async () => {
    const dto = await transformBody(PinRoomMessageDto, {});
    expect(dto.messageId).toBeUndefined();
  });

  it('PinRoomMessageDto rejects unknown fields', async () => {
    await expect(
      transformBody(PinRoomMessageDto, { messageId, extra: true }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('MarkRoomReadDto accepts messageId', async () => {
    const dto = await transformBody(MarkRoomReadDto, { messageId });
    expect(dto.messageId).toBe(messageId);
  });

  it('PinTeamForumTopicMessageDto accepts messageId for pin', async () => {
    const dto = await transformBody(PinTeamForumTopicMessageDto, {
      messageId,
    });
    expect(dto.messageId).toBe(messageId);
  });

  it('PinTeamForumTopicMessageDto accepts null for unpin', async () => {
    const dto = await transformBody(PinTeamForumTopicMessageDto, {
      messageId: null,
    });
    expect(dto.messageId).toBeNull();
  });
});
