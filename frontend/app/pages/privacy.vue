<script setup lang="ts">
const { t } = useI18n()
usePublicHead('legal.privacy', 'legal.public', '/privacy', '/en/privacy')
</script>
<template><article><h1>{{ t('legal.privacy') }}</h1>
<p>{{ t('legal.intro') }}</p>
<p>{{ t('legal.operator') }}</p>
<p>{{ t('legal.public') }}</p>
<p>{{ t('legal.private') }}</p>
<p>{{ t('legal.rights') }}</p>
<p>{{ t('legal.deletion') }}</p>
<p>{{ t('legal.retention') }}</p>
<p>{{ t('legal.reports') }}</p>
</article></template>
