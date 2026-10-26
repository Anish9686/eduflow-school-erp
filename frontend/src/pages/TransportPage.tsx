import { useState } from 'react';
import { PageHeader, EmptyState } from '../components';
import { useAuth } from '../auth/AuthProvider';
import {
  TransportOverviewView,
  TransportBusesView,
  TransportRoutesView,
  TransportStaffView,
  TransportAssignmentsView,
  TransportHistoryView,
  StudentTransportViewComponent,
  ParentTransportViewComponent,
} from './TransportComponents';
import './transport-pages.css';

export type TransportSubTab = 'overview' | 'buses' | 'routes' | 'staff' | 'assignments' | 'history';

export function TransportPage() {
  const { user } = useAuth();
  const roles = user?.roles ?? [];

  const isStudentOnly = roles.includes('STUDENT') && !roles.some(r =>
    ['SUPER_ADMIN', 'ADMIN', 'MANAGEMENT', 'STAFF_ADMIN', 'SUPPORT_AGENT'].includes(r)
  );

  const isParentOnly = roles.includes('PARENT_GUARDIAN') && !roles.some(r =>
    ['SUPER_ADMIN', 'ADMIN', 'MANAGEMENT', 'STAFF_ADMIN', 'SUPPORT_AGENT'].includes(r)
  );

  if (isStudentOnly) {
    return (
      <section className="workspace-page transport-page">
        <PageHeader
          eyebrow="STUDENT TRANSPORT"
          title="My Transport Assignment"
          description="View your allocated school bus, pickup/drop-off stops, and route details."
        />
        <StudentTransportViewComponent />
      </section>
    );
  }

  if (isParentOnly) {
    return (
      <section className="workspace-page transport-page">
        <PageHeader
          eyebrow="PARENT PORTAL"
          title="Children's Transport"
          description="View school bus allocations, route timings, and contact details for your linked children."
        />
        <ParentTransportViewComponent />
      </section>
    );
  }

  return (
    <section className="workspace-page transport-page">
      <PageHeader
        eyebrow="OPERATIONS & LOGISTICS"
        title="Transport Management"
        description="Oversee school buses, route configurations, driver & conductor allocations, and student seat assignments."
      />
      <TransportOperationalView />
    </section>
  );
}

export function TransportOperationalView() {
  const [subTab, setSubTab] = useState<TransportSubTab>('overview');

  const tabs: Array<{ id: TransportSubTab; label: import('react').ReactNode; permission?: string }> = [
    { id: 'overview', label: <><span className="ds-tab-icon" aria-hidden="true">◫</span>Overview</> },
    { id: 'buses', label: <><span className="ds-tab-icon" aria-hidden="true">🚌</span>Buses</> },
    { id: 'routes', label: <><span className="ds-tab-icon" aria-hidden="true">🗺</span>Routes</> },
    { id: 'staff', label: <><span className="ds-tab-icon" aria-hidden="true">👥</span>Drivers & Conductors</> },
    { id: 'assignments', label: <><span className="ds-tab-icon" aria-hidden="true">🪪</span>Student Assignments</> },
    { id: 'history', label: <><span className="ds-tab-icon" aria-hidden="true">⏱</span>Assignment History</> },
  ];

  const renderContent = () => {
    switch (subTab) {
      case 'overview':
        return <TransportOverviewView />;
      case 'buses':
        return <TransportBusesView />;
      case 'routes':
        return <TransportRoutesView />;
      case 'staff':
        return <TransportStaffView />;
      case 'assignments':
        return <TransportAssignmentsView />;
      case 'history':
        return <TransportHistoryView />;
      default:
        return <EmptyState title="Select a tab to view transport details" compact />;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <nav className="transport-subnav" aria-label="Transport Sections">
        {tabs.map(t => (
          <button
            key={t.id}
            type="button"
            className={subTab === t.id ? 'is-active' : ''}
            onClick={() => setSubTab(t.id)}
            aria-selected={subTab === t.id}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <div>{renderContent()}</div>
    </div>
  );
}
