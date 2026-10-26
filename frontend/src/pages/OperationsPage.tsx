import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  Alert,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  Modal,
  PageHeader,
  Select,
  Tabs,
  Textarea,
} from '../components';
import { operationsApi, type InventoryItem, type LibraryItem, type HostelRoom, type SupportTicket } from '../api/operations';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { TransportOperationalView } from './TransportPage';
import './operations-pages.css';

type OperationsTab = 'inventory' | 'library' | 'hostel' | 'support' | 'transport';
type FormState = Record<string, string>;
const tabs: Array<{ id: OperationsTab; label: ReactNode }> = [
  { id: 'inventory', label: <><span className="ds-tab-icon" aria-hidden="true">▦</span>Inventory</> },
  { id: 'library', label: <><span className="ds-tab-icon" aria-hidden="true">▤</span>Library</> },
  { id: 'hostel', label: <><span className="ds-tab-icon" aria-hidden="true">▣</span>Hostel</> },
  { id: 'support', label: <><span className="ds-tab-icon" aria-hidden="true">◉</span>Support</> },
  { id: 'transport', label: <><span className="ds-tab-icon" aria-hidden="true">🚌</span>Transport</> },
];

export function OperationsPage() {
  const { hasPermission } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab') as OperationsTab | null;
  const [tab, setTab] = useState<OperationsTab>(requestedTab && ['inventory', 'library', 'hostel', 'support', 'transport'].includes(requestedTab) ? requestedTab : 'inventory');
  const [modal, setModal] = useState<'inventory' | 'library' | 'hostel' | 'support' | null>(null);
  const [mutationError, setMutationError] = useState('');
  const queryClient = useQueryClient();
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['operations'] });
  const can = (permission: string) => hasPermission(permission);
  const visibleTabs = tabs.filter(item => {
    const perm = item.id === 'inventory' ? 'INVENTORY:READ'
      : item.id === 'library' ? 'LIBRARY:READ'
      : item.id === 'hostel' ? 'HOSTEL:READ'
      : item.id === 'support' ? 'SUPPORT:READ'
      : 'TRANSPORT:READ';
    return can(perm);
  });
  const selectedTab = visibleTabs.some(item => item.id === tab) ? tab : visibleTabs[0]?.id;

  useEffect(() => {
    if (selectedTab && selectedTab !== tab) setTab(selectedTab);
  }, [selectedTab, tab]);

  const inventory = useQuery({ queryKey: ['operations', 'inventory'], queryFn: operationsApi.inventory, enabled: can('INVENTORY:READ') });
  const library = useQuery({ queryKey: ['operations', 'library'], queryFn: operationsApi.library, enabled: can('LIBRARY:READ') });
  const rooms = useQuery({ queryKey: ['operations', 'hostel'], queryFn: operationsApi.rooms, enabled: can('HOSTEL:READ') });
  const tickets = useQuery({ queryKey: ['operations', 'support'], queryFn: operationsApi.tickets, enabled: can('SUPPORT:READ') });

  const createInventory = useMutation({ mutationFn: operationsApi.createInventoryItem, onSuccess: () => { invalidate(); setModal(null); }, onError: error => setMutationError(error.message) });
  const createLibrary = useMutation({ mutationFn: operationsApi.createLibraryItem, onSuccess: () => { invalidate(); setModal(null); }, onError: error => setMutationError(error.message) });
  const createRoom = useMutation({ mutationFn: operationsApi.createRoom, onSuccess: () => { invalidate(); setModal(null); }, onError: error => setMutationError(error.message) });
  const createTicket = useMutation({ mutationFn: operationsApi.createTicket, onSuccess: () => { invalidate(); setModal(null); }, onError: error => setMutationError(error.message) });

  const renderTab = () => {
    const openCreate = (next: NonNullable<typeof modal>) => { setMutationError(''); setModal(next); };
    if (selectedTab === 'inventory') return <InventoryView items={inventory.data ?? []} loading={inventory.isLoading} error={inventory.error} onRetry={() => void inventory.refetch()} onCreate={can('INVENTORY:MANAGE') ? () => openCreate('inventory') : undefined} />;
    if (selectedTab === 'library') return <LibraryView items={library.data ?? []} loading={library.isLoading} error={library.error} onRetry={() => void library.refetch()} onCreate={can('LIBRARY:MANAGE') ? () => openCreate('library') : undefined} />;
    if (selectedTab === 'hostel') return <HostelView rooms={rooms.data ?? []} loading={rooms.isLoading} error={rooms.error} onRetry={() => void rooms.refetch()} onCreate={can('HOSTEL:MANAGE') ? () => openCreate('hostel') : undefined} />;
    if (selectedTab === 'support') return <SupportView tickets={tickets.data ?? []} loading={tickets.isLoading} error={tickets.error} onRetry={() => void tickets.refetch()} onCreate={can('SUPPORT:MANAGE') ? () => openCreate('support') : undefined} />;
    if (selectedTab === 'transport') return <TransportOperationalView />;
    return <EmptyState title="No operational areas are available to your account." compact />;
  };

  return <section className="workspace-page operations-page">
    <PageHeader eyebrow="OPERATIONS MANAGEMENT" title="Operations workspace" description="Manage inventory, library assets, hostel allocations, support tickets, and transport logistics from one controlled workspace." />
    <Tabs
      tabs={visibleTabs.map(item => ({ ...item, content: item.id === selectedTab ? renderTab() : undefined }))}
      value={selectedTab ?? ''}
      onChange={id => {
        const nextTab = visibleTabs.find(item => item.id === id);
        if (nextTab) {
          setTab(nextTab.id);
          setSearchParams({ tab: nextTab.id });
        }
      }}
      label="Operations navigation"
    />
    <CreateModal type={modal} error={mutationError} onClose={() => { setModal(null); setMutationError(''); }} onInventory={createInventory} onLibrary={createLibrary} onRoom={createRoom} onTicket={createTicket} />
  </section>;
}

function InventoryView({ items, loading, error, onRetry, onCreate }: { items: InventoryItem[]; loading: boolean; error: unknown; onRetry: () => void; onCreate?: () => void }) {
  const columns = [
    { key: 'code', header: 'Code' }, { key: 'name', header: 'Item' }, { key: 'category', header: 'Category' }, { key: 'currentStock', header: 'Stock' }, { key: 'minStockLevel', header: 'Minimum' },
  ];
  return <OperationsPanel title="Inventory" subtitle="Track stock levels and movement readiness." loading={loading} error={error} onRetry={onRetry} onCreate={onCreate} actionLabel="Add item">
    <DataTable<InventoryItem> columns={columns} rows={items} rowKey={item => String(item.id)} caption="Inventory items" emptyMessage="No inventory items found" />
  </OperationsPanel>;
}

function LibraryView({ items, loading, error, onRetry, onCreate }: { items: LibraryItem[]; loading: boolean; error: unknown; onRetry: () => void; onCreate?: () => void }) {
  const columns = [{ key: 'isbn', header: 'ISBN' }, { key: 'title', header: 'Title' }, { key: 'author', header: 'Author' }, { key: 'availableCopies', header: 'Available' }, { key: 'rackLocation', header: 'Rack' }];
  return <OperationsPanel title="Library" subtitle="Maintain catalog copies and rack availability." loading={loading} error={error} onRetry={onRetry} onCreate={onCreate} actionLabel="Add book">
    <DataTable<LibraryItem> columns={columns} rows={items} rowKey={item => String(item.id)} caption="Library items" emptyMessage="No library items found" />
  </OperationsPanel>;
}

function HostelView({ rooms, loading, error, onRetry, onCreate }: { rooms: HostelRoom[]; loading: boolean; error: unknown; onRetry: () => void; onCreate?: () => void }) {
  const columns = [{ key: 'roomNo', header: 'Room' }, { key: 'floor', header: 'Floor' }, { key: 'block', header: 'Block' }, { key: 'capacity', header: 'Capacity' }, { key: 'roomType', header: 'Type' }];
  return <OperationsPanel title="Hostel" subtitle="Manage room capacity and active room status." loading={loading} error={error} onRetry={onRetry} onCreate={onCreate} actionLabel="Add room">
    <DataTable<HostelRoom> columns={columns} rows={rooms} rowKey={room => String(room.id)} caption="Hostel rooms" emptyMessage="No hostel rooms found" />
  </OperationsPanel>;
}

function SupportView({ tickets, loading, error, onRetry, onCreate }: { tickets: SupportTicket[]; loading: boolean; error: unknown; onRetry: () => void; onCreate?: () => void }) {
  const columns = [{ key: 'title', header: 'Ticket' }, { key: 'category', header: 'Category' }, { key: 'priority', header: 'Priority' }, { key: 'status', header: 'Status' }, { key: 'updatedAt', header: 'Updated' }];
  return <OperationsPanel title="Support tickets" subtitle="Track support requests and resolutions." loading={loading} error={error} onRetry={onRetry} onCreate={onCreate} actionLabel="Create ticket">
    <DataTable<SupportTicket> columns={columns} rows={tickets} rowKey={ticket => String(ticket.id)} caption="Support tickets" emptyMessage="No support tickets found" />
  </OperationsPanel>;
}

function OperationsPanel({ title, subtitle, children, loading, error, onRetry, onCreate, actionLabel }: { title: string; subtitle: string; children: React.ReactNode; loading: boolean; error: unknown; onRetry: () => void; onCreate?: () => void; actionLabel: string }) {
  return <Card className="operations-panel"><div className="operations-panel-head"><div><h2>{title}</h2><p>{subtitle}</p></div>{onCreate && <Button size="sm" onClick={onCreate}>{actionLabel}</Button>}</div>{loading ? <EmptyState title="Loading records…" compact /> : error ? <ErrorState title="Records unavailable" description={error instanceof Error ? error.message : 'The operations data could not be loaded.'} onRetry={onRetry} /> : children}</Card>;
}

function CreateModal({ type, error, onClose, onInventory, onLibrary, onRoom, onTicket }: {
  type: 'inventory' | 'library' | 'hostel' | 'support' | null;
  error: string;
  onClose: () => void;
  onInventory: { mutate: (body: unknown) => void };
  onLibrary: { mutate: (body: unknown) => void };
  onRoom: { mutate: (body: unknown) => void };
  onTicket: { mutate: (body: unknown) => void };
}) {
  const [form, setForm] = useState<FormState>({});
  if (!type) return null;
  const title = type === 'inventory' ? 'Add inventory item' : type === 'library' ? 'Add library item' : type === 'hostel' ? 'Add hostel room' : 'Create support ticket';
  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const body = type === 'inventory' ? { name: form.name, code: form.code, category: form.category, unit: form.unit, currentStock: Number(form.currentStock ?? 0), minStockLevel: Number(form.minStockLevel ?? 0), description: form.description } :
      type === 'library' ? { isbn: form.isbn, title: form.title, author: form.author, category: form.category, totalCopies: Number(form.totalCopies ?? 1), availableCopies: Number(form.availableCopies ?? 1), rackLocation: form.rackLocation } :
      type === 'hostel' ? { roomNo: form.roomNo, floor: form.floor, block: form.block, capacity: Number(form.capacity ?? 1), roomType: form.roomType, active: form.active === 'true' } :
      { title: form.title, description: form.description, category: form.category, priority: form.priority, status: form.status, assignee: null };
    if (type === 'inventory') onInventory.mutate(body); else if (type === 'library') onLibrary.mutate(body); else if (type === 'hostel') onRoom.mutate(body); else onTicket.mutate(body);
  };
  return <Modal open onClose={onClose} title={title} size="md" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="operations-create-form">Save</Button></>}>
    <form id="operations-create-form" className="operations-form" onSubmit={submit}>
      {error && <Alert tone="danger" title="Could not save record">{error}</Alert>}
      {type === 'inventory' && <><FormField label="Name" required><Input required value={form.name ?? ''} onChange={e => setForm({ ...form, name: e.target.value })} /></FormField><FormField label="Code" required><Input required value={form.code ?? ''} onChange={e => setForm({ ...form, code: e.target.value })} /></FormField><FormField label="Category"><Input value={form.category ?? ''} onChange={e => setForm({ ...form, category: e.target.value })} /></FormField><FormField label="Unit"><Input value={form.unit ?? ''} onChange={e => setForm({ ...form, unit: e.target.value })} /></FormField><FormField label="Current stock"><Input type="number" value={form.currentStock ?? ''} onChange={e => setForm({ ...form, currentStock: e.target.value })} /></FormField><FormField label="Minimum stock"><Input type="number" value={form.minStockLevel ?? ''} onChange={e => setForm({ ...form, minStockLevel: e.target.value })} /></FormField></>}
      {type === 'library' && <><FormField label="ISBN"><Input value={form.isbn ?? ''} onChange={e => setForm({ ...form, isbn: e.target.value })} /></FormField><FormField label="Title" required><Input required value={form.title ?? ''} onChange={e => setForm({ ...form, title: e.target.value })} /></FormField><FormField label="Author"><Input value={form.author ?? ''} onChange={e => setForm({ ...form, author: e.target.value })} /></FormField><FormField label="Category"><Input value={form.category ?? ''} onChange={e => setForm({ ...form, category: e.target.value })} /></FormField><FormField label="Available copies"><Input type="number" value={form.availableCopies ?? ''} onChange={e => setForm({ ...form, availableCopies: e.target.value })} /></FormField><FormField label="Rack"><Input value={form.rackLocation ?? ''} onChange={e => setForm({ ...form, rackLocation: e.target.value })} /></FormField></>}
      {type === 'hostel' && <><FormField label="Room number" required><Input required value={form.roomNo ?? ''} onChange={e => setForm({ ...form, roomNo: e.target.value })} /></FormField><FormField label="Floor"><Input value={form.floor ?? ''} onChange={e => setForm({ ...form, floor: e.target.value })} /></FormField><FormField label="Block"><Input value={form.block ?? ''} onChange={e => setForm({ ...form, block: e.target.value })} /></FormField><FormField label="Capacity"><Input type="number" value={form.capacity ?? ''} onChange={e => setForm({ ...form, capacity: e.target.value })} /></FormField><FormField label="Room type"><Select value={form.roomType ?? 'SINGLE'} onChange={e => setForm({ ...form, roomType: e.target.value })}><option>SINGLE</option><option>DOUBLE</option><option>DORMITORY</option></Select></FormField></>}
      {type === 'support' && <><FormField label="Title" required><Input required value={form.title ?? ''} onChange={e => setForm({ ...form, title: e.target.value })} /></FormField><FormField label="Description" required><Textarea required value={form.description ?? ''} onChange={e => setForm({ ...form, description: e.target.value })} /></FormField><FormField label="Category"><Input value={form.category ?? ''} onChange={e => setForm({ ...form, category: e.target.value })} /></FormField><FormField label="Priority"><Select value={form.priority ?? 'MEDIUM'} onChange={e => setForm({ ...form, priority: e.target.value })}><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></Select></FormField></>}
    </form>
  </Modal>;
}
