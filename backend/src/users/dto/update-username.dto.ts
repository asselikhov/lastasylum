import { IsEmail } from 'class-validator';

export class UpdateUsernameDto {
  /** Account login (email); stored as both email and username. */
  @IsEmail()
  username: string;
}
