<script setup lang="ts">
const emit = defineEmits<{
  navigate: [];
}>();

const menuGroups = [
  {
    title: 'Dashboards',
    items: [
      { key: 'health', label: 'Health', icon: 'pi pi-heart' },
      { key: 'finance', label: 'Finance', icon: 'pi pi-chart-line' }
    ]
  },
  {
    title: 'Apps',
    items: [{ key: 'connector', label: 'Connector', icon: 'pi pi-link' }]
  },
  {
    title: 'Data',
    items: [
      { key: 'staging', label: 'Staging', icon: 'pi pi-database' },
      { key: 'intermediate', label: 'Intermediate', icon: 'pi pi-server' },
      { key: 'marts', label: 'Marts', icon: 'pi pi-table' },
      { key: 'logs', label: 'Logs', icon: 'pi pi-file' }
    ]
  },
  {
    title: 'System',
    items: [{ key: 'settings', label: 'Settings', icon: 'pi pi-cog' }]
  }
];

const route = useRoute();

const isActive = (key: string) => route.query.tab === key || (!route.query.tab && key === 'health');
const toRoute = (key: string) => ({ path: '/app', query: { tab: key } });

const onNavigate = () => {
  emit('navigate');
};
</script>

<template>
  <nav aria-label="Main Navigation">
    <ul class="layout-menu">
      <template v-for="(group, groupIndex) in menuGroups" :key="group.title">
        <li class="layout-root-menuitem">
        <div class="layout-menuitem-root-text">{{ group.title }}</div>
        <ul class="layout-root-submenulist">
          <li v-for="item in group.items" :key="item.key">
            <NuxtLink
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
