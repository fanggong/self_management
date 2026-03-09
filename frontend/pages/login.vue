<script setup lang="ts">
import { useAuthStore } from '~/stores/auth';

definePageMeta({
  layout: 'minimal',
  middleware: 'guest'
});

const auth = useAuthStore();
const { showToast } = useAppToast();
const form = reactive({
  principal: '',
  password: ''
});

const touched = ref(false);

const principalError = computed(() => {
  if (!touched.value) {
    return '';
  }

  return form.principal.trim() ? '' : 'Please enter your username.';
});

const passwordError = computed(() => {
  if (!touched.value) {
    return '';
  }

  return form.password ? '' : 'Please enter your password.';
});

const hasClientError = computed(() => Boolean(principalError.value || passwordError.value));

const firstClientError = computed(() => principalError.value || passwordError.value || '');

const submit = async () => {
  touched.value = true;

  if (hasClientError.value) {
    showToast('error', firstClientError.value || 'Please complete the required fields.');
    return;
  }

  const result = await auth.login({
    principal: form.principal.trim(),
    password: form.password
  });

  if (!result.success) {
    showToast('error', result.message ?? 'Login failed. Please try again later.');
    return;
  }

  showToast('success', 'Signed in successfully.');
  await navigateTo('/app');
};
</script>

<template>
  <Card class="auth-card rounded-3xl border-0">
    <template #title>
      <div class="mb-5 space-y-2">
        <Tag severity="secondary" value="Account Access" />
        <h1 class="text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">Welcome Back</h1>
        <p class="text-sm font-normal leading-relaxed text-slate-600 dark:text-slate-300">
          Enter your username and password to sign in.
        </p>
      </div>
    </template>

    <template #content>
      <form class="space-y-5" @submit.prevent="submit">
        <div>
          <label for="login-principal" class="auth-label">Username</label>
          <IconField>
            <InputIcon class="pi pi-user" />
            <InputText
              id="login-principal"
              v-model="form.principal"
              placeholder="Enter your username"
              class="w-full"
              autocomplete="username"
              :invalid="Boolean(principalError)"
              @blur="touched = true"
            />
          </IconField>
          <small v-if="principalError" class="auth-error">{{ principalError }}</small>
        </div>

        <div>
          <label for="login-password" class="auth-label">Password</label>
          <Password
            id="login-password"
            v-model="form.password"
            placeholder="Enter your password"
            :feedback="false"
            toggle-mask
            input-class="w-full"
            class="w-full"
            autocomplete="current-password"
            :invalid="Boolean(passwordError)"
            @blur="touched = true"
          />
          <small v-if="passwordError" class="auth-error">{{ passwordError }}</small>
        </div>

        <Button type="submit" label="Sign In" class="w-full" :loading="auth.loading" />
      </form>

      <Divider />

      <p class="text-sm text-slate-500 dark:text-slate-400">
        Don't have an account?
        <NuxtLink class="font-medium text-brand-700 hover:underline dark:text-brand-300" to="/register">
          Create one
        </NuxtLink>
      </p>
    </template>
  </Card>
</template>
