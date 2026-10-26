import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProviders } from '../app/AppProviders';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppRoutes } from '../routes';

const settingsUser = {
  id: 1,
  username: 'settings-admin',
  fullName: 'Settings Administrator',
  roles: ['ADMIN'],
  permissions: ['SETTINGS:READ', 'SETTINGS:UPDATE'],
};

const institutionSetting = {
  key: 'institution.name',
  value: 'Northfield School',
  category: 'INSTITUTION',
  description: 'Official registered name',
  updatedBy: 1,
  updatedAt: '2026-10-14T08:00:00Z',
  version: 1,
};

const academicSetting = {
  ...institutionSetting,
  key: 'academic.active_year',
  value: '2026-2027',
  category: 'ACADEMIC',
  description: 'Current operational academic year',
};

function response(data: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => data,
    text: async () => (typeof data === 'string' ? data : JSON.stringify(data)),
  } as Response;
}

function renderSettings() {
  return render(<ThemeProvider><AppProviders><AppRoutes /></AppProviders></ThemeProvider>);
}

function settingsFetch(user = settingsUser, initialSettings = [institutionSetting, academicSetting]) {
  let saved = initialSettings;
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/api/v1/me')) return response(user);
    if (url.startsWith('/api/v1/admin/settings/') && init?.method === 'PUT') {
      const key = decodeURIComponent(url.split('/').at(-1) ?? '');
      const body = JSON.parse(String(init.body)) as { value: string };
      const current = saved.find(setting => setting.key === key) ?? institutionSetting;
      const updated = { ...current, value: body.value, version: current.version + 1 };
      saved = saved.map(setting => setting.key === key ? updated : setting);
      return response(updated);
    }
    if (url.endsWith('/api/v1/admin/settings')) return response(saved);
    if (url.endsWith('/api/v1/auth/change-password') && init?.method === 'POST') return response(null, 204);
    return response({ message: 'Not found' }, 404);
  });
}

describe('Settings workspace', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'settings-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    window.history.replaceState({}, '', '/app/settings');
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('shows a loading state while settings are being retrieved', async () => {
    let resolveSettings!: (value: Response) => void;
    const pendingSettings = new Promise<Response>(resolve => { resolveSettings = resolve; });
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(settingsUser);
      if (url.endsWith('/api/v1/admin/settings')) return pendingSettings;
      return response([], 200);
    }));
    renderSettings();

    expect(await screen.findByText('Loading settings…')).toBeInTheDocument();
    resolveSettings(response([institutionSetting]));
    expect(await screen.findByText('Northfield School')).toBeInTheDocument();
  });

  it('shows a retryable error when settings cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(settingsUser);
      if (url.endsWith('/api/v1/admin/settings')) return response({ message: 'Settings service unavailable' }, 503);
      return response([], 200);
    }));
    renderSettings();

    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent('Settings service unavailable');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('loads existing settings and switches categories', async () => {
    vi.stubGlobal('fetch', settingsFetch());
    renderSettings();

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(await screen.findByText('Northfield School')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Academic' }));
    expect(await screen.findByText('2026-2027')).toBeInTheDocument();
  });

  it('saves changes and reports the version returned by the API', async () => {
    const fetchMock = settingsFetch();
    vi.stubGlobal('fetch', fetchMock);
    renderSettings();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    const value = screen.getByRole('textbox', { name: 'Setting value' });
    await userEvent.clear(value);
    await userEvent.type(value, 'Northfield Academy');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('institution.name saved as version 2.')).toBeInTheDocument();
    const update = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
    expect(update?.[1]?.body).toBe(JSON.stringify({ value: 'Northfield Academy' }));
  });

  it('keeps the edit open and displays an API error when save fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(settingsUser);
      if (url.startsWith('/api/v1/admin/settings/') && init?.method === 'PUT') return response({ message: 'Invalid setting value' }, 400);
      if (url.endsWith('/api/v1/admin/settings')) return response([institutionSetting]);
      return response([], 200);
    }));
    renderSettings();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.clear(screen.getByRole('textbox', { name: 'Setting value' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Setting value' }), 'Changed');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid setting value');
    expect(screen.getByRole('textbox', { name: 'Setting value' })).toHaveValue('Changed');
  });

  it('confirms before discarding a dirty edit when changing category', async () => {
    vi.stubGlobal('fetch', settingsFetch());
    vi.stubGlobal('confirm', vi.fn(() => true));
    renderSettings();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Setting value' }), ' Updated');
    await userEvent.click(screen.getByRole('tab', { name: 'Academic' }));

    expect(window.confirm).toHaveBeenCalledWith('Discard your unsaved setting change?');
    expect(await screen.findByText('2026-2027')).toBeInTheDocument();
  });

  it('shows settings as read-only without update permission', async () => {
    vi.stubGlobal('fetch', settingsFetch({ ...settingsUser, permissions: ['SETTINGS:READ'] }, [institutionSetting]));
    renderSettings();

    expect(await screen.findByText('Northfield School')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('allows access to change password even without settings read permission', async () => {
    vi.stubGlobal('fetch', settingsFetch({ ...settingsUser, permissions: [] }, []));
    renderSettings();

    expect(await screen.findByRole('heading', { name: 'Change Password' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Configuration' })).not.toBeInTheDocument();
  });

  it('submits a password change successfully', async () => {
    const fetchMock = settingsFetch();
    vi.stubGlobal('fetch', fetchMock);
    renderSettings();

    expect(await screen.findByRole('heading', { name: 'Change Password' })).toBeInTheDocument();

    await userEvent.type(screen.getByPlaceholderText('Enter current password'), 'OldPass@123');
    await userEvent.type(screen.getByPlaceholderText('Enter new password (min. 8 characters)'), 'NewSecurePass@123');
    await userEvent.type(screen.getByPlaceholderText('Re-enter new password'), 'NewSecurePass@123');

    await userEvent.click(screen.getByRole('button', { name: 'Update password' }));

    expect(await screen.findByText('Password updated successfully. You can use your new password next time you sign in.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/auth\/change-password$/),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          currentPassword: 'OldPass@123',
          newPassword: 'NewSecurePass@123',
        }),
      })
    );
  });

  it('validates password mismatch before calling api', async () => {
    const fetchMock = settingsFetch();
    vi.stubGlobal('fetch', fetchMock);
    renderSettings();

    expect(await screen.findByRole('heading', { name: 'Change Password' })).toBeInTheDocument();

    await userEvent.type(screen.getByPlaceholderText('Enter current password'), 'OldPass@123');
    await userEvent.type(screen.getByPlaceholderText('Enter new password (min. 8 characters)'), 'NewSecurePass@123');
    await userEvent.type(screen.getByPlaceholderText('Re-enter new password'), 'MismatchPass@123');

    await userEvent.click(screen.getByRole('button', { name: 'Update password' }));

    expect(await screen.findByText('New password and confirmation do not match.')).toBeInTheDocument();
  });
});
