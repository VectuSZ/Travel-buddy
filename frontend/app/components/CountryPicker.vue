<script setup lang="ts">
import {countryName,countryOptions,matchesCountry} from '~/utils/countries'
defineProps<{name:string;label:string}>()
const model = defineModel<string>({required:true})
const {locale,t} = useI18n()
const id = useId()
const open = ref(false)
const query = ref('')
const active = ref(-1)
const options = computed(()=>countryOptions(locale.value))
const matches = computed(()=>options.value.filter(option=>matchesCountry(option.search,query.value)))
const value = computed(()=>open.value ? query.value : countryName(model.value,locale.value))
function start() {
  open.value=true
  query.value=''
  active.value=-1
}
function select(code:string) {
  model.value=code
  open.value=false
}
function move(step:number) {
  if (!open.value) start()
  active.value=Math.min(matches.value.length-1,Math.max(0,active.value+step))
  document.getElementById(id+'-'+active.value)?.scrollIntoView({block:'nearest'})
}
function type(event:Event) {
  query.value=(event.target as HTMLInputElement).value
  open.value=true
  active.value=-1
  if (!query.value) model.value=''
}
function enter(event:KeyboardEvent) {
  if (!open.value) return
  event.preventDefault()
  const option=matches.value[active.value] || (matches.value.length===1 && matches.value[0])
  if (option) select(option.code)
}
</script>
<template>
<div class="picker">
<label :for="id">{{ label }}</label>
<input :id="id" :name="name" :value="value" role="combobox" autocomplete="off" aria-autocomplete="list"
 :aria-controls="id+'-options'" :aria-expanded="open" :aria-activedescendant="open && active>=0 ? id+'-'+active : undefined"
 @focus="start" @click="!open && start()" @input="type" @blur="open=false"
 @keydown.down.prevent="move(1)" @keydown.up.prevent="move(-1)" @keydown.enter="enter" @keydown.esc="open=false">
<ul v-show="open" :id="id+'-options'" role="listbox" :aria-label="label">
<li v-if="open && !matches.length" role="presentation">{{ t('search.noCountry') }}</li>
<template v-if="open"><li v-for="(option,index) in matches" :id="id+'-'+index" :key="option.code" role="option"
 :aria-selected="model===option.code" :class="{active:index===active}" @mousedown.prevent @click="select(option.code)">{{ option.name }}</li></template>
</ul>
</div>
</template>
<style scoped>
.picker{position:relative}.picker label{margin-bottom:0}.picker ul{position:absolute;z-index:2;inset-inline:0;max-height:14rem;overflow:auto;margin:0;padding:0;background:white;border:1px solid #82998f;list-style:none}.picker li{padding:.5rem .75rem;cursor:pointer}.picker li:hover,.picker .active{background:#e2efe8}
</style>
