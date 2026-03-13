import type { ApiResult } from '~/types/auth';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

type RequestApiOptions = {
  method?: HttpMethod;
  token?: string | null;
  body?: unknown;
};

const TOKEN_COOKIE_KEY = 'sm_auth_token';

const isApiResultPayload = <T>(value: unknown): value is ApiResult<T> => {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const maybeResult = value as Partial<ApiResult<T>>;
  return typeof maybeResult.success === 'boolean'
    && ('message' in maybeResult)
    && ('code' in maybeResult);
};

const handleUnauthorized = () => {
  if (!import.meta.client) {
    return;
  }

  const tokenCookie = useCookie<string | null>(TOKEN_COOKIE_KEY, { sameSite: 'lax' });
  tokenCookie.value = null;

  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

const resolveApiBase = () => {
  const config = useRuntimeConfig();
  return import.meta.server ? config.apiBaseInternal : config.public.apiBase;
};

export const useHttpApiMode = () => {
  return useRuntimeConfig().public.apiMode === 'http';
};

export const requestApi = async <T>(path: string, options: RequestApiOptions = {}): Promise<ApiResult<T>> => {
  try {
    return await $fetch<ApiResult<T>>(path, {
      baseURL: resolveApiBase(),
      method: options.method ?? 'GET',
      body: options.body as BodyInit | Record<string, any> | null | undefined,
      headers: options.token
        ? {
            Authorization: `Bearer ${options.token}`
          }
        : undefined
    });
  } catch (error) {
    const response = error as {
      data?: ApiResult<T>;
      message?: string;
      statusMessage?: string;
      statusCode?: number;
    };

    if (isApiResultPayload<T>(response.data)) {
      return response.data;
    }

    if (response.statusCode === 401) {
      handleUnauthorized();
      return {
        success: false,
        message: 'Session expired. Please log in again.',
        code: 'UNAUTHORIZED'
      };
    }

    return {
      success: false,
      message: response.message ?? response.statusMessage ?? 'Request failed.',
      code: 'REQUEST_ERROR'
    };
  }
};
