<script setup lang="ts">
const { t } = useI18n()
const localePath = useLocalePath()
const { account, load, logout } = useAuth()
const error = ref(false)
onMounted(() => load().catch(() => { error.value = true }))
async function signOut() {
  error.value = false
  try { await logout(); await navigateTo(localePath('/login')) }
  catch { error.value = true }
}
</script>
<template><nav :aria-label="t('auth.account')">
  <template v-if="account"><NuxtLink :to="localePath('/account')">{{ t('auth.account') }}</NuxtLink> <button @click="signOut">{{ t('auth.logout') }}</button></template>
  <template v-else><NuxtLink :to="localePath('/login')">{{ t('auth.login') }}</NuxtLink> · <NuxtLink :to="localePath('/register')">{{ t('auth.register') }}</NuxtLink></template>
  <span v-if="error" role="alert">{{ t('error') }}</span>
</nav></template>
