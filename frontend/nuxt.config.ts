import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

const AvalonPreset = definePreset(Aura, {
  semantic: {
    select: {
      borderRadius: '{border.radius.lg}',
      height: '2rem'
    },
    formField: {
      borderRadius: '{border.radius.md}'
    },
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.500}',
      600: '{blue.600}',
      700: '{blue.700}',
      800: '{blue.800}',
      900: '{blue.900}',
      950: '{blue.950}'
    }
  }
});

const env = (globalThis as typeof globalThis & {
  process?: {
    env?: Record<string, string | undefined>;
  };
}).process?.env ?? {};

export default defineNuxtConfig({
  compatibilityDate: '2026-03-05',
  devtools: { enabled: true },
  css: ['primeicons/primeicons.css'],
  modules: ['@pinia/nuxt', '@primevue/nuxt-module', '@nuxtjs/tailwindcss'],
  tailwindcss: {
    cssPath: '~/assets/css/main.css',
    configPath: 'tailwind.config.ts',
    viewer: false
  },
  primevue: {
    options: {
      ripple: true,
      inputVariant: 'filled',
      theme: {
        preset: AvalonPreset,
        options: {
          darkModeSelector: '.dark'
        }
      }
    }
  },
  typescript: {
    strict: true,
    typeCheck: false
  },
  runtimeConfig: {
    apiBaseInternal: env.NUXT_API_BASE_INTERNAL ?? 'http://admin-api:8080/api/v1',
    public: {
      appName: 'OTW',
      apiMode: env.NUXT_PUBLIC_API_MODE ?? 'http',
      apiBase: env.NUXT_PUBLIC_API_BASE ?? 'https://api.example.com/api/v1'
    }
  },
  routeRules: {
    '/': { redirect: '/login' }
  }
});
