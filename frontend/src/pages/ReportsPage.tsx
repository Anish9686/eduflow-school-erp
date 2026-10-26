import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reportsApi, saveReport } from '../api/reports';
import type { ExportJob, ReportDefinition, ReportPageData } from '../api/reports';
import './reports-page.css';

export function ReportsPage() {
  const [code, setCode] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [sortBy, setSortBy] = useState('');
  const [direction, setDirection] = useState('ASC');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');
  const catalogQuery = useQuery({ queryKey: ['reports', 'catalog'], queryFn: reportsApi.catalog });
  const catalog: ReportDefinition[] = catalogQuery.data ?? [];
  const selected = catalog.find(report => report.code === code);
  const activeFilters = useMemo(() => Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')), [filters]);
  const reportQuery = useQuery({
    queryKey: ['reports', 'result', code, activeFilters, page, sortBy, direction],
    queryFn: ({ signal }) => reportsApi.read(code, { ...activeFilters, page, size: 25, sortBy: sortBy || undefined, direction }, signal),
    enabled: Boolean(code),
    placeholderData: previous => previous,
  });
  const results: ReportPageData | undefined = reportQuery.data;
  const loading = code ? reportQuery.isPending : catalogQuery.isPending;
  const error = exportError || (catalogQuery.error instanceof Error ? catalogQuery.error.message : reportQuery.error instanceof Error ? reportQuery.error.message : '');
  useEffect(() => {
    if (catalogQuery.data?.length && !code) setCode(catalogQuery.data[0]!.code);
  }, [catalogQuery.data, catalogQuery.isError, code]);

  function changeReport(value: string) { setCode(value); setFilters({}); setPage(0); setSortBy(''); setExportError(''); }
  async function exportReport(format: string) {
    if (!selected) return;
    setExporting(true); setExportError('');
    try {
      const response = await reportsApi.export(code, { ...activeFilters, sortBy: sortBy || undefined, direction }, format, true);
      if ('blob' in response) { saveReport(response.blob, response.filename); return; }
      let job: ExportJob = response.job;
      for (let attempt = 0; attempt < 120 && !['FAILED', 'COMPLETED'].includes(job.status); attempt += 1) {
        await new Promise(resolve => window.setTimeout(resolve, 1000)); job = await reportsApi.job(job.jobId);
      }
      if (job.status !== 'COMPLETED') throw new Error('Report export did not complete.');
      const file = await reportsApi.download(job.jobId); saveReport(file.blob, file.filename);
    } catch (reason) { setExportError(reason instanceof Error ? reason.message : 'Export failed.'); }
    finally { setExporting(false); }
  }

  return <main className="reports-page">
    <header className="reports-heading"><div><span className="reports-eyebrow">INSIGHTS</span><h1>Reports</h1><p>View scoped reports and export authorized results.</p></div></header>
    <section className="reports-panel" aria-label="Report filters">
      <label className="reports-field"><span>Report</span><select value={code} onChange={event => changeReport(event.target.value)} disabled={!catalog.length}><option value="">Choose a report</option>{catalog.map(item => <option key={item.code} value={item.code}>{item.title}</option>)}</select></label>
      {selected?.filters.map(filter => <label className="reports-field" key={filter.key}><span>{filter.label}</span>{filter.type === 'select' ? <select value={filters[filter.key] ?? ''} onChange={event => { setFilters(current => ({ ...current, [filter.key]: event.target.value })); setPage(0); }}><option value="">All</option>{filter.options.map(option => <option key={option} value={option}>{option}</option>)}</select> : <input aria-label={filter.label} type={filter.type === 'number' ? 'number' : filter.type} value={filters[filter.key] ?? ''} onChange={event => { setFilters(current => ({ ...current, [filter.key]: event.target.value })); setPage(0); }} />}</label>)}
      <label className="reports-field"><span>Sort by</span><select value={sortBy || selected?.sortableFields[0] || ''} onChange={event => setSortBy(event.target.value)}>{selected?.sortableFields.map(field => <option key={field} value={field}>{selected.columns.find(column => column.key === field)?.label ?? field}</option>)}</select></label>
      <label className="reports-field"><span>Direction</span><select value={direction} onChange={event => setDirection(event.target.value)}><option>ASC</option><option>DESC</option></select></label>
    </section>
    {error && <div className="reports-error" role="alert">{error}{catalogQuery.isError && <button type="button" onClick={() => void catalogQuery.refetch()}>Retry</button>}{reportQuery.isError && <button type="button" onClick={() => void reportQuery.refetch()}>Retry</button>}</div>}
    <section className="reports-results" aria-label="Report results">
      <div className="reports-results-heading"><div><h2>{selected?.title ?? 'Report results'}</h2><p>{selected?.description ?? 'Select a report to view its results.'}</p></div><div className="reports-actions">{selected?.formats.map(format => <button type="button" key={format} disabled={loading || exporting} onClick={() => void exportReport(format)}>{exporting ? 'Preparing…' : `Export ${format}`}</button>)}</div></div>
      {loading ? <div className="reports-state" role="status">Loading report…</div> : results?.content.length ? <><div className="reports-table-wrap"><table><thead><tr>{results.columns.map(column => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>{results.content.map((row, index) => <tr key={`${index}-${String(row[results.columns[0]?.key ?? ''] ?? '')}`}>{results.columns.map(column => <td key={column.key}>{row[column.key] == null ? '—' : String(row[column.key])}</td>)}</tr>)}</tbody></table></div><div className="reports-pagination"><span>{results.totalElements} records · Page {results.totalPages ? results.page + 1 : 0} of {results.totalPages}</span><div><button type="button" disabled={page <= 0} onClick={() => setPage(value => Math.max(0, value - 1))}>Previous</button><button type="button" disabled={page + 1 >= results.totalPages} onClick={() => setPage(value => value + 1)}>Next</button></div></div></> : <div className="reports-state">{code ? 'No records match the selected report and filters.' : 'Choose a report to get started.'}</div>}
    </section>
  </main>;
}
