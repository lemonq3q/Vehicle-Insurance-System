<template><teleport to="body"><transition name="toast"><div v-if="message" class="app-toast" :class="type" role="status">{{message}}</div></transition></teleport></template>
<script>export default{name:'AppToast',props:{message:String,type:{type:String,default:'success'}}};</script>
