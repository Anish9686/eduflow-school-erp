import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Button, FormField, Input } from '../components';
import { useAuth } from '../auth/AuthProvider';
import { CampusBackground } from '../layouts/CampusBackground';

export function LoginPage() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
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
          <FormField label="Password" required>
            <Input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={event => setPassword(event.target.value)}
            />
          </FormField>
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
