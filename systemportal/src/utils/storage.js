const Storage = {
  /**
   * * 序列化门户本地数据；传入有效期时额外写入标记和绝对过期时间，普通数据保持原值结构。
   */
  set(key, value, expireSeconds) {
    const payload = expireSeconds
      ? {
          __portalStorage: true,
          value,
          expiresAt: Date.now() + expireSeconds * 1000
        }
      : value;
    localStorage.setItem(key, JSON.stringify(payload));
  },
  /**
   * * 读取并解析门户本地数据，自动删除已过期或损坏的内容；未设置有效期的数据直接返回。
   */
  get(key) {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    try {
      const payload = JSON.parse(raw);
      if (!payload || payload.__portalStorage !== true) return payload;
      if (Date.now() >= payload.expiresAt) {
        localStorage.removeItem(key);
        return null;
      }
      return payload.value;
    } catch (error) {
      localStorage.removeItem(key);
      return null;
    }
  },
  /**
   * * 删除指定门户缓存键，供注销和 401 会话清理使用。
   */
  remove(key) {
    localStorage.removeItem(key);
  }
};

export default Storage;
