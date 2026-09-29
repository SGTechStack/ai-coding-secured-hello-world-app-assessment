export type Role = 'USER' | 'ADMIN';

export interface User {
  id: string;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}
