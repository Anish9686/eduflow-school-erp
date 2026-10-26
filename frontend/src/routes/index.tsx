import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from '../auth/AuthProvider';
import { AppLayout } from '../layouts/AppLayout';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { DashboardPage, ForbiddenPage, RequirePermission } from '../pages/WorkspacePages';
import { AdmissionCreatePage, AdmissionDetailPage, AdmissionListPage, StudentDetailPage, StudentFormPage, StudentListPage } from '../pages/StudentModulePages';
import { ClassesPage, HomeworkPage, SectionsPage, SessionsPage, SubjectsPage, SyllabusPage, TimetablePage } from '../pages/AcademicPages';
import { AttendancePage } from '../pages/AttendancePages';
import { FinancePage } from '../pages/FinancePage';
import { OperationsPage } from '../pages/OperationsPage';
import { TransportPage } from '../pages/TransportPage';
import { AdministrationPage } from '../pages/AdministrationPage';
import { ReportsPage } from '../pages/ReportsPage';
import { SettingsPage } from '../pages/SettingsPage';
import { ExamManagementPage } from '../pages/ExamPages';
const HelpCenterPage = lazy(() => import('../pages/HelpCenterPage').then(module => ({ default: module.HelpCenterPage })));

function AuthenticatedLayout() {
  const { status } = useAuth(); const location = useLocation();
  if (status === 'loading') return <div className="auth-loading" role="status">Loading your workspace…</div>;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <AppLayout />;
}

function PermissionSetPage({ permissions, children }: React.PropsWithChildren<{ permissions: string[] }>) {
  const { hasPermission, status } = useAuth(); const location = useLocation();
  if (status === 'loading') return <div className="auth-loading" role="status">Checking your access…</div>;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!permissions.some(hasPermission)) return <Navigate to="/app/forbidden" replace state={{ path: location.pathname }} />;
  return children;
}

function RequireStaffStudents({ children }: React.PropsWithChildren) {
  const { user, hasPermission, status } = useAuth(); const location = useLocation();
  if (status === 'loading') return <div className="auth-loading" role="status">Checking your access…</div>;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  const isStudent = user?.roles.includes('STUDENT') && !user?.roles.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER');
  if (isStudent || !hasPermission('STUDENT:READ')) return <Navigate to="/app/forbidden" replace state={{ path: location.pathname }} />;
  return children;
}

function RequireNonAccountantAttendance({ children }: React.PropsWithChildren) {
  const { user, hasPermission, status } = useAuth(); const location = useLocation();
  if (status === 'loading') return <div className="auth-loading" role="status">Checking your access…</div>;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  const isAccountant = user?.roles.includes('ACCOUNTANT') && !user?.roles.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER');
  const attendancePerms = ['ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE','STAFF_ATTENDANCE:READ','STAFF_ATTENDANCE:MARK','STAFF_ATTENDANCE:MANAGE'];
  if (isAccountant || !attendancePerms.some(hasPermission)) return <Navigate to="/app/forbidden" replace state={{ path: location.pathname }} />;
  return children;
}

function RequireStaffAcademic({ children }: React.PropsWithChildren) {
  const { user, hasPermission, status } = useAuth(); const location = useLocation();
  if (status === 'loading') return <div className="auth-loading" role="status">Checking your access…</div>;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  const isStudentOrParent = user?.roles.some(r => r === 'STUDENT' || r === 'PARENT_GUARDIAN');
  if (isStudentOrParent || !hasPermission('ACADEMIC:READ')) return <Navigate to="/app/forbidden" replace state={{ path: location.pathname }} />;
  return children;
}

export function AppRoutes() {
  return <BrowserRouter><AuthProvider><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
    <Route path="/app" element={<AuthenticatedLayout />}>
      <Route index element={<Navigate to="dashboard" replace />} />
      <Route path="dashboard" element={<DashboardPage />} />
      <Route path="students" element={<RequireStaffStudents><StudentListPage /></RequireStaffStudents>} />
      <Route path="students/new" element={<RequirePermission permission="STUDENT:CREATE"><StudentFormPage /></RequirePermission>} />
      <Route path="students/:id/edit" element={<RequirePermission permission="STUDENT:UPDATE"><StudentFormPage /></RequirePermission>} />
      <Route path="students/:id" element={<RequirePermission permission="STUDENT:READ"><StudentDetailPage /></RequirePermission>} />
      <Route path="admissions" element={<RequirePermission permission="ADMISSION:READ"><AdmissionListPage /></RequirePermission>} />
      <Route path="admissions/new" element={<RequirePermission permission="ADMISSION:CREATE"><AdmissionCreatePage /></RequirePermission>} />
      <Route path="admissions/:id" element={<RequirePermission permission="ADMISSION:READ"><AdmissionDetailPage /></RequirePermission>} />
      <Route path="academics/sessions" element={<RequireStaffAcademic><SessionsPage /></RequireStaffAcademic>} />
      <Route path="academics/classes" element={<RequireStaffAcademic><ClassesPage /></RequireStaffAcademic>} />
      <Route path="academics/sections" element={<RequireStaffAcademic><SectionsPage /></RequireStaffAcademic>} />
      <Route path="academics/subjects" element={<RequireStaffAcademic><SubjectsPage /></RequireStaffAcademic>} />
      <Route path="academics/timetable" element={<PermissionSetPage permissions={['ACADEMIC:READ','TIMETABLE:READ']}><TimetablePage /></PermissionSetPage>} />
      <Route path="academics/homework" element={<PermissionSetPage permissions={['HOMEWORK:READ','HOMEWORK:CREATE','HOMEWORK:MANAGE']}><HomeworkPage /></PermissionSetPage>} />
      <Route path="academics/syllabus" element={<PermissionSetPage permissions={['SYLLABUS:READ','SYLLABUS:UPDATE','SYLLABUS:MANAGE']}><SyllabusPage /></PermissionSetPage>} />
      <Route path="academics/examinations" element={<PermissionSetPage permissions={['EXAM:READ','RESULT:READ']}><ExamManagementPage /></PermissionSetPage>} />
      <Route path="examinations" element={<Navigate to="/app/academics/examinations" replace />} />
      <Route path="results" element={<Navigate to="/app/academics/examinations" replace />} />
      <Route path="attendance" element={<RequireNonAccountantAttendance><AttendancePage /></RequireNonAccountantAttendance>} />
      <Route path="finance" element={<PermissionSetPage permissions={['FEE:MANAGE','FEE:COLLECT','FEE:REFUND','FEE_DISCOUNT:REQUEST','FEE_DISCOUNT:APPROVE','EXPENSE:MANAGE','EXPENSE:APPROVE','FINANCE_REPORT:READ','FINANCE_REPORT:EXPORT']}><FinancePage /></PermissionSetPage>} />
      <Route path="operations" element={<PermissionSetPage permissions={['INVENTORY:READ','LIBRARY:READ','HOSTEL:READ','SUPPORT:READ','TRANSPORT:READ']}><OperationsPage /></PermissionSetPage>} />
      <Route path="operations/transport" element={<Navigate to="/app/operations?tab=transport" replace />} />
      <Route path="transport" element={<PermissionSetPage permissions={['TRANSPORT:READ']}><TransportPage /></PermissionSetPage>} />
      <Route path="administration" element={<PermissionSetPage permissions={['RBAC:ROLE_READ','USER:READ','USER:CREATE','USER:MANAGE','AUDIT:READ','USER:IMPORT']}><AdministrationPage /></PermissionSetPage>} />
      <Route path="reports" element={<PermissionSetPage permissions={['STUDENT:READ','ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE','FINANCE_REPORT:READ']}><ReportsPage /></PermissionSetPage>} />
      <Route path="settings" element={<SettingsPage />} />
      <Route path="help" element={<Suspense fallback={<div className="auth-loading" role="status">Loading Help Center…</div>}><HelpCenterPage /></Suspense>} />
      <Route path="forbidden" element={<ForbiddenPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
  </Routes></AuthProvider></BrowserRouter>;
}
