<script setup lang="ts">
const props = defineProps<{ mode: 'register' | 'login' | 'forgot' | 'reset' }>()
const { t, locale } = useI18n()
const localePath = useLocalePath()
const { post, load } = useAuth()
const email = ref('')
const password = ref('')
const confirmation = ref('')
const accepted = ref(false)
const googleEnabled = ref(false)
const busy = ref(false)
const hydrated = ref(false)
const done = ref(false)
const errorCode = ref('')
let token = ''
const newPassword = computed(() => props.mode === 'register' || props.mode === 'reset')
onMounted(() => {
  hydrated.value = true
  if (props.mode === 'login' || props.mode === 'register') $fetch<{ enabled: boolean }>('/api/v1/auth/google/config').then(c => { googleEnabled.value = c.enabled }).catch(() => {})
  if (props.mode === 'reset') {
    token = new URLSearchParams(window.location.hash.slice(1)).get('token') || ''
    window.history.replaceState(null, '', window.location.pathname)
  }
})
async function google() {
  busy.value = true
  errorCode.value = ''
  try {
    const result = await post('google/start', { mode: props.mode, locale: locale.value, acceptTerms: accepted.value, termsVersion: 'local-draft-1' }) as { url: string }
    window.location.assign(result.url)
  } catch { errorCode.value = 'UNKNOWN'; busy.value = false }
}
async function submit() {
  errorCode.value = ''
  if (newPassword.value && password.value !== confirmation.value) { errorCode.value = 'PASSWORD_MISMATCH'; return }
  busy.value = true
  try {
    if (props.mode === 'register') {
      await post('register', { email: email.value, password: password.value, passwordConfirmation: confirmation.value,
        locale: locale.value, acceptTerms: accepted.value, termsVersion: 'local-draft-1' })
    } else if (props.mode === 'login') await post('login', { email: email.value, password: password.value })
    else if (props.mode === 'forgot') await post('password-reset/request', { email: email.value })
    else await post('password-reset/confirm', { token, password: password.value, passwordConfirmation: confirmation.value })
    password.value = ''; confirmation.value = ''
    if (props.mode === 'login' || props.mode === 'register') { await load(); await navigateTo(localePath('/account')) }
    else done.value = true
  } catch (e: unknown) {
    const code = (e as { data?: { code?: string } }).data?.code
    errorCode.value = code && ['VALIDATION_FAILED','PASSWORD_MISMATCH','EMAIL_UNAVAILABLE','INVALID_CREDENTIALS','UNAUTHENTICATED','CSRF_INVALID','ORIGIN_REJECTED','RATE_LIMITED','RESET_INVALID'].includes(code) ? code : 'UNKNOWN'
  } finally { busy.value = false }
}
</script>
<template>
  <h1>{{ t('auth.' + mode) }}</h1>
  <div v-if="done" role="status"><p>{{ t(mode === 'forgot' ? 'auth.resetSent' : 'auth.resetDone') }}</p><NuxtLink :to="localePath('/login')">{{ t('auth.login') }}</NuxtLink></div>
  <form v-else @submit.prevent="submit"><fieldset :disabled="!hydrated || busy">
    <label v-if="mode !== 'reset'">{{ t('auth.email') }}<input v-model="email" type="email" name="email" autocomplete="email" maxlength="254" required></label>
    <label v-if="mode !== 'forgot'">{{ t('auth.password') }}<input v-model="password" type="password" name="password" :autocomplete="newPassword ? 'new-password' : 'current-password'" minlength="12" maxlength="1024" required></label>
    <p v-if="newPassword">{{ t('auth.passwordHint') }}</p>
    <label v-if="newPassword">{{ t('auth.confirmation') }}<input v-model="confirmation" type="password" name="confirmation" autocomplete="new-password" minlength="12" maxlength="1024" required></label>
    <label v-if="mode === 'register'" class="checkbox"><input v-model="accepted" type="checkbox" required> {{ t('auth.accept') }} <NuxtLink :to="localePath('/terms')" target="_blank">{{ t('auth.terms') }}</NuxtLink> (local-draft-1)</label>
    <p v-if="errorCode" role="alert">{{ t('auth.errors.' + errorCode) }}</p>
    <button type="submit" :disabled="busy">{{ busy ? t('loading') : t('auth.' + mode) }}</button>
    <button v-if="googleEnabled" type="button" :disabled="busy || (mode === 'register' && !accepted)" @click="google">{{ t('google.continue') }}</button>
  </fieldset></form>
  <p v-if="mode === 'login'"><NuxtLink :to="localePath('/forgot-password')">{{ t('auth.forgot') }}</NuxtLink></p>
</template>
