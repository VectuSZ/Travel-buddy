<script setup lang="ts">
const { t } = useI18n()
useHead({ title: () => t('auth.register') + ' — Travel Buddy', meta: [{ name: 'robots', content: 'noindex' }] })
</script>
<template><AuthForm mode="register" /></template>
