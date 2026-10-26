import { Link, Navigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton, StatCard } from '../components';
import { useAuth } from '../auth/AuthProvider';
import { getDashboard } from '../api/dashboard';
import { ApiRequestError } from '../api/client';

const metricIcons: Record<string, string> = {
  students: '♙', staff: '♧', attendance: '✓', outstandingFees: '₹', collections: '↗',
  pendingApprovals: '◷', paymentsToday: '•', discounts: '%', expenses: '₹',
  pendingApplicants: '＋', admissions: '▤', documents: '▧', applicants: '♙',
  assignedClasses: '▤', homework: '✎', syllabus: '◫',
  ownAttendance: '✓', ownFees: '₹', ownTimetable: '◷', ownHomework: '✎', ownAcademics: '▤',
  linkedChildren: '♙', linkedAttendance: '✓', linkedFees: '₹',
  staffAttendance: '♧', libraryCatalog: '▤', libraryIssues: '↗',
  inventoryItems: '⌘', lowStock: '!', assignedTickets: '✉',
};

export function greetingForLocalTime(date: Date = new Date()): string {
  const hour = date.getHours();
  if (hour >= 5 && hour < 12) return 'Good morning';
  if (hour >= 12 && hour < 17) return 'Good afternoon';
  if (hour >= 17 && hour < 22) return 'Good evening';
  return 'Good night';
}

export function extractFirstName(user: { fullName?: string | null; username?: string | null; roles?: string[] } | null | undefined): string {
  if (!user) return '';
  const fullName = user.fullName?.trim();
  if (fullName) {
    const first = fullName.split(/\s+/)[0];
    if (first) return first;
  }
  const username = user.username?.trim();
  if (username) {
    const withoutDomain = username.split('@')[0] ?? '';
    const token = withoutDomain.split(/[\._\-]+/)[0];
    if (token) {
      return token.charAt(0).toUpperCase() + token.slice(1).toLowerCase();
    }
    return username;
  }
  const firstRole = user.roles?.[0];
  if (firstRole) {
    const roleName = firstRole.split('_')[0] ?? '';
    if (roleName) {
      return roleName.charAt(0).toUpperCase() + roleName.slice(1).toLowerCase();
    }
  }
  return 'Member';
}

export function DashboardPage() {
  const { user } = useAuth();
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: ({ signal }) => getDashboard(signal), retry: false });
  const firstName = extractFirstName(user);
  return <div className="workspace-page dashboard-page">
    <div className="dashboard-hero"><PageHeader eyebrow="OVERVIEW" title={firstName ? `${greetingForLocalTime()}, ${firstName}!` : 'Welcome to EduFlow'} description="A role-scoped view of your school workspace." /></div>
    {dashboard.isPending ? <div className="dashboard-kpis" aria-label="Loading dashboard" role="status">{Array.from({ length: 4 }, (_, i) => <Card className="dashboard-kpi-skeleton" key={i}><Skeleton width="42%" height={13} /><Skeleton width="58%" height={30} /><Skeleton width="72%" height={12} /></Card>)}</div> : dashboard.isError ? <ErrorState title={dashboard.error instanceof ApiRequestError && dashboard.error.status === 403 ? 'Dashboard access denied' : 'Dashboard unavailable'} description={dashboard.error instanceof ApiRequestError && dashboard.error.status === 403 ? 'Your account is authenticated but does not have permission to view this dashboard.' : 'We couldn’t load your current summary.'} onRetry={() => void dashboard.refetch()} /> : <>
      {dashboard.data.metrics.length ? <div className="dashboard-kpis">{dashboard.data.metrics.map(metric => <StatCard key={metric.key} label={metric.label} value={metric.value} detail={metric.detail} icon={metricIcons[metric.key] ?? '◌'} />)}</div> : <Card><EmptyState title="No dashboard data available" description="Your account has no dashboard metrics yet, or the linked records have not been set up." compact /></Card>}
      {dashboard.data.recentActivity.length > 0 && <Card className="dashboard-activity"><div className="dashboard-section-heading"><div><p className="eyebrow">AUDIT TRAIL</p><h2>Recent activity</h2></div><span>{dashboard.data.recentActivity.length} latest events</span></div><div className="dashboard-activity-list">{dashboard.data.recentActivity.map((item, index) => <div className="dashboard-activity-row" key={`${item.createdAt}-${index}`}><span className="dashboard-activity-mark" aria-hidden="true">↗</span><div><strong>{item.action.replaceAll('_', ' ')}</strong><small>{item.entityType} · {item.actor}</small></div><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString()}</time></div>)}</div></Card>}
      {!dashboard.data.metrics.length && dashboard.data.recentActivity.length > 0 && <Alert tone="info" title="Role scoped overview">Only activity your role is allowed to view is shown here.</Alert>}
    </>}
  </div>;
}

export function ModulePlaceholder({ title }: { title: string }) {
  return <div className="workspace-page"><PageHeader eyebrow="WORKSPACE" title={title} description={`The ${title.toLowerCase()} workspace is ready for its implementation phase.`} actions={<Button variant="secondary" onClick={() => history.back()}>Go back</Button>} /><Card className="module-placeholder"><span className="welcome-icon">◫</span><h2>{title} workspace</h2><p>This application shell provides the navigation and access boundary. Module functionality will be added in its own implementation phase.</p><Link to="/app/dashboard" className="welcome-link">Return to dashboard <span aria-hidden="true">→</span></Link></Card></div>;
}

export function ForbiddenPage() {
  const location = useLocation();
  return <div className="workspace-page"><PageHeader eyebrow="ACCESS RESTRICTED" title="You don’t have access to this area" description="Your current role does not include the permission required for this page." /><Card className="module-placeholder"><Alert tone="warning" title="Permission required">Frontend navigation reflects your grants, but the server remains the authority for every protected request.</Alert><p className="blocked-route">Requested route: <code>{location.state?.path || location.pathname}</code></p><Link to="/app/dashboard" className="welcome-link">Return to dashboard <span aria-hidden="true">→</span></Link></Card></div>;
}

export function RequirePermission({ permission, children }: React.PropsWithChildren<{ permission: string }>) {
  const { hasPermission, status } = useAuth(); const location = useLocation();
  if (status === 'loading') return <div className="auth-loading" role="status">Checking your access…</div>;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!hasPermission(permission)) return <Navigate to="/app/forbidden" replace state={{ path: location.pathname }} />;
  return children;
}
