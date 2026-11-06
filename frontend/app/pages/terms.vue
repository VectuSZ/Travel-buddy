<script setup lang="ts">
const { t } = useI18n()
</script>
<template><h1>{{ t('auth.terms') }}</h1><article><p><strong>{{ t('auth.draftWarning') }}</strong></p><p>{{ t('auth.draftText') }}</p><p>{{ t('auth.draftMissing') }}</p><small>local-draft-1</small></article></template>
