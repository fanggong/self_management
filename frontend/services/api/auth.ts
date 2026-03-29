import type {
  ApiResult,
  AuthUser,
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  UpdateProfilePayload
} from '~/types/auth';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockAuthApi } from '~/services/mock/auth';

export const authApi = {
  login(payload: LoginPayload): Promise<ApiResult<AuthUser>> {
    if (useHttpApiMode()) {
      return requestApi<AuthUser>('/auth/login', {
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

  logout(): Promise<ApiResult<null>> {
    if (useHttpApiMode()) {
      return requestApi<null>('/auth/logout', {
        method: 'POST'
      });
    }

    return mockAuthApi.logout();
  },

  me(): Promise<ApiResult<AuthUser>> {
    if (useHttpApiMode()) {
      return requestApi<AuthUser>('/auth/me');
    }

    return mockAuthApi.me();
  },

  updateProfile(payload: UpdateProfilePayload): Promise<ApiResult<AuthUser>> {
    if (useHttpApiMode()) {
      return requestApi<AuthUser>('/users/me/profile', {
        method: 'PUT',
        body: payload
      });
    }

    return mockAuthApi.updateProfile(payload);
  },

  changePassword(payload: ChangePasswordPayload): Promise<ApiResult<null>> {
    if (useHttpApiMode()) {
      return requestApi<null>('/users/me/password', {
        method: 'POST',
        body: payload
      });
    }

    return mockAuthApi.changePassword(payload);
  }
};
