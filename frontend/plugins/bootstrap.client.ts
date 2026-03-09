import { useAuthStore } from '~/stores/auth';
import { useUiStore } from '~/stores/ui';

export default defineNuxtPlugin(async () => {
  const ui = useUiStore();
  ui.initialize();

  const auth = useAuthStore();
  await auth.hydrateFromCookie();
});
