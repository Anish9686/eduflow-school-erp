import { apiGet, apiPost, apiPut, apiPatch } from './client';

export interface BusResponse {
  id: number;
  busName: string;
  registrationNo: string;
  fleetNo?: string | null;
  busType: string;
  capacity: number;
  activeOccupancy: number;
  availableSeats: number;
  currentRouteId?: number | null;
  routeName?: string | null;
  routeCode?: string | null;
  driverStaffId?: number | null;
  driverName?: string | null;
  driverPhone?: string | null;
  conductorStaffId?: number | null;
  conductorName?: string | null;
  conductorPhone?: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';
  institutionId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBusRequest {
  busName: string;
  registrationNo: string;
  fleetNo?: string;
  busType?: string;
  capacity: number;
  routeId?: number | null;
  driverStaffId?: number | null;
  conductorStaffId?: number | null;
  institutionId?: string;
}

export interface UpdateBusRequest {
  busName: string;
  registrationNo: string;
  fleetNo?: string;
  busType?: string;
  capacity: number;
  routeId?: number | null;
  driverStaffId?: number | null;
  conductorStaffId?: number | null;
  status?: string;
}

export interface BusDetailResponse {
  bus: BusResponse;
  assignedStudents: StudentAssignmentResponse[];
  routeStops: RouteStopResponse[];
}

export interface RouteResponse {
  id: number;
  routeName: string;
  routeCode: string;
  description?: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  assignedBusId?: number | null;
  assignedBusName?: string | null;
  stopsCount: number;
  activeStudentsCount: number;
  institutionId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRouteRequest {
  routeName: string;
  routeCode: string;
  description?: string;
  stops?: RouteStopRequest[];
  institutionId?: string;
}

export interface UpdateRouteRequest {
  routeName: string;
  routeCode: string;
  description?: string;
  status?: string;
}

export interface RouteStopRequest {
  id?: number;
  stopName: string;
  stopOrder: number;
  pickupTime?: string;
  dropTime?: string;
}

export interface RouteStopResponse {
  id: number;
  routeId: number;
  stopName: string;
  stopOrder: number;
  pickupTime?: string | null;
  dropTime?: string | null;
}

export interface RouteDetailResponse {
  route: RouteResponse;
  stops: RouteStopResponse[];
  assignedBuses: BusResponse[];
}

export interface AssignStudentRequest {
  studentId: number;
  busId: number;
  routeId: number;
  pickupStopId?: number | null;
  dropStopId?: number | null;
  startDate: string;
  endDate?: string | null;
}

export interface ReassignStudentRequest {
  busId: number;
  routeId: number;
  pickupStopId?: number | null;
  dropStopId?: number | null;
  reason?: string;
}

export interface StudentAssignmentResponse {
  id: number;
  studentId: number;
  admissionNo: string;
  studentName: string;
  busId: number;
  busName: string;
  registrationNo: string;
  routeId: number;
  routeName: string;
  pickupStopId?: number | null;
  pickupStopName?: string | null;
  pickupTime?: string | null;
  dropStopId?: number | null;
  dropStopName?: string | null;
  dropTime?: string | null;
  startDate: string;
  endDate?: string | null;
  status: 'ACTIVE' | 'ENDED' | 'TRANSFERRED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
}

export interface StudentTransportView {
  assigned: boolean;
  assignmentId?: number | null;
  studentId?: number | null;
  studentName?: string | null;
  admissionNo?: string | null;
  busName?: string | null;
  registrationNo?: string | null;
  routeName?: string | null;
  pickupStopName?: string | null;
  pickupTime?: string | null;
  dropStopName?: string | null;
  dropTime?: string | null;
  driverName?: string | null;
  conductorName?: string | null;
  status?: string | null;
  routeStops: RouteStopResponse[];
  message?: string | null;
}

export interface ParentChildTransportView {
  childId: number;
  childName: string;
  admissionNo: string;
  assigned: boolean;
  assignmentId?: number | null;
  busName?: string | null;
  registrationNo?: string | null;
  routeName?: string | null;
  pickupStopName?: string | null;
  pickupTime?: string | null;
  dropStopName?: string | null;
  dropTime?: string | null;
  driverName?: string | null;
  conductorName?: string | null;
  status?: string | null;
  capacity?: number | null;
  occupancy?: number | null;
  availableSeats?: number | null;
  routeStops: RouteStopResponse[];
  message?: string | null;
}

export interface TransportOverviewResponse {
  totalActiveBuses: number;
  totalActiveRoutes: number;
  totalAssignedStudents: number;
  totalAvailableSeats: number;
  fullBusesCount: number;
  busesWithoutDriver: number;
  busesWithoutConductor: number;
  routesWithoutBus: number;
  recentHistory: AssignmentHistoryResponse[];
}

export interface AssignmentHistoryResponse {
  id: number;
  assignmentId: number;
  studentId: number;
  studentName: string;
  admissionNo: string;
  busId: number;
  busName: string;
  routeId: number;
  routeName: string;
  action: string;
  reason?: string | null;
  changedBy?: number | null;
  changerName?: string | null;
  changedAt: string;
}

export interface TransportStaffOption {
  id: number;
  employeeNo: string;
  name: string;
  designation: string;
  phone?: string | null;
  assignedBusId?: number | null;
  assignedBusName?: string | null;
}

export interface CreateTransportStaffRequest {
  employeeNo: string;
  firstName: string;
  lastName?: string;
  designation: string;
  phone?: string;
}

export interface StudentTransportOption {
  id: number;
  admissionNo: string;
  name: string;
  status: string;
  currentlyAssigned: boolean;
  currentBusName?: string | null;
}

export const transportApi = {
  getOverview: (): Promise<TransportOverviewResponse> =>
    apiGet<TransportOverviewResponse>('/api/v1/transport/overview'),

  getBuses: (params?: { search?: string; status?: string; routeId?: number }): Promise<BusResponse[]> =>
    apiGet<BusResponse[]>('/api/v1/transport/buses', params),

  getBus: (id: number): Promise<BusDetailResponse> =>
    apiGet<BusDetailResponse>(`/api/v1/transport/buses/${id}`),

  createBus: (data: CreateBusRequest): Promise<BusResponse> =>
    apiPost<BusResponse>('/api/v1/transport/buses', data),

  updateBus: (id: number, data: UpdateBusRequest): Promise<BusResponse> =>
    apiPut<BusResponse>(`/api/v1/transport/buses/${id}`, data),

  deactivateBus: (id: number): Promise<void> =>
    apiPatch<void>(`/api/v1/transport/buses/${id}/deactivate`),

  getRoutes: (params?: { search?: string; status?: string }): Promise<RouteResponse[]> =>
    apiGet<RouteResponse[]>('/api/v1/transport/routes', params),

  getRoute: (id: number): Promise<RouteDetailResponse> =>
    apiGet<RouteDetailResponse>(`/api/v1/transport/routes/${id}`),

  createRoute: (data: CreateRouteRequest): Promise<RouteResponse> =>
    apiPost<RouteResponse>('/api/v1/transport/routes', data),

  updateRoute: (id: number, data: UpdateRouteRequest): Promise<RouteResponse> =>
    apiPut<RouteResponse>(`/api/v1/transport/routes/${id}`, data),

  deactivateRoute: (id: number): Promise<void> =>
    apiPatch<void>(`/api/v1/transport/routes/${id}/deactivate`),

  getRouteStops: (routeId: number): Promise<RouteStopResponse[]> =>
    apiGet<RouteStopResponse[]>(`/api/v1/transport/routes/${routeId}/stops`),

  setRouteStops: (routeId: number, stops: RouteStopRequest[]): Promise<RouteStopResponse[]> =>
    apiPut<RouteStopResponse[]>(`/api/v1/transport/routes/${routeId}/stops`, stops),

  getAssignments: (params?: { search?: string; busId?: number; routeId?: number; status?: string }): Promise<StudentAssignmentResponse[]> =>
    apiGet<StudentAssignmentResponse[]>('/api/v1/transport/assignments', params),

  assignStudent: (data: AssignStudentRequest): Promise<StudentAssignmentResponse> =>
    apiPost<StudentAssignmentResponse>('/api/v1/transport/assignments', data),

  reassignStudent: (assignmentId: number, data: ReassignStudentRequest): Promise<StudentAssignmentResponse> =>
    apiPut<StudentAssignmentResponse>(`/api/v1/transport/assignments/${assignmentId}/reassign`, data),

  endAssignment: (assignmentId: number, reason?: string): Promise<void> =>
    apiPost<void>(`/api/v1/transport/assignments/${assignmentId}/end`, { reason: reason || 'Assignment ended' }),

  getHistory: (params?: { studentId?: number; busId?: number; limit?: number }): Promise<AssignmentHistoryResponse[]> =>
    apiGet<AssignmentHistoryResponse[]>('/api/v1/transport/history', params),

  getStaffOptions: (role?: 'DRIVER' | 'CONDUCTOR'): Promise<TransportStaffOption[]> =>
    apiGet<TransportStaffOption[]>('/api/v1/transport/staff-options', role ? { role } : undefined),

  createStaff: (data: CreateTransportStaffRequest): Promise<TransportStaffOption> =>
    apiPost<TransportStaffOption>('/api/v1/transport/staff', data),

  getStudentOptions: (search?: string): Promise<StudentTransportOption[]> =>
    apiGet<StudentTransportOption[]>('/api/v1/transport/student-options', search ? { search } : undefined),

  getMyAssignment: (): Promise<StudentTransportView> =>
    apiGet<StudentTransportView>('/api/v1/transport/my-assignment'),

  getMyChildren: (): Promise<ParentChildTransportView[]> =>
    apiGet<ParentChildTransportView[]>('/api/v1/transport/my-children'),
};
