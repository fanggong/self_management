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
  password: '',
  confirmPassword: ''
});

const submitted = ref(false);
const fieldTouched = reactive({
  principal: false,
  password: false,
  confirmPassword: false
});

const shouldValidate = (field: keyof typeof fieldTouched) => submitted.value || fieldTouched[field];

const principalError = computed(() => {
  if (!shouldValidate('principal')) {
    return '';
  }

  return form.principal.trim() ? '' : 'Please enter your username.';
});

const passwordError = computed(() => {
  if (!shouldValidate('password')) {
    return '';
  }

  if (!form.password) {
    return 'Please enter your password.';
  }

  if (form.password.length < 6) {
    return 'Password must be at least 6 characters.';
  }

  return '';
});

const confirmPasswordError = computed(() => {
  if (!shouldValidate('confirmPassword')) {
    return '';
  }

  if (!form.confirmPassword) {
    return 'Please confirm your password.';
  }

  return form.confirmPassword === form.password ? '' : 'Passwords do not match.';
});

const hasClientError = computed(() => {
  return Boolean(principalError.value || passwordError.value || confirmPasswordError.value);
});

const firstClientError = computed(() => {
  return principalError.value || passwordError.value || confirmPasswordError.value || '';
});

const onBlur = (field: keyof typeof fieldTouched) => {
  fieldTouched[field] = true;
};

const submit = async () => {
  submitted.value = true;

  if (hasClientError.value) {
    showToast('error', firstClientError.value || 'Please complete the required fields.');
    return;
  }

  const result = await auth.register({
    displayName: form.principal.trim(),
    principal: form.principal.trim(),
    password: form.password,
    confirmPassword: form.confirmPassword
  });

  if (!result.success) {
    showToast('error', result.message ?? 'Registration failed. Please try again later.');
    return;
  }

  showToast('success', result.message ?? 'Registration successful. Please sign in.');
  await navigateTo('/login');
};
</script>

<template>
  <Card class="auth-card rounded-3xl border-0">
    <template #title>
      <div class="mb-5 space-y-2">
        <Tag severity="secondary" value="Account Registration" />
        <h1 class="text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">Create Account</h1>
        <p class="text-sm font-normal leading-relaxed text-slate-600 dark:text-slate-300">
          Set your username and password to register.
        </p>
      </div>
    </template>

    <template #content>
      <form class="space-y-5" @submit.prevent="submit">
        <div>
          <label for="register-principal" class="auth-label">Username</label>
          <IconField>
            <InputIcon class="pi pi-user" />
            <InputText
              id="register-principal"
              v-model="form.principal"
              class="w-full"
              placeholder="Enter your username"
              autocomplete="username"
              :invalid="Boolean(principalError)"
              @blur="onBlur('principal')"
            />
          </IconField>
          <small v-if="principalError" class="auth-error">{{ principalError }}</small>
        </div>

        <div>
          <label for="register-password" class="auth-label">Password</label>
          <Password
            id="register-password"
            v-model="form.password"
            toggle-mask
            class="w-full"
            input-class="w-full"
            autocomplete="new-password"
            :invalid="Boolean(passwordError)"
            @blur="onBlur('password')"
          />
          <small v-if="passwordError" class="auth-error">{{ passwordError }}</small>
        </div>

        <div>
          <label for="register-confirm-password" class="auth-label">Confirm Password</label>
          <Password
            id="register-confirm-password"
            v-model="form.confirmPassword"
            toggle-mask
            :feedback="false"
            class="w-full"
            input-class="w-full"
            autocomplete="new-password"
            :invalid="Boolean(confirmPasswordError)"
            @blur="onBlur('confirmPassword')"
          />
          <small v-if="confirmPasswordError" class="auth-error">{{ confirmPasswordError }}</small>
        </div>

        <Button type="submit" label="Create Account" class="w-full" :loading="auth.loading" />
      </form>

      <Divider />

      <p class="text-sm text-slate-500 dark:text-slate-400">
        Already have an account?
        <NuxtLink class="font-medium text-brand-700 hover:underline dark:text-brand-300" to="/login">
          Sign In
        </NuxtLink>
      </p>
    </template>
  </Card>
</template>
