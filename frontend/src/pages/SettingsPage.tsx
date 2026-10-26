import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, ErrorState, FormField, Input, PageHeader } from '../components';
import { administrationApi } from '../api/administration';
import type { SystemSetting } from '../api/administration';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthProvider';
import './settings-page.css';

const SETTINGS_QUERY_KEY = ['system-settings'];

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function categoryLabel(category: string) {
  return category.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, character => character.toUpperCase());
}

export function SettingsPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [activeCategory, setActiveCategory] = useState('');
  const [editing, setEditing] = useState<SystemSetting | null>(null);
  const [draft, setDraft] = useState('');
  const [saveError, setSaveError] = useState('');
  const [savedMessage, setSavedMessage] = useState('');

  // Password change state for all users
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');

  const changePasswordMutation = useMutation({
    mutationFn: authApi.changePassword,
    onSuccess: () => {
      setPasswordSuccess('Password updated successfully. You can use your new password next time you sign in.');
      setPasswordError('');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    },
    onError: error => {
      setPasswordError(errorMessage(error, 'Could not change password.'));
      setPasswordSuccess('');
    },
  });

  function handlePasswordChange(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');
    if (newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters long.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('New password and confirmation do not match.');
      return;
    }
    changePasswordMutation.mutate({ currentPassword, newPassword });
  }

  const settingsQuery = useQuery({
    queryKey: SETTINGS_QUERY_KEY,
    queryFn: administrationApi.listSettings,
    enabled: hasPermission('SETTINGS:READ'),
  });
  const settings = settingsQuery.data ?? [];
  const categories = useMemo(() => [...new Set(settings.map(setting => setting.category))], [settings]);
  const selectedCategory = categories.includes(activeCategory) ? activeCategory : categories[0] ?? '';
  const visibleSettings = settings.filter(setting => setting.category === selectedCategory);
  const isDirty = Boolean(editing && draft !== editing.value);

  const updateMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => administrationApi.updateSetting(key, value),
    onSuccess: updated => {
      void queryClient.invalidateQueries({ queryKey: SETTINGS_QUERY_KEY });
      setEditing(null);
      setDraft('');
      setSaveError('');
      setSavedMessage(`${updated.key} saved as version ${updated.version}.`);
    },
    onError: error => setSaveError(errorMessage(error, 'Could not save this setting.')),
  });

  useEffect(() => {
    if (!isDirty) return undefined;
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [isDirty]);

  function confirmDiscard() {
    return !isDirty || window.confirm('Discard your unsaved setting change?');
  }

  function changeCategory(category: string) {
    if (!confirmDiscard()) return;
    setEditing(null);
    setDraft('');
    setSaveError('');
    setActiveCategory(category);
  }

  function cancelEdit() {
    if (!confirmDiscard()) return;
    setEditing(null);
    setDraft('');
    setSaveError('');
  }

  function startEdit(setting: SystemSetting) {
    setSavedMessage('');
    setSaveError('');
    setEditing(setting);
    setDraft(setting.value);
  }

  function saveEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || !isDirty || updateMutation.isPending) return;
    setSaveError('');
    setSavedMessage('');
    updateMutation.mutate({ key: editing.key, value: draft });
  }

  return <div className="settings-page">
    <PageHeader
      eyebrow="SETTINGS"
      title="Settings"
      description="Manage your account security and review available workspace configuration."
    />

    <section className="settings-password-card" aria-label="Security and Password">
      <div className="settings-password-heading">
        <h2>Change Password</h2>
        <p>Update your account password. If you received initial credentials from an administrator, set your own secure password here.</p>
      </div>
      {passwordSuccess && <Alert tone="success" title="Password changed">{passwordSuccess}</Alert>}
      {passwordError && <Alert tone="danger" title="Unable to change password">{passwordError}</Alert>}
      <form className="settings-password-form" onSubmit={handlePasswordChange}>
        <FormField label="Current password" required>
          <Input
            type="password"
            autoComplete="current-password"
            required
            value={currentPassword}
            onChange={e => setCurrentPassword(e.target.value)}
            placeholder="Enter current password"
          />
        </FormField>
        <FormField label="New password" required hint="Must be at least 8 characters">
          <Input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={newPassword}
            onChange={e => setNewPassword(e.target.value)}
            placeholder="Enter new password (min. 8 characters)"
          />
        </FormField>
        <FormField label="Confirm new password" required>
          <Input
            type="password"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            placeholder="Re-enter new password"
          />
        </FormField>
        <div className="settings-password-actions">
          <Button type="submit" variant="primary" disabled={changePasswordMutation.isPending}>
            {changePasswordMutation.isPending ? 'Updating password…' : 'Update password'}
          </Button>
        </div>
      </form>
    </section>

    {hasPermission('SETTINGS:READ') && <>
      {savedMessage && <Alert tone="success" title="Settings saved">{savedMessage}</Alert>}
      {saveError && <Alert tone="danger" title="Unable to save setting">{saveError}</Alert>}

      {settingsQuery.isPending ? (
        <div className="settings-loading" role="status">Loading settings…</div>
      ) : settingsQuery.isError ? (
        <ErrorState title="Settings unavailable" description={errorMessage(settingsQuery.error, 'Could not load settings.')} onRetry={() => void settingsQuery.refetch()} />
      ) : settings.length === 0 ? (
        <div className="settings-empty">No settings are currently available to your account.</div>
      ) : <>
        <section className="settings-category-section" aria-label="Settings categories">
          <div className="settings-category-heading">
            <h2>Configuration</h2>
            <p>Choose a category to view its current values.</p>
          </div>
          <div className="settings-category-tabs" role="tablist" aria-label="Settings categories">
            {categories.map(category => <button
              key={category}
              type="button"
              role="tab"
              id={`settings-tab-${category}`}
              aria-selected={selectedCategory === category}
              aria-controls="settings-category-panel"
              onClick={() => changeCategory(category)}
            >{categoryLabel(category)}</button>)}
          </div>
        </section>

        <section className="settings-list" id="settings-category-panel" role="tabpanel" aria-labelledby={`settings-tab-${selectedCategory}`}>
          <div className="settings-list-heading">
            <div><h2>{categoryLabel(selectedCategory)}</h2><p>{visibleSettings.length} {visibleSettings.length === 1 ? 'setting' : 'settings'}</p></div>
          </div>
          {visibleSettings.map((setting, index) => {
            const isEditing = editing?.key === setting.key;
            const inputId = `setting-value-${index}`;
            return <article className="settings-item" key={setting.key}>
              <div className="settings-item-heading">
                <div><h3>{setting.key}</h3><p>{setting.description || 'Configuration value'}</p></div>
                <span className="settings-version">Version {setting.version}</span>
              </div>
              {isEditing ? <form className="settings-edit-form" onSubmit={saveEdit}>
                <label htmlFor={inputId}>Setting value</label>
                <input id={inputId} value={draft} onChange={event => setDraft(event.target.value)} autoFocus disabled={updateMutation.isPending} />
                <div className="settings-edit-actions">
                  <Button type="submit" variant="primary" disabled={!isDirty || updateMutation.isPending}>{updateMutation.isPending ? 'Saving…' : 'Save changes'}</Button>
                  <Button type="button" variant="secondary" disabled={updateMutation.isPending} onClick={cancelEdit}>Cancel</Button>
                </div>
              </form> : <div className="settings-item-value">
                <strong>{setting.value}</strong>
                {hasPermission('SETTINGS:UPDATE') && <Button type="button" size="sm" variant="secondary" onClick={() => startEdit(setting)}>Edit</Button>}
              </div>}
              <p className="settings-item-meta">Updated {new Date(setting.updatedAt).toLocaleString()}</p>
            </article>;
          })}
        </section>
      </>}
    </>}
  </div>;
}
