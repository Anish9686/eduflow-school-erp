import { apiPost } from './client';

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
};

export const authApi = {
  changePassword: (payload: ChangePasswordPayload) =>
    apiPost<void>('/api/v1/auth/change-password', payload),
};
