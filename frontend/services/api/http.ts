import type { ApiResult } from '~/types/auth';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

type RequestApiOptions = {
  method?: HttpMethod;
  body?: unknown;
  headers?: Record<string, string>;
};

export const isApiResultPayload = <T>(value: unknown): value is ApiResult<T> => {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const maybeResult = value as Partial<ApiResult<T>>;
  return typeof maybeResult.success === 'boolean'
    && ('message' in maybeResult)
    && ('code' in maybeResult);
};

export const handleUnauthorized = () => {
  if (!import.meta.client) {
    return;
  }

  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

export const resolveApiBase = () => {
  const config = useRuntimeConfig();
  return import.meta.server ? config.apiBaseInternal : config.public.apiBase;
};

const buildRequestHeaders = (headers?: Record<string, string>) => {
  const mergedHeaders: Record<string, string> = { ...(headers ?? {}) };

  if (import.meta.server) {
    const requestHeaders = useRequestHeaders(['cookie']);
    if (requestHeaders.cookie) {
      mergedHeaders.cookie = requestHeaders.cookie;
    }
  }

  return Object.keys(mergedHeaders).length > 0 ? mergedHeaders : undefined;
};

export const buildApiFetchOptions = (headers?: Record<string, string>) => ({
  credentials: 'include' as const,
  headers: buildRequestHeaders(headers)
});

export const useHttpApiMode = () => {
  return useRuntimeConfig().public.apiMode === 'http';
};

export const requestApi = async <T>(path: string, options: RequestApiOptions = {}): Promise<ApiResult<T>> => {
  try {
    return await $fetch<ApiResult<T>>(path, {
      baseURL: resolveApiBase(),
      method: options.method ?? 'GET',
      body: options.body as BodyInit | Record<string, any> | null | undefined,
      ...buildApiFetchOptions(options.headers)
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
