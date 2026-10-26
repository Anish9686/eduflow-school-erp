import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppProviders } from '../app/AppProviders';
import { AppRoutes } from '../routes';

const manager={id:10,username:'academic-admin',fullName:'Academic Admin',roles:['ADMIN'],permissions:['ACADEMIC:READ','ACADEMIC:MANAGE','TIMETABLE:MANAGE','HOMEWORK:READ','HOMEWORK:CREATE','SYLLABUS:READ','SYLLABUS:UPDATE']};
function response(data:unknown,status=200){return {ok:status>=200&&status<300,status,json:async()=>data} as Response;}
function renderRoutes(){return render(<ThemeProvider><AppProviders><AppRoutes/></AppProviders></ThemeProvider>);}

describe('academic module routes and screens',()=>{
  afterEach(()=>{cleanup();vi.unstubAllGlobals();});
  beforeEach(()=>{window.sessionStorage.clear();window.sessionStorage.setItem('erp-portal-access-token','academic-test');window.sessionStorage.setItem('erp-portal-access-expires',String(Date.now()+60_000));window.history.replaceState({},'','/app/academics/sessions');});

  it('renders academic sessions and submits a new session through the API',async()=>{
    const fetchMock=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
      const url=String(input);
      if(url.endsWith('/api/v1/me'))return response(manager);
      if(url.endsWith('/api/v1/academics/sessions')&&init?.method==='POST')return response({id:2,code:'2027-28',name:'2027–2028',startDate:'2027-04-01',endDate:'2028-03-31',active:false,createdAt:'2027-04-01T00:00:00'},201);
      if(url.endsWith('/academics/sessions'))return response([{id:1,code:'2026-27',name:'2026–2027',startDate:'2026-04-01',endDate:'2027-03-31',active:true,createdAt:'2026-04-01T00:00:00'}]);
      return response([],200);
    });vi.stubGlobal('fetch',fetchMock);
    renderRoutes();
    expect(await screen.findByRole('heading',{name:'Academic sessions'})).toBeInTheDocument();
    expect(await screen.findByText('2026–2027')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'New session'}));
    await userEvent.type(screen.getByLabelText(/Session code/),'2027-28');
    await userEvent.type(screen.getByLabelText(/^Name/),'2027–2028');
    await userEvent.type(screen.getByLabelText(/^Start date/),'2027-04-01');
    await userEvent.type(screen.getByLabelText(/^End date/),'2028-03-31');
    await userEvent.click(screen.getByRole('button',{name:'Save session'}));
    await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/v1/academics/sessions'),expect.objectContaining({method:'POST'})));
  });

  it('blocks an academic route when the user has no academic permission',async()=>{
    vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>String(input).endsWith('/api/v1/me')?response({...manager,permissions:[]}):response([])));
    renderRoutes();
    expect(await screen.findByRole('heading',{name:'You don’t have access to this area'})).toBeInTheDocument();
    expect(window.location.pathname).toBe('/app/forbidden');
  });
});
