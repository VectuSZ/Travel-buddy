<script setup lang="ts">
const {locale,locales,t}=useI18n()
const switchPath=useSwitchLocalePath()
const open=ref(false)
const root=ref<HTMLElement>()
const toggle=ref<HTMLButtonElement>()
function outside(event:PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value=false
}
function escape() {
  open.value=false
  toggle.value?.focus()
}
onMounted(()=>document.addEventListener('pointerdown',outside))
onBeforeUnmount(()=>document.removeEventListener('pointerdown',outside))
const interactive=ref(false)
onMounted(()=>{interactive.value=true})
</script>
<template>
<nav ref="root" :aria-label="t('languageMenu')" @keydown.esc="escape" @focusout="!root?.contains($event.relatedTarget as Node) && (open=false)">
<button ref="toggle" :disabled="!interactive" :aria-expanded="open" aria-controls="language-options" @click="open=!open">{{ locale.toUpperCase() }} <span aria-hidden="true">▾</span></button>
<ul v-show="open" id="language-options">
<li v-for="item in locales" :key="item.code"><NuxtLink :to="switchPath(item.code)" :aria-current="locale===item.code?'true':undefined" @click="open=false">{{ item.code.toUpperCase() }} <span v-if="locale===item.code" aria-hidden="true">✓</span></NuxtLink></li>
</ul>
</nav>
</template>
<style scoped>
nav{position:relative}ul{position:absolute;right:0;z-index:3;margin:0;padding:.25rem;list-style:none;background:white;border:1px solid #82998f;border-radius:6px}a{display:block;padding:.4rem .8rem;white-space:nowrap}
</style>
