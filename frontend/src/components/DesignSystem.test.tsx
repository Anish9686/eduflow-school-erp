import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  Alert,
  Button,
  Checkbox,
  ConfirmDialog,
  DataTable,
  DatePicker,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  Modal,
  Pagination,
  Radio,
  Select,
  Skeleton,
  StatCard,
  StatusBadge,
  Switch,
  Tabs,
  Textarea,
} from './DesignSystem';

describe('DesignSystem UI Components', () => {
  afterEach(() => cleanup());

  describe('Buttons & Controls', () => {
    it('renders Button with variants and sizes', async () => {
      const user = userEvent.setup();
      const handleClick = vi.fn();

      render(
        <Button variant="danger" size="sm" onClick={handleClick}>
          Delete Record
        </Button>
      );

      const button = screen.getByRole('button', { name: 'Delete Record' });
      expect(button).toBeInTheDocument();
      expect(button).toHaveClass('ds-button--danger');
      expect(button).toHaveClass('ds-button--sm');

      await user.click(button);
      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('renders Input and handles invalid state with aria-invalid', () => {
      const { rerender } = render(<Input placeholder="Enter username" />);
      const input = screen.getByPlaceholderText('Enter username');
      expect(input).not.toHaveClass('is-invalid');
      expect(input).not.toHaveAttribute('aria-invalid');

      rerender(<Input placeholder="Enter username" invalid />);
      expect(input).toHaveClass('is-invalid');
      expect(input).toHaveAttribute('aria-invalid', 'true');
    });

    it('renders Select with options and handles selection', async () => {
      const user = userEvent.setup();
      const handleChange = vi.fn();

      render(
        <Select aria-label="Role" onChange={handleChange}>
          <option value="ADMIN">Admin</option>
          <option value="TEACHER">Teacher</option>
        </Select>
      );

      const select = screen.getByRole('combobox', { name: 'Role' });
      expect(select).toHaveValue('ADMIN');

      await user.selectOptions(select, 'TEACHER');
      expect(handleChange).toHaveBeenCalled();
      expect(select).toHaveValue('TEACHER');
    });

    it('renders Textarea and DatePicker controls', () => {
      render(
        <div>
          <Textarea placeholder="Address details" invalid />
          <DatePicker aria-label="Date of Birth" />
        </div>
      );

      const textarea = screen.getByPlaceholderText('Address details');
      expect(textarea).toHaveClass('is-invalid');
      expect(textarea).toHaveAttribute('aria-invalid', 'true');

      const datePicker = screen.getByLabelText('Date of Birth');
      expect(datePicker).toHaveAttribute('type', 'date');
    });

    it('renders Checkbox, Radio, and Switch controls', async () => {
      const user = userEvent.setup();

      render(
        <div>
          <Checkbox label="Send Notifications" defaultChecked />
          <Radio name="option" label="Option One" />
          <Switch label="Dark Mode Feature" />
        </div>
      );

      const checkbox = screen.getByLabelText('Send Notifications');
      expect(checkbox).toBeChecked();

      const radio = screen.getByLabelText('Option One');
      expect(radio).not.toBeChecked();
      await user.click(radio);
      expect(radio).toBeChecked();

      const toggle = screen.getByRole('switch', { name: 'Dark Mode Feature' });
      expect(toggle).not.toBeChecked();
      await user.click(toggle);
      expect(toggle).toBeChecked();
    });
  });

  describe('FormField & Accessibility', () => {
    it('connects label, input, hint, and error via aria-describedby and role=alert', () => {
      render(
        <FormField
          label="Student Email"
          required
          hint="We will never share your email"
          error="Email address is required"
        >
          <Input type="email" placeholder="student@example.com" />
        </FormField>
      );

      const label = screen.getByText('Student Email');
      expect(label).toBeInTheDocument();
      expect(screen.getByText('*')).toBeInTheDocument();

      const input = screen.getByPlaceholderText('student@example.com');
      expect(input).toHaveAttribute('aria-invalid', 'true');

      const error = screen.getByRole('alert');
      expect(error).toHaveTextContent('Email address is required');

      const hint = screen.getByText('We will never share your email');
      expect(input.getAttribute('aria-describedby')).toContain(hint.id);
      expect(input.getAttribute('aria-describedby')).toContain(error.id);
    });
  });

  describe('Badges and Status Badges', () => {
    it('maps status strings to appropriate tones', () => {
      const { rerender } = render(<StatusBadge status="ACTIVE" />);
      expect(screen.getByText('ACTIVE')).toHaveClass('ds-badge--success');

      rerender(<StatusBadge status="PENDING" />);
      expect(screen.getByText('PENDING')).toHaveClass('ds-badge--warning');

      rerender(<StatusBadge status="REJECTED" />);
      expect(screen.getByText('REJECTED')).toHaveClass('ds-badge--danger');

      rerender(<StatusBadge status="UNKNOWN" />);
      expect(screen.getByText('UNKNOWN')).toHaveClass('ds-badge--neutral');
    });
  });

  describe('Cards and StatCards', () => {
    it('renders StatCard with value, change badge, and detail', () => {
      render(
        <StatCard
          label="Total Enrollments"
          value="1,250"
          change="+12% YoY"
          detail="Active academic session"
          tone="success"
          icon="🎓"
        />
      );

      expect(screen.getByText('Total Enrollments')).toBeInTheDocument();
      expect(screen.getByText('1,250')).toBeInTheDocument();
      expect(screen.getByText('+12% YoY')).toBeInTheDocument();
      expect(screen.getByText('Active academic session')).toBeInTheDocument();
      expect(screen.getByText('🎓')).toBeInTheDocument();
    });
  });

  describe('DataTable & Pagination', () => {
    type TestRow = { id: string; name: string; score: number };
    const columns = [
      { key: 'name', header: 'Student Name' },
      { key: 'score', header: 'Marks Score' },
    ];
    const rows: TestRow[] = [
      { id: '1', name: 'Alice', score: 95 },
      { id: '2', name: 'Bob', score: 88 },
    ];

    it('renders table headers, rows, and cells', () => {
      render(
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          caption="Students Scoreboard"
        />
      );

      expect(screen.getByText('Student Name')).toBeInTheDocument();
      expect(screen.getByText('Marks Score')).toBeInTheDocument();
      expect(screen.getByText('Alice')).toBeInTheDocument();
      expect(screen.getByText('95')).toBeInTheDocument();
      expect(screen.getByText('Bob')).toBeInTheDocument();
    });

    it('renders empty message when no rows are provided', () => {
      render(
        <DataTable
          columns={columns}
          rows={[]}
          rowKey={(r) => (r as TestRow).id}
          caption="Empty Table"
          emptyMessage="No students found for this class"
        />
      );

      expect(screen.getByText('No students found for this class')).toBeInTheDocument();
    });

    it('renders Pagination controls and responds to next/prev clicks', async () => {
      const user = userEvent.setup();
      const onPageChange = vi.fn();

      render(
        <Pagination page={2} pageCount={5} onPageChange={onPageChange} itemLabel="page" />
      );

      expect(screen.getByText('2 of 5 pages')).toBeInTheDocument();

      const prevBtn = screen.getByRole('button', { name: 'Previous' });
      const nextBtn = screen.getByRole('button', { name: 'Next' });

      await user.click(prevBtn);
      expect(onPageChange).toHaveBeenCalledWith(1);

      await user.click(nextBtn);
      expect(onPageChange).toHaveBeenCalledWith(3);
    });
  });

  describe('Tabs', () => {
    it('renders tabs and switches active tab on click and keyboard navigation', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const tabs = [
        { id: 'general', label: 'General Info', content: <p>General Content</p> },
        { id: 'academic', label: 'Academic Info', content: <p>Academic Content</p> },
      ];

      render(<Tabs tabs={tabs} value="general" onChange={onChange} />);

      const generalTab = screen.getByRole('tab', { name: 'General Info' });
      const academicTab = screen.getByRole('tab', { name: 'Academic Info' });

      expect(generalTab).toHaveAttribute('aria-selected', 'true');
      expect(academicTab).toHaveAttribute('aria-selected', 'false');
      expect(screen.getByText('General Content')).toBeInTheDocument();

      await user.click(academicTab);
      expect(onChange).toHaveBeenCalledWith('academic');

      await user.type(generalTab, '{arrowright}');
      expect(onChange).toHaveBeenCalledWith('academic');
    });
  });

  describe('Modal & ConfirmDialog', () => {
    it('renders Modal when open and closes on Close button', async () => {
      const user = userEvent.setup();
      const handleClose = vi.fn();

      render(
        <Modal open onClose={handleClose} title="Edit Student Profile">
          <p>Modal body content</p>
        </Modal>
      );

      expect(screen.getByRole('dialog', { name: 'Edit Student Profile' })).toBeInTheDocument();
      expect(screen.getByText('Modal body content')).toBeInTheDocument();

      const closeBtn = screen.getByRole('button', { name: 'Close dialog' });
      await user.click(closeBtn);
      expect(handleClose).toHaveBeenCalledTimes(1);
    });

    it('does not render Modal when open is false', () => {
      render(
        <Modal open={false} onClose={() => {}} title="Hidden Modal">
          <p>Hidden body</p>
        </Modal>
      );

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('renders ConfirmDialog with Cancel and Confirm action triggers', async () => {
      const user = userEvent.setup();
      const handleClose = vi.fn();
      const handleConfirm = vi.fn();

      render(
        <ConfirmDialog
          open
          onClose={handleClose}
          onConfirm={handleConfirm}
          title="Confirm Deletion"
          confirmLabel="Delete Permanently"
        >
          Are you sure you want to delete this record?
        </ConfirmDialog>
      );

      expect(screen.getByText('Are you sure you want to delete this record?')).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(handleClose).toHaveBeenCalledTimes(1);

      await user.click(screen.getByRole('button', { name: 'Delete Permanently' }));
      expect(handleConfirm).toHaveBeenCalledTimes(1);
    });
  });

  describe('Loading, Error, and Empty States', () => {
    it('renders Skeleton with dimensions and round style', () => {
      const { container } = render(<Skeleton width="120px" height={24} rounded />);
      const skeleton = container.querySelector('.ds-skeleton');
      expect(skeleton).toHaveClass('ds-skeleton--round');
      expect(skeleton).toHaveStyle({ width: '120px', height: '24px' });
    });

    it('renders EmptyState with icon, title, description, and action', async () => {
      const user = userEvent.setup();
      const handleAction = vi.fn();

      render(
        <EmptyState
          title="No attendance records"
          description="Select a class and section to view records."
          action={<Button onClick={handleAction}>Mark Attendance</Button>}
        />
      );

      expect(screen.getByText('No attendance records')).toBeInTheDocument();
      expect(screen.getByText('Select a class and section to view records.')).toBeInTheDocument();

      const button = screen.getByRole('button', { name: 'Mark Attendance' });
      await user.click(button);
      expect(handleAction).toHaveBeenCalledTimes(1);
    });

    it('renders ErrorState with alert role, message, and retry button', async () => {
      const user = userEvent.setup();
      const handleRetry = vi.fn();

      render(
        <ErrorState
          title="Connection Failure"
          description="Failed to reach the ERP backend server."
          onRetry={handleRetry}
        />
      );

      const alert = screen.getByRole('alert');
      expect(alert).toBeInTheDocument();
      expect(screen.getByText('Connection Failure')).toBeInTheDocument();
      expect(screen.getByText('Failed to reach the ERP backend server.')).toBeInTheDocument();

      const retryBtn = screen.getByRole('button', { name: 'Try again' });
      await user.click(retryBtn);
      expect(handleRetry).toHaveBeenCalledTimes(1);
    });

    it('renders Alert component with proper role depending on tone', () => {
      const { rerender } = render(<Alert tone="danger" title="Error">Operation failed</Alert>);
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText('Error')).toBeInTheDocument();
      expect(screen.getByText('Operation failed')).toBeInTheDocument();

      rerender(<Alert tone="info" title="Info">Notice message</Alert>);
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });
});
