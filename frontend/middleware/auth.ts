import { useAuthStore } from '~/stores/auth';

export default defineNuxtRouteMiddleware(async () => {
  const auth = useAuthStore();

  if (!auth.initialized) {
    await auth.hydrateFromCookie();
  }

  if (!auth.isAuthenticated) {
    return navigateTo('/login');
  }
});
