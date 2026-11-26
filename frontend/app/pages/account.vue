<script setup lang="ts">
const { t, locale } = useI18n()
const localePath = useLocalePath()
const { account, ready, load, logout, post } = useAuth()
const error = ref(false)
const confirmed = ref(false)
const password = ref('')
async function reauthenticateGoogle() {
  try {
    const result = await post('google/start', { reauthenticate: true, locale: locale.value }) as { url: string }
    window.location.assign(result.url)
  } catch { error.value = true }
}
const methods = ref<{password: boolean, google: boolean, enabled: boolean}>()
async function linkGoogle() {
  try {
    const result = await post('google/start', { link: true, locale: locale.value }) as { url: string }
    window.location.assign(result.url)
  } catch { error.value = true }
}
const deleting = ref(false)
async function deleteAccount() {
  if (!confirmed.value || deleting.value) return
  deleting.value = true
  error.value = false
  try { if (password.value) await post('reauthenticate', { password: password.value });
    password.value = '';
    const csrf = await $fetch<{ token: string }>('/api/v1/auth/csrf', { cache: 'no-store', retry: 0 });
    await $fetch('/api/v1/me', { method: 'DELETE', headers: { 'X-CSRF-TOKEN': csrf.token }, retry: 0 }); account.value = null; await navigateTo(localePath("/")) }
  catch { error.value = true }
  finally { deleting.value = false }
}
onMounted(async () => {
  try { await load(); if (!account.value) await navigateTo(localePath('/login'))
    else methods.value = await $fetch<{password: boolean, google: boolean, enabled: boolean}>('/api/v1/auth/google/methods', { cache: 'no-store' }) }
  catch { error.value = true }
})
async function signOut() {
  try { await logout(); await navigateTo(localePath('/login')) }
  catch { error.value = true }
}
useHead({ title: () => t('auth.account'), meta: [{ name: 'robots', content: 'noindex' }] })
</script>
<template><h1>{{ t('auth.account') }}</h1><ClientOnly><p v-if="error" role="alert">{{ t('error') }}</p><template v-if="account"><p>{{ account.email }}</p><button data-testid="logout" @click="signOut">{{ t('auth.logout') }}</button><section v-if="methods">
  <h2>{{ t('google.methods') }}</h2>
  <p>{{ t(methods.password ? 'google.passwordOn' : 'google.passwordOff') }}</p>
  <p>{{ t(methods.google ? 'google.connected' : 'google.disconnected') }}</p>
  <button v-if="methods.enabled && methods.password && !methods.google" @click="linkGoogle">{{ t('google.link') }}</button>
</section><ProfileEditor /><section><h2>{{ t("deletion.title") }}</h2><p>{{ t("deletion.warning") }}</p><label v-if="methods?.password">{{ t("deletion.password") }}<input v-model="password" type="password" autocomplete="current-password"></label><button v-if="methods?.google && methods.enabled" @click="reauthenticateGoogle">{{ t("deletion.google") }}</button><label><input v-model="confirmed" type="checkbox"> {{ t("deletion.confirm") }}</label><button :disabled="!confirmed || deleting" @click="deleteAccount">{{ t("deletion.title") }}</button></section></template><p v-else-if="!ready">{{ t('loading') }}</p></ClientOnly></template>
