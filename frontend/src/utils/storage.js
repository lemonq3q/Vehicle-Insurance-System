const Storage = {
  /**
   * 存储数据（支持过期时间）
   * @param {string} key 存储键名
   * @param {any} value 存储值（任意类型，会自动转JSON）
   * @param {number} expire 过期时间（单位：秒），不传则永久存储
   */
  set(key, value, expire) {
    try {
      const data = {
        value: value, // 实际存储的数据
        time: Date.now(), // 存储时的时间戳
        expire: expire ? Date.now() + expire * 1000 : null // 过期时间戳（毫秒）
      };
      localStorage.setItem(key, JSON.stringify(data));
    } catch (e) {
      console.error('localStorage 存储失败：', e);
    }
  },

  /**
   * 获取数据（自动判断是否过期）
   * @param {string} key 存储键名
   * @returns {any} 存储值（过期则返回null）
   */
  get(key) {
    try {
      const str = localStorage.getItem(key);
      if (!str) return null;

      const data = JSON.parse(str);
      // 没有设置过期时间 → 直接返回
      if (!data.expire) return data.value;

      // 已过期 → 删除数据并返回null
      if (Date.now() > data.expire) {
        localStorage.removeItem(key);
        return null;
      }

      // 未过期 → 返回数据
      return data.value;
    } catch (e) {
      console.error('localStorage 获取失败：', e);
      localStorage.removeItem(key); // 解析失败则删除脏数据
      return null;
    }
  },

  /**
   * 删除指定键名的数据
   * @param {string} key 存储键名
   */
  remove(key) {
    localStorage.removeItem(key);
  },

  /**
   * 清空所有 localStorage 数据
   */
  clear() {
    localStorage.clear();
  }
};

export default Storage;