import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Button, FormField, Input } from '../components';
import { useAuth } from '../auth/AuthProvider';
import { CampusBackground } from '../layouts/CampusBackground';

function EyeIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
      <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
      <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
      <line x1="2" y1="2" x2="22" y2="22" />
    </svg>
  );
}

export function LoginPage() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  if (status === 'loading') return <div className="auth-loading" role="status">Checking your session…</div>;
  if (status === 'authenticated') return <Navigate to="/app/dashboard" replace />;

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setPending(true);
    try {
      await login(username.trim(), password);
      const destination = (location.state as { from?: string } | null)?.from ?? '/app/dashboard';
      navigate(destination, { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Sign in failed. Please try again.');
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="auth-screen">
      <CampusBackground fullScreen />
      <section className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark">E</span>
          <strong>EduFlow</strong>
        </div>
        <p className="eyebrow">SCHOOL WORKSPACE</p>
        <h1>Welcome back</h1>
        <p className="auth-description">Sign in to continue to your workspace.</p>
        <form className="auth-form" onSubmit={submit}>
          <FormField label="Username or Email" required>
            <Input
              autoComplete="username"
              value={username}
              onChange={event => setUsername(event.target.value)}
              placeholder="e.g. admin or admin@eduflow.edu"
            />
          </FormField>
          <div className="ds-field">
            <label className="ds-label" htmlFor="password-input">
              Password<span className="ds-required" aria-hidden="true"> *</span>
            </label>
            <div className="password-input-wrap">
              <Input
                id="password-input"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                onChange={event => setPassword(event.target.value)}
                className="password-input-field"
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowPassword(prev => !prev)}
                aria-label={showPassword ? 'Hide' : 'Show'}
                title={showPassword ? 'Hide' : 'Show'}
              >
                {showPassword ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
          </div>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <Button type="submit" disabled={pending || !username.trim() || !password}>
            {pending ? 'Signing in…' : 'Sign in'}
            <span aria-hidden="true">→</span>
          </Button>
        </form>

        <p className="auth-footnote">Your access is managed by your school administrator.</p>
      </section>
    </main>
  );
}
