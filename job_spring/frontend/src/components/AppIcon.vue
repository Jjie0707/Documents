<script setup>
defineProps({ name: { type: String, default: 'note' } })
const paths = {
  plus: ['M12 5v14M5 12h14'],
  search: ['M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13', 'm16 16 4 4'],
  'arrow-left': ['M19 12H5m6-6-6 6 6 6'],
  'arrow-right': ['M5 12h14m-6-6 6 6-6 6'],
  briefcase: ['M4 7h16v13H4z', 'M9 7V4h6v3M4 12c5 3 11 3 16 0M10 13v3h4v-3'],
  image: ['M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2', 'm3 16 5-5 5 5 3-3 5 5M16 7h.01'],
  note: ['M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2', 'M8 8h8M8 12h8M8 16h5'],
  chat: ['M21 11a8 8 0 0 1-8 8H7l-4 3V11a9 9 0 0 1 18 0', 'M8 10h8M8 14h5'],
  progress: ['M4 5h16M4 12h16M4 19h16', 'M8 3v4M16 10v4M10 17v4'],
  settings: ['M4 7h16M4 17h16', 'M9 4v6M15 14v6'],
  check: ['m5 12 4 4L19 6'],
}
</script>

<template>
  <svg class="app-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
       stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <path v-for="(path, index) in paths[name] || paths.note" :key="index" :d="path" />
  </svg>
</template>
