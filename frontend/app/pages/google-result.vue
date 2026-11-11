<script setup lang="ts">
const { t } = useI18n()
const route = useRoute()
const localePath = useLocalePath()
const allowed = ['GOOGLE_CANCELLED','GOOGLE_FAILED','GOOGLE_DISABLED','GOOGLE_EMAIL_UNVERIFIED','GOOGLE_ACCOUNT_BLOCKED','GOOGLE_EMAIL_CONFLICT','GOOGLE_REGISTRATION_REQUIRED','GOOGLE_FLOW_INVALID','GOOGLE_TERMS_REQUIRED']
const code = computed(() => typeof route.query.code === 'string' && allowed.includes(route.query.code) ? route.query.code : 'GOOGLE_FAILED')
useHead({ title: () => t('google.result'), meta: [{name:'robots',content:'noindex'},{name:'referrer',content:'no-referrer'}] })
</script>
<template><h1>{{ t('google.result') }}</h1><p role="alert">{{ t('google.errors.' + code) }}</p><NuxtLink :to="localePath('/login')">{{ t('auth.login') }}</NuxtLink> · <NuxtLink :to="localePath('/register')">{{ t('auth.register') }}</NuxtLink></template>
