import request from './request';
export const dashboardApi = { get: params => request.get('/dashboard', { params }) };
export const enterpriseApi = {
  list: params => request.get('/enterprises', { params }), options: () => request.get('/enterprises/options'), detail: id => request.get(`/enterprises/${id}`),
  usage: (id, params) => request.get(`/enterprises/${id}/usage`, { params }), compare: params => request.get('/enterprises/usage-comparison', { params }),
  members: (id, params) => request.get(`/enterprises/${id}/members`, { params }), financeSummary: id => request.get(`/enterprises/${id}/finance-summary`),
  finance: (id, type, params) => request.get(`/enterprises/${id}/${type}`, { params }), adjustBalance: (id, data) => request.post(`/enterprises/${id}/balance-adjustments`, data),
  setSubscription: (id, data) => request.put(`/enterprises/${id}/subscription`, data), cancelSubscription: (id, data) => request.delete(`/enterprises/${id}/subscription`, { data })
};
export const planApi = { list: () => request.get('/plans'), detail: id => request.get(`/plans/${id}`), create: data => request.post('/plans', data), update: (id, data) => request.put(`/plans/${id}`, data), updateStatus: (id, data) => request.patch(`/plans/${id}/status`, data) };
export const userApi = { list: params => request.get('/users', { params }), detail: id => request.get(`/users/${id}`), create: data => request.post('/users', data), update: (id, data) => request.put(`/users/${id}`, data), updateStatus: (id, data) => request.patch(`/users/${id}/status`, data), resetPassword: (id, data) => request.post(`/users/${id}/reset-password`, data), remove: (id, data) => request.delete(`/users/${id}`, { data }) };
