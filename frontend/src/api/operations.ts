import { apiGet, apiPost } from './client';

export type InventoryItem = {
  id: number;
  name: string;
  code: string;
  category: string;
  unit: string;
  currentStock: number;
  minStockLevel: number;
  description: string;
};

export type LibraryItem = {
  id: number;
  isbn: string;
  title: string;
  author: string;
  category: string;
  totalCopies: number;
  availableCopies: number;
  rackLocation: string;
};

export type HostelRoom = {
  id: number;
  roomNo: string;
  floor: string;
  block: string;
  capacity: number;
  roomType: string;
  active: boolean;
};

export type SupportTicket = {
  id: number;
  title: string;
  description: string;
  category: string;
  priority: string;
  status: string;
  raisedBy: number;
  assignedTo: number | null;
  createdAt: string;
  updatedAt: string;
};

export const operationsApi = {
  inventory: () => apiGet<InventoryItem[]>('/api/v1/operations/inventory/items'),
  createInventoryItem: (body: unknown) => apiPost<InventoryItem>('/api/v1/operations/inventory/items', body),
  library: () => apiGet<LibraryItem[]>('/api/v1/operations/library/items'),
  createLibraryItem: (body: unknown) => apiPost<LibraryItem>('/api/v1/operations/library/items', body),
  rooms: () => apiGet<HostelRoom[]>('/api/v1/operations/hostel/rooms'),
  createRoom: (body: unknown) => apiPost<HostelRoom>('/api/v1/operations/hostel/rooms', body),
  tickets: () => apiGet<SupportTicket[]>('/api/v1/operations/support/tickets'),
  createTicket: (body: unknown) => apiPost<SupportTicket>('/api/v1/operations/support/tickets', body),
};
