<script setup lang="ts">
const { t } = useI18n()
usePublicHead('legal.cookies', 'legal.essential', '/cookies', '/en/cookies')

const optional = useOptionalCookies()
</script>
<template><article><h1>{{ t('legal.cookies') }}</h1>
<p>{{ t('legal.essential') }}</p>
<p>{{ t('legal.necessary') }}</p>
<section v-if="optional.available"><h2>{{ t('legal.optional') }}</h2><button :aria-pressed="optional.choice.value === 'allow'" @click="optional.set('allow')">{{ t('legal.allow') }}</button> <button :aria-pressed="optional.choice.value === 'deny'" @click="optional.set('deny')">{{ t('legal.deny') }}</button></section></article></template>
