import type { ApiResult } from '~/types/auth';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

type RequestApiOptions = {
  method?: HttpMethod;
  token?: string | null;
  body?: unknown;
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
    };

    if (response.data) {
      return response.data;
    }

    return {
      success: false,
      message: response.message ?? response.statusMessage ?? 'Request failed.',
      code: 'REQUEST_ERROR'
    };
  }
};
