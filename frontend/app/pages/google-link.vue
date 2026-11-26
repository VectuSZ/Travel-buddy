<script setup lang="ts">
const { t } = useI18n()
const localePath = useLocalePath()
const { post, load } = useAuth()
interface PendingLink {confirmation?: string, email?: string, settings?: boolean}
const pending = ref<PendingLink>({})
const password = ref('')
const confirming = ref(false)
const busy = ref(false)
const error = ref('')
async function result(code: string) {
  await navigateTo({ path: localePath('/google-result'), query: { code } }, { replace: true })
}
onMounted(async () => {
  try {
    pending.value = await $fetch<PendingLink>('/api/v1/auth/google/link', { cache: 'no-store' })
    if (!pending.value.confirmation) await result('GOOGLE_FLOW_INVALID')
  } catch { await result('GOOGLE_FAILED') }
})
async function decide(destination?: '/login' | '/account' | '/forgot-password') {
  if (busy.value || !pending.value.confirmation) return
  busy.value = true
  error.value = ''
  try {
    await post('google/link', { confirmation: pending.value.confirmation, cancel: !!destination,
      password: destination ? undefined : password.value })
    if (!destination) await load()
    await navigateTo(localePath(destination || '/account'), { replace: true })
  } catch (e) {
    const code = (e as {data?: {code?: string}}).data?.code
    if (code === 'INVALID_CREDENTIALS' || code === 'RATE_LIMITED') error.value = t('auth.errors.' + code)
    else if (destination && code === 'GOOGLE_FLOW_INVALID') await navigateTo(localePath(destination))
    else await result(code || 'GOOGLE_FAILED')
  } finally { password.value = ''; busy.value = false }
}
</script>
<template>
  <section v-if="pending.confirmation">
    <h1>{{ t('google.link') }}</h1>
    <p v-if="!pending.settings">{{ t('google.linkOffer') }}</p>
    <p>{{ t('google.selected', {email: pending.email}) }}</p>
    <button v-if="!confirming" @click="confirming = true">{{ t('google.link') }}</button>
    <form v-else @submit.prevent="decide()">
      <label>{{ t('google.prove') }}<input v-model="password" type="password" autocomplete="current-password" maxlength="1024" required></label>
      <p v-if="error" role="alert">{{ error }}</p>
      <button :disabled="busy" type="submit">{{ t('google.link') }}</button>
    </form>
    <button :disabled="busy" @click="decide(pending.settings ? '/account' : '/login')">{{ t(pending.settings ? 'auth.account' : 'google.cancel') }}</button>
    <button :disabled="busy" @click="decide('/forgot-password')">{{ t('auth.forgot') }}</button>
  </section>
</template>
