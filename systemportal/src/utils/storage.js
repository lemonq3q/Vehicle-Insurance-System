const Storage = {
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
  remove(key) {
    localStorage.removeItem(key);
  }
};

export default Storage;
