<template>
  <div class="home">
    <div class="top">
      <HeaderComponent></HeaderComponent>
    </div>
    <div class="bottom">
      <div class="left">
        <MenuComponent></MenuComponent>
      </div>
      <div class="right">
        <router-view></router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import HeaderComponent from '@/components/HeaderComponent.vue';
import MenuComponent from '@/components/MenuComponent.vue';



  
</script>


<style scoped>
.home {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.top {
  width: 100%;
  z-index: 1000;
  box-shadow: 0 0 20px rgba(0, 0, 0, .2);
}


.bottom {
  flex: 1;
  width: 100%;
  display: flex;
  overflow: hidden;
}

.left {
  height: 100%;
  z-index: 500;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
}
.right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 700px;
  overflow:auto;
  background-color: #F5F7FA;
  padding: 10px;
}
</style>
