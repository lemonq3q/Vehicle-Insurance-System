<template>
  <div class="hello">
    <h1>调试页面，正式上线会删除</h1>
    <!-- 访问 state 和 getters -->
    <p>当前计数：{{ count }}</p>
    <p>计数加倍：{{ doubleCount }}</p>
    <p>当前用户：{{ userName }}</p>
    <p>setup测试：{{ localNum }}</p>
    <p>setup测试：{{ localUser.username }}</p>
    <p>token: {{ token }}</p>
    <p>perms: {{ perms }}</p>
    <!-- 操作按钮 -->
    <button @click="handleIncrement">+1（同步）</button>
    <button @click="handleIncrementAsync">+1（延迟1秒）</button>
    <button @click="handleFetchUser">加载用户</button>
    <button @click="handleNumAdd(1)">local +1</button>
    <button @click="handleUsernameChange('666')">change username</button>
  </div>
</template>

<script setup>
  import { useStore } from 'vuex'
  import { computed, reactive, ref } from 'vue'

  // 1. 获取 store 实例
  const store = useStore()

  // 2. 访问 state（通过 computed 保持响应式）
  const count = computed(() => store.state.hello.count)
  const userName = computed(() => store.state.login.user.name)

  // 3. 访问 getters
  const doubleCount = computed(() => store.getters['hello/doubleCount'])

  const token = computed(() => store.state.login.token)
  const perms = computed(() => store.state.login.user.perms)

  let localNum = ref(0)
  const localUser = reactive({
    username: 'zhangsan',
    male: 1
  })

  // 4. 提交 mutations（同步修改状态）
  const handleIncrement = () => {
    store.commit('hello/increment') // 调用 mutation
  }

  // 5. 调用 actions（处理异步操作）
  const handleIncrementAsync = () => {
    store.dispatch('hello/incrementAsync', 1000) // 传递参数（延迟时间）
  }

  const handleFetchUser = () => {
    store.dispatch('hello/fetchUser') // 调用异步 action
  }

  const handleNumAdd = (num) => {
    localNum.value += num
  }

  const handleUsernameChange = (username) => {
    localUser.username = username
  }
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.hello {
  background-color: #fff;
}
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
button {
  margin: 0 8px;
  padding: 4px 8px;
}
</style>
