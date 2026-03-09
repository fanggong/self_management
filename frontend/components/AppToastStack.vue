<script setup lang="ts">
const { toastMessages } = useAppToast();
</script>

<template>
  <Teleport to="body">
    <TransitionGroup name="toast-stack" tag="div" class="app-toast-stack">
      <div
        v-for="message in toastMessages"
        :key="message.id"
        class="app-toast"
        :class="message.severity === 'success' ? 'app-toast-success' : 'app-toast-error'"
      >
        <i :class="message.severity === 'success' ? 'pi pi-check-circle' : 'pi pi-exclamation-circle'" />
        <span>{{ message.text }}</span>
      </div>
    </TransitionGroup>
  </Teleport>
</template>
