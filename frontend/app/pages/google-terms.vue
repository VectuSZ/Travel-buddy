<script setup lang="ts">
const { t } = useI18n()
const localePath = useLocalePath()
const { post, load } = useAuth()
const accepted = ref(false)
const busy = ref(false)
const confirmation = ref('')
const result = (code: string) => navigateTo({ path: localePath('/google-result'), query: { code } }, { replace: true })
onMounted(async () => {
  try {
    const pending = await $fetch<{ confirmation: string }>('/api/v1/auth/google/consent', { cache: 'no-store' })
    confirmation.value = pending.confirmation
    if (!confirmation.value) await result('GOOGLE_FLOW_INVALID')
  } catch { await result('GOOGLE_FAILED') }
})
async function decide(create: boolean) {
  if (busy.value || !confirmation.value || (create && !accepted.value)) return
  busy.value = true
  try {
    await post('google/consent', { confirmation: confirmation.value, accepted: create, termsVersion: 'local-draft-2' })
    if (create) await load()
    await navigateTo(localePath(create ? '/account' : '/login'), { replace: true })
  } catch (error) {
    await result((error as { data?: { code?: string } }).data?.code || 'GOOGLE_FAILED')
  } finally { busy.value = false }
}
</script>
<template>
  <form v-if="confirmation" @submit.prevent="decide(true)">
    <h1>{{ t('google.termsTitle') }}</h1>
    <fieldset :disabled="busy">
      <label class="checkbox"><input v-model="accepted" type="checkbox" required>
        {{ t('auth.accept') }} <NuxtLink :to="localePath('/terms')" target="_blank">{{ t('auth.terms') }}</NuxtLink> (local-draft-2)
      </label>
      <NuxtLink :to="localePath('/privacy')" target="_blank">{{ t('legal.privacy') }}</NuxtLink>
      <button type="submit" :disabled="!accepted">{{ t('google.create') }}</button>
      <button type="button" @click="decide(false)">{{ t('google.cancel') }}</button>
    </fieldset>
  </form>
</template>
