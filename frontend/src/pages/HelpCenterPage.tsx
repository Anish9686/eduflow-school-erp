import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState, Input, PageHeader } from '../components';
import { useAuth } from '../auth/AuthProvider';
import './help-center-page.css';

type HelpArticle = {
  id: string;
  category: string;
  title: string;
  summary: string;
  keywords: string[];
  steps: string[];
  destination?: { label: string; path: string; permissions?: string[]; roles?: string[] };
};

const articles: HelpArticle[] = [
  { id: 'getting-started', category: 'Getting Started', title: 'Navigate your workspace', summary: 'Use the workspace navigation to reach the areas available to your account.', keywords: ['sidebar', 'navigation', 'permissions', 'profile'], steps: ['Open a workspace area from the sidebar. The visible menu is based on your account access.', 'Use the breadcrumb at the top to see where you are and return to a parent area.', 'Your workspace uses a consistent dark appearance on this device.'], destination: { label: 'Dashboard', path: '/app/dashboard' } },
  { id: 'dashboard', category: 'Dashboard', title: 'Read your dashboard', summary: 'The dashboard shows the current role-scoped metrics and recent activity provided to your account.', keywords: ['overview', 'metrics', 'activity', 'loading', 'empty'], steps: ['Open Dashboard from the sidebar.', 'Review the metrics and recent activity returned for your account.', 'If no data is available, the dashboard shows an empty state. If loading fails, use Try again.'], destination: { label: 'Open Dashboard', path: '/app/dashboard' } },
  { id: 'students-admissions', category: 'Students and Admissions', title: 'Find students and review admissions', summary: 'Student records and admission applications are available to accounts with the corresponding student permissions.', keywords: ['student', 'search', 'record', 'admission', 'application'], steps: ['Open Students to search and review student records.', 'Open Admission to review applications and their current status.', 'Create or update actions appear only when your account has the required access.'], destination: { label: 'Open Students', path: '/app/students', permissions: ['STUDENT:READ'] } },
  { id: 'academics', category: 'Academics', title: 'Use the academic workspace', summary: 'The academic area contains sessions, classes, sections, subjects, timetable, homework, and syllabus progress screens.', keywords: ['session', 'class', 'section', 'subject', 'timetable', 'homework', 'syllabus'], steps: ['Expand Academics in the sidebar and choose the relevant area.', 'Use the filters shown on a page to narrow its records.', 'Create and update controls are shown only where your account has the required permissions.'], destination: { label: 'Open Sessions', path: '/app/academics/sessions', permissions: ['ACADEMIC:READ'] } },
  { id: 'attendance', category: 'Attendance', title: 'Access attendance', summary: 'Attendance provides student and staff attendance workflows according to your assigned permissions.', keywords: ['present', 'absent', 'staff', 'student', 'date', 'session', 'class', 'section'], steps: ['Open Attendance from the sidebar.', 'Choose the session, class, section, and date where those filters are available.', 'Use the attendance tabs and actions that your account is permitted to access.'], destination: { label: 'Open Attendance', path: '/app/attendance', permissions: ['ATTENDANCE:READ', 'ATTENDANCE:MARK', 'ATTENDANCE:MANAGE', 'STAFF_ATTENDANCE:READ', 'STAFF_ATTENDANCE:MARK', 'STAFF_ATTENDANCE:MANAGE'] } },
  { id: 'finance', category: 'Fees and Finance', title: 'Review fee and finance information', summary: 'Finance screens expose fee, payment, expense, and reporting actions based on your account permissions.', keywords: ['fees', 'payment', 'collection', 'refund', 'expense', 'outstanding', 'export'], steps: ['Open Finance from the sidebar.', 'Use the available Finance tabs to review or manage records within your access.', 'Exports and transactional actions are permission controlled.'], destination: { label: 'Open Finance', path: '/app/finance', permissions: ['FEE:READ', 'FEE:MANAGE', 'FEE:COLLECT', 'FEE:REFUND', 'FEE_DISCOUNT:REQUEST', 'FEE_DISCOUNT:APPROVE', 'EXPENSE:MANAGE', 'EXPENSE:APPROVE', 'FINANCE_REPORT:READ', 'FINANCE_REPORT:EXPORT'] } },
  { id: 'administration', category: 'Administration and Permissions', title: 'Manage users and access', summary: 'Administration contains user, role, permission, audit, and import/export tools for authorized administrators.', keywords: ['user', 'role', 'permission', 'audit', 'import', 'export', 'lock', 'deactivate'], steps: ['Open Administration from the sidebar if your account has administration access.', 'Use the tabs to work with the areas available to your permissions.', 'Changes and exports may require additional permissions; the server enforces access.'], destination: { label: 'Open Administration', path: '/app/administration', permissions: ['RBAC:ROLE_READ', 'USER:READ', 'USER:CREATE', 'USER:MANAGE', 'AUDIT:READ', 'SETTINGS:READ', 'USER:IMPORT'] } },
  { id: 'settings', category: 'Settings', title: 'Review available settings', summary: 'The Settings page lists configuration that is available to your account.', keywords: ['configuration', 'version', 'save', 'school', 'system'], steps: ['Open Settings from the sidebar if you have Settings read access.', 'Edit an available value and save it from the setting controls.', 'Saved changes include a version and audit record.'], destination: { label: 'Open Settings', path: '/app/settings', permissions: ['SETTINGS:READ'] } },
  { id: 'examinations', category: 'Results & Examinations', title: 'Manage examinations and publish results', summary: 'The results and examination management module covers scheduling, marks entry, review workflows, approval, publication, report cards, and analytics.', keywords: ['exam', 'marks', 'review', 'results', 'schedule', 'grading', 'report card', 'analytics'], steps: ['Open Academics > Results & Examinations from the sidebar.', 'Administrators can create examinations, configure schedules, and define grading schemes.', 'Teachers can enter marks and submit them for review.', 'Reviewers can approve or reject submissions before final calculation and publication.'], destination: { label: 'Open Examinations', path: '/app/academics/examinations', permissions: ['EXAM:READ', 'RESULT:READ'] } },
  { id: 'transport', category: 'Transport', title: 'Track and manage school transport', summary: 'Transport logistics includes bus fleets, routes, stops, driver/conductor assignments, and student bus tracking.', keywords: ['transport', 'bus', 'route', 'stops', 'driver', 'conductor', 'assignment'], steps: ['Operational staff can manage buses and routes from Operations > Transport.', 'Students and parents can view their assigned bus, driver contact, and route stops from Transport in the sidebar.', 'Staff assignments and seat capacity are enforced on all student assignments.'], destination: { label: 'Open Transport', path: '/app/operations?tab=transport', permissions: ['TRANSPORT:READ'] } },
  { id: 'common-problems', category: 'Common Problems', title: 'A page or request failed to load', summary: 'Use the page state and your administrator’s access configuration to diagnose common problems.', keywords: ['error', 'failed', 'loading', 'retry', 'forbidden', 'login', 'session'], steps: ['If a page shows a retry action, use it after checking your connection.', 'If you see an access-restricted message, ask your school administrator to review your permissions.', 'If your session cannot be verified, return to sign in and start a new session.'],
  },
];

export function HelpCenterPage() {
  const [query, setQuery] = useState('');
  const { hasPermission, user } = useAuth();
  const visibleArticles = useMemo(() => {
    const search = query.trim().toLocaleLowerCase();
    return articles.filter(article => !search || [article.title, article.category, article.summary, ...article.keywords, ...article.steps].join(' ').toLocaleLowerCase().includes(search));
  }, [query]);
  const isStudent = user?.roles.includes('STUDENT') && !user?.roles.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER');
  const isAccountant = user?.roles.includes('ACCOUNTANT') && !user?.roles.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER');
  const canOpen = (destination: HelpArticle['destination']) => {
    if (!destination) return false;
    if (isStudent && destination.path === '/app/students') return false;
    if (isAccountant && destination.path === '/app/attendance') return false;
    return Boolean(destination.roles?.some(role => user?.roles.includes(role))) ||
      !destination.permissions?.length || destination.permissions.some(hasPermission);
  };

  return <section className="workspace-page help-center-page">
    <PageHeader eyebrow="HELP & GUIDANCE" title="Help Center" description="Short guides to the areas and controls currently available in EduFlow." />
    <div className="help-center-search"><label htmlFor="help-search">Search help articles</label><Input id="help-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search by topic or keyword…" /></div>
    <div className="help-center-results" aria-live="polite">
      {visibleArticles.length ? visibleArticles.map(article => <details className="help-article" key={article.id}>
        <summary><span className="help-article-category">{article.category}</span><strong>{article.title}</strong><span className="help-article-summary">{article.summary}</span></summary>
        <div className="help-article-content"><ol>{article.steps.map(step => <li key={step}>{step}</li>)}</ol>{article.destination && canOpen(article.destination) && <Link className="help-article-link" to={article.destination.path}>{article.destination.label} <span aria-hidden="true">→</span></Link>}</div>
      </details>) : <div className="help-center-empty"><EmptyState title="No matching help articles" description="Try a different word or search for a module name." compact /></div>}
    </div>
    <aside className="help-contact-note"><strong>Support contact</strong><p>Support contact information has not been configured for this workspace.</p></aside>
  </section>;
}
