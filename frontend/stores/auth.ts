import { defineStore } from 'pinia';
import { authApi } from '~/services/api/auth';
import type {
  ApiResult,
  AuthLoginResult,
  AuthUser,
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  UpdateProfilePayload
} from '~/types/auth';

const TOKEN_COOKIE_KEY = 'sm_auth_token';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as AuthUser | null,
    token: null as string | null,
    loading: false,
    initialized: false
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.user && state.token)
  },

  actions: {
    async hydrateFromCookie() {
      if (this.initialized) {
        return;
      }

      const tokenCookie = useCookie<string | null>(TOKEN_COOKIE_KEY, { sameSite: 'lax' });
      this.token = tokenCookie.value ?? null;

      if (!this.token) {
        this.user = null;
        this.initialized = true;
        return;
      }

      const result = await authApi.me(this.token);
      if (result.success && result.data) {
        this.user = result.data;
      } else {
        tokenCookie.value = null;
        this.token = null;
        this.user = null;
      }

      this.initialized = true;
    },

    async login(payload: LoginPayload): Promise<ApiResult<AuthLoginResult>> {
      this.loading = true;

      try {
        const result = await authApi.login(payload);

        if (result.success && result.data) {
          const tokenCookie = useCookie<string | null>(TOKEN_COOKIE_KEY, { sameSite: 'lax' });
          tokenCookie.value = result.data.token;
          this.token = result.data.token;
          this.user = result.data.user;
          this.initialized = true;
        }

        return result;
      } finally {
        this.loading = false;
      }
    },

    async register(payload: RegisterPayload) {
      this.loading = true;

      try {
        return await authApi.register(payload);
      } finally {
        this.loading = false;
      }
    },

    async logout() {
      await authApi.logout(this.token);

      const tokenCookie = useCookie<string | null>(TOKEN_COOKIE_KEY, { sameSite: 'lax' });
      tokenCookie.value = null;
      this.token = null;
      this.user = null;
      this.initialized = true;
    },

    async updateProfile(payload: UpdateProfilePayload): Promise<ApiResult<AuthUser>> {
      if (!this.token) {
        return {
          success: false,
          message: 'You are not signed in.',
          code: 'INVALID_TOKEN'
        };
      }

      this.loading = true;

      try {
        const result = await authApi.updateProfile(this.token, payload);

        if (result.success && result.data) {
          this.user = result.data;
        }

        return result;
      } finally {
        this.loading = false;
      }
    },

    async changePassword(payload: ChangePasswordPayload): Promise<ApiResult<null>> {
      if (!this.token) {
        return {
          success: false,
          message: 'You are not signed in.',
          code: 'INVALID_TOKEN'
        };
      }

      this.loading = true;

      try {
        return await authApi.changePassword(this.token, payload);
      } finally {
        this.loading = false;
      }
    }
  }
});
