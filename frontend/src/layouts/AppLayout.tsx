import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { administrationApi } from '../api/administration';
import { CampusBackground } from './CampusBackground';

type Entry = { label: string; path: string; icon?: string; permission?: string | string[] };
const academics: Entry[] = [
  { label: 'Sessions', path: '/app/academics/sessions', icon: '◷', permission: 'ACADEMIC:READ' },
  { label: 'Classes', path: '/app/academics/classes', icon: '🏛', permission: 'ACADEMIC:READ' },
  { label: 'Sections', path: '/app/academics/sections', icon: '☷', permission: 'ACADEMIC:READ' },
  { label: 'Subjects', path: '/app/academics/subjects', icon: '📖', permission: 'ACADEMIC:READ' },
  { label: 'Timetable', path: '/app/academics/timetable', icon: '📅', permission: ['ACADEMIC:READ','TIMETABLE:READ'] },
  { label: 'Homework', path: '/app/academics/homework', icon: '✍', permission: ['HOMEWORK:READ','HOMEWORK:CREATE','HOMEWORK:MANAGE'] },
  { label: 'Syllabus', path: '/app/academics/syllabus', icon: '📄', permission: ['SYLLABUS:READ','SYLLABUS:UPDATE','SYLLABUS:MANAGE'] },
  { label: 'Results & Examinations', path: '/app/academics/examinations', icon: '📋', permission: ['EXAM:READ','RESULT:READ'] },
];
const topPrimary: Entry[] = [
  { label: 'Dashboard', path: '/app/dashboard', icon: '◫' },
  { label: 'Students', path: '/app/students', icon: '♙', permission: 'STUDENT:READ' },
  { label: 'Admission', path: '/app/admissions', icon: '＋', permission: 'ADMISSION:READ' },
];
const bottomPrimary: Entry[] = [
  { label: 'Attendance', path: '/app/attendance', icon: '◷', permission: ['ATTENDANCE:READ', 'ATTENDANCE:MARK', 'ATTENDANCE:MANAGE', 'STAFF_ATTENDANCE:READ', 'STAFF_ATTENDANCE:MARK', 'STAFF_ATTENDANCE:MANAGE'] },
  { label: 'Finance', path: '/app/finance', icon: '◇', permission: ['FEE:MANAGE','FEE:COLLECT','FEE:REFUND','FEE_DISCOUNT:REQUEST','FEE_DISCOUNT:APPROVE','EXPENSE:MANAGE','EXPENSE:APPROVE','FINANCE_REPORT:READ','FINANCE_REPORT:EXPORT'] },
  { label: 'Operations', path: '/app/operations', icon: '⌘', permission: ['INVENTORY:READ','LIBRARY:READ','HOSTEL:READ','SUPPORT:READ','TRANSPORT:READ'] },
  { label: 'Administration', path: '/app/administration', icon: '⚙', permission: ['RBAC:ROLE_READ', 'USER:READ', 'AUDIT:READ', 'USER:MANAGE', 'USER:IMPORT'] },
  { label: 'Reports', path: '/app/reports', icon: '▥', permission: ['STUDENT:READ','ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE','FINANCE_REPORT:READ'] },
];
const primary: Entry[] = [...topPrimary, ...bottomPrimary];

function allowed(item: Entry, hasPermission: (permission: string) => boolean) {
  return !item.permission ||
    (Array.isArray(item.permission) ? item.permission.some(hasPermission) : hasPermission(item.permission));
}

export function Sidebar({ collapsed, onToggle, mobileOpen, onNavigate }: { collapsed: boolean; onToggle: () => void; mobileOpen: boolean; onNavigate: () => void }) {
  const { hasPermission, user } = useAuth(); const location = useLocation();
  const isAcademicRoute = location.pathname.startsWith('/app/academics');
  const [academicsOpen, setAcademicsOpen] = useState(isAcademicRoute);

  useEffect(() => {
    setAcademicsOpen(isAcademicRoute);
  }, [isAcademicRoute]);

  const isStudentOrParent = Boolean(user?.roles?.some(r => r === 'STUDENT' || r === 'PARENT_GUARDIAN'));
  const isStudent = Boolean(user?.roles?.includes('STUDENT') && !user?.roles?.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER'));
  const isParent = Boolean(user?.roles?.includes('PARENT_GUARDIAN') && !user?.roles?.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER'));
  const isAccountant = Boolean(user?.roles?.includes('ACCOUNTANT') && !user?.roles?.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER'));
  // Keep navigation consistent with the same grants used by the route guards.
  const academicLinks = isAccountant ? [] : academics.filter(item => {
    if (isStudentOrParent && ['Sessions', 'Classes', 'Sections', 'Subjects'].includes(item.label)) {
      return false;
    }
    return allowed(item, hasPermission);
  });
  const topLinks = topPrimary.filter(item => {
    if (isStudent && item.label === 'Students') return false;
    return allowed(item, hasPermission);
  }).map(item => {
    if (isParent && item.label === 'Students') {
      return { ...item, label: 'My Children' };
    }
    return item;
  });
  const bottomLinks = [
    ...bottomPrimary.filter(item => {
      if (isStudentOrParent && (item.label === 'Finance' || item.label === 'Operations')) return false;
      if (isAccountant && item.label === 'Attendance') return false;
      return allowed(item, hasPermission);
    }),
    ...(isStudentOrParent && allowed({ label: 'Transport', path: '/app/transport', icon: '🚌', permission: 'TRANSPORT:READ' }, hasPermission)
      ? [{ label: 'Transport', path: '/app/transport', icon: '🚌' }]
      : []),
  ];
  return <>
    {mobileOpen && <button className="shell-scrim" aria-label="Close navigation" onClick={onNavigate} />}
    <aside className={`app-sidebar ${collapsed ? 'is-collapsed' : ''} ${mobileOpen ? 'is-mobile-open' : ''}`} aria-label="Main navigation">
      <div className="sidebar-brand-row"><Link to="/app/dashboard" className="shell-brand" onClick={onNavigate}><span className="brand-mark">E</span><span className="sidebar-brand-name">EduFlow</span></Link><button className="sidebar-collapse" onClick={onToggle} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>{collapsed ? '»' : '«'}</button></div>
      <div className="sidebar-caption">WORKSPACE</div>
      <nav className="sidebar-nav" aria-label="Workspace">
        {topLinks.map(item => <NavItem key={item.path} item={item} onNavigate={onNavigate} />)}
        {academicLinks.length > 0 && <div className="nav-group">
          <button type="button" className={`nav-item nav-group-trigger ${isAcademicRoute ? 'is-active' : ''}`} title={collapsed ? 'Academics' : undefined} aria-expanded={academicsOpen} onClick={() => { if (collapsed) onToggle(); setAcademicsOpen(value => !value); }}><span className="nav-icon">▤</span><span className="nav-label">Academics</span><span className="nav-chevron">{academicsOpen ? '⌄' : '›'}</span></button>
          {academicsOpen && <div className="nav-submenu">{academicLinks.map(item => <NavItem key={item.path} item={item} onNavigate={onNavigate} nested />)}</div>}
        </div>}
        {bottomLinks.map(item => <NavItem key={item.path} item={item} onNavigate={onNavigate} />)}
      </nav>
      <div className="sidebar-bottom"><NavItem item={{ label: 'Settings', path: '/app/settings', icon: '⚙' }} onNavigate={onNavigate} /><Link to="/app/help" className="sidebar-help" onClick={onNavigate} aria-label="Need a hand? Visit the Help Center"><span className="help-avatar" aria-hidden="true">?</span><span className="help-copy"><strong>Need a hand?</strong><small>Visit help center</small></span><span aria-hidden="true">↗</span></Link></div>
      <div className="sidebar-identity" aria-label="EduFlow school workspace"><span className="brand-mark" aria-hidden="true">E</span><span className="sidebar-brand-name">EduFlow <small>School workspace</small></span></div>
    </aside>
  </>;
}

function NavItem({ item, onNavigate, nested = false }: { item: Entry; onNavigate: () => void; nested?: boolean }) {
  return <NavLink to={item.path} onClick={onNavigate} title={item.label} className={({ isActive }) => `nav-item ${nested ? 'nav-item-nested' : ''} ${isActive ? 'is-active' : ''}`}><span className="nav-icon" aria-hidden="true">{item.icon || <i />}</span><span className="nav-label">{item.label}</span></NavLink>;
}

function Breadcrumb() {
  const { pathname } = useLocation();
  const { user } = useAuth();
  const isParent = Boolean(user?.roles?.includes('PARENT_GUARDIAN') && !user?.roles?.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER'));
  const labels: Record<string, string> = { dashboard: 'Dashboard', students: isParent ? 'My Children' : 'Students', admissions: 'Admission', academics: 'Academics', sessions: 'Sessions', classes: 'Classes', sections: 'Sections', subjects: 'Subjects', timetable: 'Timetable', homework: 'Homework', syllabus: 'Syllabus', examinations: 'Results & Examinations', exams: 'Examinations', attendance: 'Attendance', finance: 'Finance', operations: 'Operations', transport: 'Transport', administration: 'Administration', reports: 'Reports', settings: 'Settings', help: 'Help Center' };
  const parts = pathname.split('/').filter(Boolean).slice(1);
  return <nav className="shell-breadcrumb" aria-label="Breadcrumb"><Link to="/app/dashboard">Workspace</Link>{parts.map((part, index) => <span key={`${part}-${index}`} className="breadcrumb-piece"><span aria-hidden="true">/</span>{index === parts.length - 1 ? <span aria-current="page">{labels[part] ?? part}</span> : <Link to={`/app/${parts.slice(0, index + 1).join('/')}`}>{labels[part] ?? part}</Link>}</span>)}</nav>;
}

function NotificationArea() {
  const { hasPermission } = useAuth();
  const [open, setOpen] = useState(false);
  const settingsQuery = useQuery({
    queryKey: ['system-settings'],
    queryFn: administrationApi.listSettings,
    enabled: hasPermission('SETTINGS:READ'),
    staleTime: 60000,
  });

  const banner = settingsQuery.data?.find(s => s.key === 'system.notification_banner');
  const broadcastText = banner?.value?.trim() || '';
  const canEdit = hasPermission('SETTINGS:UPDATE');
  const hasBroadcast = Boolean(broadcastText);

  return <div className="notification-wrap">
    <button
      className="icon-button notification-button"
      aria-label={hasBroadcast ? "Notifications, 1 announcement" : "Notifications, up to date"}
      aria-expanded={open}
      onClick={() => setOpen(value => !value)}
    >
      <span aria-hidden="true">♧</span>
      {hasBroadcast && <i />}
    </button>
    {open && <div className="notification-popover" role="dialog" aria-label="Notifications">
      <div className="popover-heading">
        <strong>Notifications</strong>
        <span className="unread-count">{hasBroadcast ? '1 announcement' : 'All caught up'}</span>
      </div>
      {hasBroadcast && (
        <div className="notification-item notification-broadcast-item">
          <span className="notification-dot" />
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '2px' }}>
              <strong>School Announcement</strong>
              <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: 'color-mix(in srgb, var(--accent) 15%, transparent)', color: 'var(--accent)', fontWeight: 650 }}>BROADCAST</span>
            </div>
            <p>{broadcastText}</p>
            <small>{banner?.updatedAt ? `Updated ${new Date(banner.updatedAt).toLocaleDateString()}` : 'Active notice'}</small>
          </div>
        </div>
      )}
      <div className="notification-item">
        <span className="notification-dot muted-dot" />
        <div>
          <strong>Welcome to your workspace</strong>
          <p>Your school workspace is ready to explore.</p>
          <small>System</small>
        </div>
      </div>
      {canEdit && (
        <Link to="/app/settings" className="popover-footer" onClick={() => setOpen(false)}>
          Edit broadcast notice in Settings ↗
        </Link>
      )}
    </div>}
  </div>;
}

function UserMenu() {
  const { user, logout } = useAuth(); const [open, setOpen] = useState(false); const navigate = useNavigate();
  async function handleLogout() { await logout(); navigate('/login', { replace: true }); }
  const initial = (user?.fullName?.trim() || user?.username || 'U').slice(0, 1).toUpperCase();
  return <div className="user-menu-wrap"><button className="user-menu-trigger" aria-expanded={open} onClick={() => setOpen(value => !value)}><span className="user-avatar">{initial}</span><span className="user-meta"><strong>{user?.fullName || user?.username}</strong><small>{user?.roles[0]?.replaceAll('_', ' ') || 'Member'}</small></span><span className="user-chevron">⌄</span></button>{open && <div className="user-menu" role="menu"><div className="user-menu-summary"><strong>{user?.fullName || user?.username}</strong><small>{user?.username}</small></div><button role="menuitem" onClick={handleLogout}>Sign out <span aria-hidden="true">↗</span></button></div>}</div>;
}

function WorkspaceSearch() {
  const { hasPermission, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const searchRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const isStudentOrParent = user?.roles.some(r => r === 'STUDENT' || r === 'PARENT_GUARDIAN');
  const isStudent = user?.roles.includes('STUDENT') && !user?.roles.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER');
  const isAccountant = user?.roles.includes('ACCOUNTANT') && !user?.roles.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER');
  const availablePages = [
    ...primary.filter(item => {
      if (isStudent && item.label === 'Students') return false;
      if (isStudentOrParent && (item.label === 'Finance' || item.label === 'Operations')) return false;
      if (isAccountant && item.label === 'Attendance') return false;
      return true;
    }),
    ...(isStudentOrParent ? [{ label: 'Transport', path: '/app/transport', icon: '🚌', permission: 'TRANSPORT:READ' }] : []),
    ...(isAccountant ? [] : academics.filter(item => !(isStudentOrParent && ['Sessions', 'Classes', 'Sections', 'Subjects'].includes(item.label))).map(item => ({ ...item, group: 'Academics' }))),
    { label: 'Settings', path: '/app/settings', icon: '⚙' },
    { label: 'Help Center', path: '/app/help', icon: '?' },
  ].filter(item => allowed(item, hasPermission));
  const terms = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  const results = (terms.length
    ? availablePages.filter(item => {
      const searchableText = `${item.label} ${'group' in item ? item.group : ''}`.toLocaleLowerCase();
      return terms.every(term => searchableText.includes(term));
    })
    : availablePages
  ).slice(0, 8);

  useEffect(() => {
    setOpen(false);
    setQuery('');
  }, [location.pathname]);

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (!searchRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLocaleLowerCase() === 'k') {
        event.preventDefault();
        setOpen(true);
        window.requestAnimationFrame(() => inputRef.current?.focus());
      }
    };
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleShortcut);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleShortcut);
    };
  }, []);

  function selectResult(index: number) {
    const result = results[index];
    if (!result) return;
    setOpen(false);
    setQuery('');
    inputRef.current?.blur();
    navigate(result.path);
  }

  function openSearch() {
    setOpen(true);
    window.requestAnimationFrame(() => inputRef.current?.focus());
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      setOpen(false);
      inputRef.current?.blur();
      return;
    }
    if (!open || results.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex(index => (index + 1) % results.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex(index => (index - 1 + results.length) % results.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      selectResult(activeIndex);
    }
  }

  return <div className={`quick-search ${open ? 'is-open' : ''}`} ref={searchRef} role="search">
    <button className="quick-search-icon" type="button" aria-label="Open workspace search" onClick={openSearch}>⌕</button>
    <input
      ref={inputRef}
      id="shell-search"
      type="search"
      placeholder="Search pages and modules..."
      aria-label="Search pages and modules"
      aria-autocomplete="list"
      aria-expanded={open}
      aria-controls="workspace-search-results"
      aria-activedescendant={open && results.length ? `workspace-search-result-${activeIndex}` : undefined}
      value={query}
      onFocus={() => setOpen(true)}
      onChange={event => { setQuery(event.target.value); setActiveIndex(0); setOpen(true); }}
      onKeyDown={handleKeyDown}
    />
    <kbd>⌘ K</kbd>
    {open && <div className="quick-search-results" id="workspace-search-results" role="listbox" aria-label="Available workspace pages">
      {results.length ? results.map((item, index) => <button
        key={item.path}
        id={`workspace-search-result-${index}`}
        type="button"
        role="option"
        aria-selected={index === activeIndex}
        className="quick-search-result"
        onMouseEnter={() => setActiveIndex(index)}
        onClick={() => selectResult(index)}
      >
        <span className="quick-search-result-icon" aria-hidden="true">{item.icon || '›'}</span>
        <span>{item.label}</span>
        {'group' in item && typeof item.group === 'string' && <small>{item.group}</small>}
      </button>) : <p className="quick-search-empty">No matching pages. Try a module name.</p>}
    </div>}
  </div>;
}

export function AppLayout() {
  const { hasPermission } = useAuth();
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('erp-sidebar-collapsed') === 'true'; } catch { return false; } });
  const [mobileOpen, setMobileOpen] = useState(false);
  const [dismissedBanner, setDismissedBanner] = useState<string | null>(() => {
    try {
      return sessionStorage.getItem('erp-broadcast-dismissed');
    } catch {
      return null;
    }
  });

  const settingsQuery = useQuery({
    queryKey: ['system-settings'],
    queryFn: administrationApi.listSettings,
    enabled: hasPermission('SETTINGS:READ'),
    staleTime: 60000,
  });

  const banner = settingsQuery.data?.find(s => s.key === 'system.notification_banner');
  const broadcastText = banner?.value?.trim() || '';
  const canEdit = hasPermission('SETTINGS:UPDATE');
  const showBanner = Boolean(broadcastText) && dismissedBanner !== broadcastText;

  const handleDismissBanner = () => {
    setDismissedBanner(broadcastText);
    try {
      sessionStorage.setItem('erp-broadcast-dismissed', broadcastText);
    } catch {
      /* Optional preference */
    }
  };

  useEffect(() => { try { localStorage.setItem('erp-sidebar-collapsed', String(collapsed)); } catch { /* Optional preference. */ } }, [collapsed]);
  useEffect(() => { setMobileOpen(false); }, []);

  return <div className={`shell-frame ${collapsed ? 'sidebar-collapsed' : ''}`}>
    <CampusBackground />
    <Sidebar collapsed={collapsed} onToggle={() => setCollapsed(value => !value)} mobileOpen={mobileOpen} onNavigate={() => setMobileOpen(false)} />
    <div className="shell-main">
      <header className="shell-topbar">
        <div className="topbar-leading">
          <button className="mobile-menu-button icon-button" aria-label="Open navigation" aria-expanded={mobileOpen} onClick={() => setMobileOpen(true)}>☰</button>
          <Breadcrumb />
        </div>
        <div className="topbar-actions">
          <WorkspaceSearch />
          <div className="topbar-divider" />
          <NotificationArea />
          <div className="topbar-divider" />
          <UserMenu />
        </div>
      </header>
      {showBanner && (
        <aside className="workspace-broadcast-banner" role="region" aria-label="School Announcement">
          <div className="broadcast-banner-content">
            <span className="broadcast-badge" aria-hidden="true">Notice</span>
            <span className="broadcast-text">{broadcastText}</span>
          </div>
          <div className="broadcast-banner-actions">
            {canEdit && (
              <Link to="/app/settings" className="broadcast-edit-link">
                Edit notice ↗
              </Link>
            )}
            <button
              type="button"
              className="broadcast-dismiss-btn"
              aria-label="Dismiss announcement"
              onClick={handleDismissBanner}
            >
              ×
            </button>
          </div>
        </aside>
      )}
      <main className="shell-content"><Outlet /></main>
      <footer className="shell-footer">
        <span>EduFlow · School workspace</span>
        <Link to="/app/help" aria-label="Open Help Center">Help center <span aria-hidden="true">↗</span></Link>
      </footer>
    </div>
  </div>;
}
