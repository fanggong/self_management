<script setup lang="ts">
import AppNavMenu from '~/components/AppNavMenu.vue';
import { useAuthStore } from '~/stores/auth';
import { useUiStore } from '~/stores/ui';

const auth = useAuthStore();
const ui = useUiStore();
const route = useRoute();
const currentTab = computed(() => {
  const tab = String(route.query.tab ?? 'health');
  return tab === 'connector' ? 'connector-settings' : tab;
});

const currentUserName = computed(() => auth.user?.displayName ?? 'Guest');
const currentUserAvatarUrl = computed(() => auth.user?.avatarUrl?.trim() ?? '');
const pageTitleMap: Record<string, string> = {
  health: 'Health',
  finance: 'Finance',
  'connector-settings': 'Connector',
  'connector-tasks': 'Connector',
  staging: 'Staging',
  intermediate: 'Intermediate',
  marts: 'Marts',
  logs: 'Logs',
  settings: 'Settings'
};
const breadcrumbCategoryMap: Record<string, string> = {
  health: 'Dashboards',
  finance: 'Dashboards',
  'connector-settings': 'Apps',
  'connector-tasks': 'Apps',
  staging: 'Data',
  intermediate: 'Data',
  marts: 'Data',
  logs: 'Data',
  settings: 'System'
};
const breadcrumbChildMap: Record<string, string> = {
  'connector-settings': 'Settings',
  'connector-tasks': 'Tasks'
};
const currentPageTitle = computed(() => pageTitleMap[currentTab.value] ?? 'Dashboard');
const currentCategory = computed(() => breadcrumbCategoryMap[currentTab.value] ?? 'Module');
const currentBreadcrumbChild = computed(() => breadcrumbChildMap[currentTab.value] ?? '');
const userInitial = computed(() => (currentUserName.value[0] ?? 'U').toUpperCase());
const searchKeyword = ref('');
const wrapperClasses = computed(() => [
  'layout-wrapper',
  'h-dvh',
  'relative',
  'p-2',
  'flex',
  'overflow-hidden',
  {
    'layout-static': true,
    'layout-static-inactive': ui.navMode === 'hidden',
    'layout-mobile-active': ui.mobileDrawerOpen
  }
]);
const logoTo = computed(() => ({ path: '/app', query: { tab: 'health' } }));

const handleLogout = async () => {
  await auth.logout();
  await navigateTo('/login');
};

const handleSettings = async () => {
  await navigateTo({ path: '/app', query: { tab: 'settings' } }, { replace: false });
};

const userMenuRef = ref<{ toggle: (event: Event) => void } | null>(null);
const userMenuItems = [
  {
    label: 'Settings',
    icon: 'pi pi-cog',
    command: handleSettings
  },
  {
    label: 'Log Out',
    icon: 'pi pi-sign-out',
    command: handleLogout
  }
];

const toggleUserMenu = (event: Event) => {
  userMenuRef.value?.toggle(event);
};

onMounted(() => {
  ui.initialize();
});
</script>

<template>
  <div :class="wrapperClasses">
    <AppToastStack />

    <div class="layout-bg-accent pointer-events-none absolute inset-0">
      <div class="layout-bg-orb layout-bg-orb-left" />
      <div class="layout-bg-orb layout-bg-orb-right" />
    </div>

    <aside
      class="layout-sidebar relative z-20"
      :class="ui.theme === 'dark' ? 'layout-sidebar-dark' : 'layout-sidebar-light'"
    >
      <div class="sidebar-header">
        <NuxtLink :to="logoTo" class="logo">
          <span class="logo-image">
            <i class="pi pi-circle-fill" />
          </span>
          <span class="app-name">{{ currentUserName }}</span>
        </NuxtLink>
      </div>

      <div class="layout-menu-container">
        <AppNavMenu @navigate="ui.closeMobileDrawer" />
      </div>
    </aside>

    <main
      class="layout-content-wrapper relative z-10 flex h-full w-full flex-1 flex-col overflow-hidden rounded-3xl bg-background shadow-stroke"
    >
      <header class="layout-topbar !z-40">
        <div class="topbar-left">
          <button type="button" class="menu-button" aria-label="Toggle navigation" @click="ui.toggleNavigation">
            <i class="pi pi-bars" />
          </button>
          <span class="topbar-separator" />
          <div class="page-title">{{ currentPageTitle }}</div>
        </div>

        <div class="topbar-right">
          <IconField class="hidden md:block">
            <InputIcon class="pi pi-search" />
            <InputText v-model="searchKeyword" class="w-60" placeholder="Search" />
          </IconField>

          <div class="topbar-actions">
            <button type="button" class="menu-button" aria-label="Notifications">
              <i class="pi pi-bell" />
            </button>

            <button type="button" class="topbar-avatar-trigger" aria-label="User menu" @click="toggleUserMenu">
              <Avatar
                v-if="currentUserAvatarUrl"
                :image="currentUserAvatarUrl"
                size="small"
                class="topbar-user-avatar"
              />
              <Avatar v-else :label="userInitial" size="small" class="topbar-user-avatar" />
            </button>
            <Menu ref="userMenuRef" :model="userMenuItems" popup class="topbar-user-menu" />
          </div>
        </div>
      </header>

      <div class="layout-breadcrumb">
        <span class="text-slate-400">
          <i class="pi pi-home !text-xs" />
        </span>
        <span class="text-sm text-slate-400">{{ currentCategory }}</span>
        <template v-if="currentBreadcrumbChild">
          <i class="pi pi-chevron-right !text-[10px] text-slate-400" />
          <span class="text-sm text-slate-400">{{ currentPageTitle }}</span>
          <i class="pi pi-chevron-right !text-[10px] text-slate-400" />
          <span class="text-sm font-medium text-slate-700 dark:text-slate-200">{{ currentBreadcrumbChild }}</span>
        </template>
        <template v-else>
          <i class="pi pi-chevron-right !text-[10px] text-slate-400" />
          <span class="text-sm font-medium text-slate-700 dark:text-slate-200">{{ currentPageTitle }}</span>
        </template>
      </div>

      <div class="layout-content-area scrollable-content flex-1 overflow-auto p-6">
        <slot />
      </div>
    </main>

    <div v-if="ui.mobileDrawerOpen" class="layout-mask" @click="ui.closeMobileDrawer" />
  </div>
</template>
