<script setup lang="ts">
const { t } = useI18n()
usePublicHead('auth.terms', 'legal.service', '/terms', '/en/terms')
</script>
<template><article><h1>{{ t('auth.terms') }}</h1>
<p>{{ t('legal.intro') }}</p>
<p>{{ t('legal.operator') }}</p>
<p>{{ t('legal.service') }}</p>
<p>{{ t('legal.conduct') }}</p>
<p>{{ t('legal.reports') }}</p>
<p>{{ t('legal.public') }}</p>
<p>{{ t('legal.private') }}</p>
<p>{{ t('legal.deletion') }}</p>
<small>local-draft-2</small></article></template>
