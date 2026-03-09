<template>
  <main class="avalon-shell-bg relative min-h-screen overflow-hidden">
    <AppToastStack />

    <div class="pointer-events-none absolute inset-0">
      <div class="absolute -left-16 top-0 h-72 w-72 rounded-full bg-brand-300/45 blur-3xl dark:bg-brand-700/30" />
      <div class="absolute -right-16 bottom-8 h-80 w-80 rounded-full bg-cyan-200/50 blur-3xl dark:bg-cyan-900/35" />
      <div
        class="absolute inset-0 bg-[linear-gradient(to_right,rgba(15,23,42,0.045)_1px,transparent_1px),linear-gradient(to_bottom,rgba(15,23,42,0.045)_1px,transparent_1px)] bg-[size:24px_24px] dark:bg-[linear-gradient(to_right,rgba(148,163,184,0.07)_1px,transparent_1px),linear-gradient(to_bottom,rgba(148,163,184,0.07)_1px,transparent_1px)]"
      />
    </div>

    <div class="relative mx-auto flex min-h-screen w-full max-w-6xl items-center justify-center px-4 py-8 sm:px-8">
      <div class="w-full max-w-[460px]">
        <slot />
      </div>
    </div>
  </main>
</template>
