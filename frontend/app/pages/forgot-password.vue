<script setup lang="ts">
const { t } = useI18n()
useHead({ title: () => t('auth.forgot') + ' — Travel Buddy', meta: [{ name: 'robots', content: 'noindex' }] })
</script>
<template><AuthForm mode="forgot" /></template>
