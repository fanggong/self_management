import type {
  ApiResult,
  AuthLoginResult,
  AuthUser,
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  UpdateProfilePayload
} from '~/types/auth';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockAuthApi } from '~/services/mock/auth';

export const authApi = {
  login(payload: LoginPayload): Promise<ApiResult<AuthLoginResult>> {
    if (useHttpApiMode()) {
      return requestApi<AuthLoginResult>('/auth/login', {
        method: 'POST',
        body: payload
      });
    }

    return mockAuthApi.login(payload);
  },

  register(payload: RegisterPayload): Promise<ApiResult<AuthUser>> {
    if (useHttpApiMode()) {
      return requestApi<AuthUser>('/auth/register', {
        method: 'POST',
        body: payload
      });
    }

    return mockAuthApi.register(payload);
  },

  logout(token?: string | null): Promise<ApiResult<null>> {
    if (useHttpApiMode()) {
      return requestApi<null>('/auth/logout', {
        method: 'POST',
        token
      });
    }

    return mockAuthApi.logout();
  },

  me(token: string): Promise<ApiResult<AuthUser>> {
    if (useHttpApiMode()) {
      return requestApi<AuthUser>('/auth/me', {
        token
      });
    }

    return mockAuthApi.me(token);
  },

  updateProfile(token: string, payload: UpdateProfilePayload): Promise<ApiResult<AuthUser>> {
    if (useHttpApiMode()) {
      return requestApi<AuthUser>('/users/me/profile', {
        method: 'PUT',
        token,
        body: payload
      });
    }

    return mockAuthApi.updateProfile(token, payload);
  },

  changePassword(token: string, payload: ChangePasswordPayload): Promise<ApiResult<null>> {
    if (useHttpApiMode()) {
      return requestApi<null>('/users/me/password', {
        method: 'POST',
        token,
        body: payload
      });
    }

    return mockAuthApi.changePassword(token, payload);
  }
};
