import { mockRequest } from '@/mock/portalMock';

function parseRequestData(data) {
  if (!data) return {};
  if (typeof data !== 'string') return data;
  try {
    return JSON.parse(data);
  } catch (error) {
    return data;
  }
}

export default async function mockAxiosAdapter(config) {
  const data = await mockRequest({
    url: config.url,
    method: String(config.method || 'GET').toUpperCase(),
    data: parseRequestData(config.data),
    params: config.params || {}
  });

  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: { 'content-type': 'application/json' },
    config,
    request: null
  };
}
