import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, apiRequest, ApiRequestError } from './client';

const errorBody = (status: number) => ({ status, message: `server says ${status}`, fieldErrors: [{ field: 'name', message: 'Required' }], traceId: 'trace-123' });

afterEach(() => {
  vi.unstubAllGlobals();
  window.sessionStorage.clear();
});

describe('central API client', () => {
  it.each([400, 401, 403, 404, 409, 422, 500])('maps HTTP %i errors and retains validation metadata', async status => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(errorBody(status)), { status, headers: { 'Content-Type': 'application/json' } })));
    await expect(apiRequest('/api/v1/example')).rejects.toMatchObject({ status, fieldErrors: errorBody(status).fieldErrors, traceId: 'trace-123', message: expect.stringContaining('name: Required') });
  });

  it('reports network errors as retryable API failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(apiFetch('/api/v1/example')).rejects.toMatchObject({ status: 0, message: expect.stringContaining('reach the server') });
  });

  it('sends the session token and clears it after an expired-session response', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'test-token');
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(errorBody(401)), { status: 401, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    const unauthorized = vi.fn();
    window.addEventListener('erp:unauthorized', unauthorized);
    try {
      await expect(apiFetch('/api/v1/example')).rejects.toBeInstanceOf(ApiRequestError);
      expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer test-token');
      expect(window.sessionStorage.getItem('erp-portal-access-token')).toBeNull();
      expect(unauthorized).toHaveBeenCalledOnce();
    } finally {
      window.removeEventListener('erp:unauthorized', unauthorized);
    }
  });

  it('does not send a stale token with a login request', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'old-token');
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    await apiFetch('/api/v1/auth/login', { method: 'POST', body: '{}' });
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).has('Authorization')).toBe(false);
  });

  it('builds query strings correctly ignoring nullish values', async () => {
    const { buildQueryString } = await import('./client');
    expect(buildQueryString({ a: '1', b: 2, c: '', d: null, e: undefined })).toBe('?a=1&b=2');
    expect(buildQueryString()).toBe('');
    expect(buildQueryString({})).toBe('');
  });

  it('performs apiPost, apiPut, apiPatch, and apiDelete with proper serialization and methods', async () => {
    const { apiGet, apiPost, apiPut, apiPatch, apiDelete } = await import('./client');
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ success: true }), { status: 200, headers: { 'Content-Type': 'application/json' } })));
    vi.stubGlobal('fetch', fetchMock);

    await apiGet('/api/v1/test', { search: 'query' });
    expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('/api/v1/test?search=query'), expect.objectContaining({ method: 'GET' }));

    await apiPost('/api/v1/test', { name: 'Item' });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/test', expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: 'Item' }) }));

    await apiPut('/api/v1/test/1', { name: 'Updated' });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/test/1', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ name: 'Updated' }) }));

    await apiPatch('/api/v1/test/1', { active: true });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/test/1', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ active: true }) }));

    await apiDelete('/api/v1/test/1');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/test/1', expect.objectContaining({ method: 'DELETE' }));
  });

  it('extracts field errors and error messages correctly', async () => {
    const { extractFieldErrors, extractErrorMessage, ApiRequestError } = await import('./client');
    const err = new ApiRequestError(400, {
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Invalid input',
      path: '/api/v1/test',
      timestamp: new Date().toISOString(),
      traceId: 'trace-1',
      fieldErrors: [{ field: 'email', message: 'Invalid format' }],
    });
    expect(extractFieldErrors(err)).toEqual({ email: 'Invalid format' });
    expect(extractErrorMessage(err)).toContain('Invalid input');
    expect(extractErrorMessage(new Error('Simple failure'))).toBe('Simple failure');
    expect(extractErrorMessage('string' as unknown)).toBe('string');
  });
});
