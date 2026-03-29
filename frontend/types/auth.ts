export type ApiResult<T> = {
  success: boolean;
  data?: T;
  message?: string;
  code?: string;
};

export type AuthUser = {
  id: string;
  displayName: string;
  principal: string;
  email?: string;
  phone?: string;
  avatarUrl?: string;
  role?: string;
  [key: string]: unknown;
};

export type LoginPayload = {
  principal: string;
  password: string;
  [key: string]: unknown;
};

export type RegisterPayload = {
  displayName: string;
  principal: string;
  password: string;
  confirmPassword: string;
  [key: string]: unknown;
};

export type UpdateProfilePayload = {
  displayName: string;
  email: string;
  phone: string;
  avatarUrl?: string;
};

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};
