import { defineStore } from 'pinia';
import { authApi } from '~/services/api/auth';
import type {
  ApiResult,
  AuthUser,
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  UpdateProfilePayload
} from '~/types/auth';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as AuthUser | null,
    loading: false,
    initialized: false
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.user)
  },

  actions: {
    async hydrateSession() {
      if (this.initialized) {
        return;
      }

      const result = await authApi.me();
      if (result.success && result.data) {
        this.user = result.data;
      } else {
        this.user = null;
      }

      this.initialized = true;
    },

    async login(payload: LoginPayload): Promise<ApiResult<AuthUser>> {
      this.loading = true;

      try {
        const result = await authApi.login(payload);

        if (result.success && result.data) {
          this.user = result.data;
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
      await authApi.logout();
      this.user = null;
      this.initialized = true;
    },

    async updateProfile(payload: UpdateProfilePayload): Promise<ApiResult<AuthUser>> {
      if (!this.user) {
        return {
          success: false,
          message: 'You are not signed in.',
          code: 'INVALID_TOKEN'
        };
      }

      this.loading = true;

      try {
        const result = await authApi.updateProfile(payload);

        if (result.success && result.data) {
          this.user = result.data;
        }

        return result;
      } finally {
        this.loading = false;
      }
    },

    async changePassword(payload: ChangePasswordPayload): Promise<ApiResult<null>> {
      if (!this.user) {
        return {
          success: false,
          message: 'You are not signed in.',
          code: 'INVALID_TOKEN'
        };
      }

      this.loading = true;

      try {
        return await authApi.changePassword(payload);
      } finally {
        this.loading = false;
      }
    }
  }
});
