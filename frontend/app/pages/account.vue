<script setup lang="ts">
const { t } = useI18n()
const localePath = useLocalePath()
const { account, ready, load, logout } = useAuth()
const error = ref(false)
onMounted(async () => {
  try { await load(); if (!account.value) await navigateTo(localePath('/login')) }
  catch { error.value = true }
})
async function signOut() {
  try { await logout(); await navigateTo(localePath('/login')) }
  catch { error.value = true }
}
useHead({ title: () => t('auth.account'), meta: [{ name: 'robots', content: 'noindex' }] })
</script>
<template><h1>{{ t('auth.account') }}</h1><ClientOnly><p v-if="error" role="alert">{{ t('error') }}</p><template v-if="account"><p>{{ account.email }}</p><button @click="signOut">{{ t('auth.logout') }}</button></template><p v-else-if="!ready">{{ t('loading') }}</p></ClientOnly></template>
