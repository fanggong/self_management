<script setup lang="ts">
type MenuItem = {
  key: string;
  label: string;
  icon?: string;
  children?: Array<{
    key: string;
    label: string;
    icon?: string;
  }>;
};

const emit = defineEmits<{
  navigate: [];
}>();

const menuGroups: Array<{
  title: string;
  items: MenuItem[];
}> = [
  {
    title: 'Dashboards',
    items: [
      { key: 'health', label: 'Health', icon: 'pi pi-heart' },
      { key: 'finance', label: 'Finance', icon: 'pi pi-chart-line' }
    ]
  },
  {
    title: 'Apps',
    items: [
      {
        key: 'connector',
        label: 'Connector',
        icon: 'pi pi-link',
        children: [
          { key: 'connector-settings', label: 'Settings', icon: 'pi pi-sliders-h' },
          { key: 'connector-tasks', label: 'Tasks', icon: 'pi pi-list' }
        ]
      }
    ]
  },
  {
    title: 'Data Model',
    items: [
      { key: 'staging', label: 'Staging', icon: 'pi pi-database' },
      { key: 'intermediate', label: 'Intermediate', icon: 'pi pi-server' },
      { key: 'marts', label: 'Marts', icon: 'pi pi-table' },
      { key: 'logs', label: 'Logs', icon: 'pi pi-history' }
    ]
  },
  {
    title: 'System',
    items: [{ key: 'settings', label: 'Settings', icon: 'pi pi-cog' }]
  }
];

const route = useRoute();
const currentTab = computed(() => {
  const tab = String(route.query.tab ?? 'health');
  return tab === 'connector' ? 'connector-settings' : tab;
});
const expandedParents = ref<Record<string, boolean>>({});

const isActive = (key: string) => currentTab.value === key || (!route.query.tab && key === 'health');
const hasActiveChild = (item: MenuItem) => item.children?.some((child) => isActive(child.key)) ?? false;
const toRoute = (key: string) => ({ path: '/app', query: { tab: key } });
const isExpanded = (key: string) => expandedParents.value[key] ?? false;
const isParentHighlighted = (item: MenuItem) => isExpanded(item.key);

const setExpandedParent = (key: string | null) => {
  const nextState: Record<string, boolean> = {};

  for (const group of menuGroups) {
    for (const item of group.items) {
      if (item.children?.length) {
        nextState[item.key] = item.key === key;
      }
    }
  }

  expandedParents.value = nextState;
};

const toggleParent = (key: string) => {
  setExpandedParent(isExpanded(key) ? null : key);
};

const onNavigate = () => {
  emit('navigate');
};

watch(
  currentTab,
  () => {
    const hasAnyActiveChild = menuGroups.some((group) =>
      group.items.some((item) => item.children?.length && hasActiveChild(item))
    );

    if (!hasAnyActiveChild) {
      setExpandedParent(null);
    }
  },
  { immediate: true }
);
</script>

<template>
  <nav aria-label="Main Navigation">
    <ul class="layout-menu">
      <template v-for="(group, groupIndex) in menuGroups" :key="group.title">
        <li class="layout-root-menuitem">
        <div class="layout-menuitem-root-text">{{ group.title }}</div>
        <ul class="layout-root-submenulist">
          <li v-for="item in group.items" :key="item.key">
            <template v-if="item.children?.length">
              <a
                href=""
                :title="item.label"
                class="app-menu-link app-menu-link-parent"
                :class="isParentHighlighted(item) ? 'app-menu-link-parent-active' : ''"
                :aria-expanded="isExpanded(item.key)"
                @click.prevent="toggleParent(item.key)"
              >
                <span class="app-menu-link-main">
                  <i :class="[item.icon, 'layout-menuitem-icon']" />
                  <span class="layout-menuitem-text">{{ item.label }}</span>
                </span>
                <i class="pi pi-angle-down submenu-chevron" :class="{ 'submenu-chevron-open': isExpanded(item.key) }" />
              </a>

              <transition name="menu-subtree">
                <ul v-show="isExpanded(item.key)" class="layout-submenu-list">
                  <li v-for="child in item.children" :key="child.key">
                    <NuxtLink
                      :to="toRoute(child.key)"
                      :title="child.label"
                      class="app-submenu-link"
                      :class="isActive(child.key) ? 'active-route' : ''"
                      @click="onNavigate"
                    >
                      <span class="app-submenu-link-content">
                        <i v-if="child.icon" :class="[child.icon, 'layout-submenu-icon']" />
                        <span class="layout-menuitem-text">{{ child.label }}</span>
                      </span>
                    </NuxtLink>
                  </li>
                </ul>
              </transition>
            </template>

            <NuxtLink
              v-else
              :to="toRoute(item.key)"
              :title="item.label"
              class="app-menu-link"
              :class="isActive(item.key) ? 'active-route' : ''"
              @click="onNavigate"
            >
              <i :class="[item.icon, 'layout-menuitem-icon']" />
              <span class="layout-menuitem-text">{{ item.label }}</span>
            </NuxtLink>
          </li>
        </ul>
        </li>
        <li v-if="groupIndex < menuGroups.length - 1" class="menu-separator" role="separator" />
      </template>
    </ul>
  </nav>
</template>
