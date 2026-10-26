import React from 'react';
import ReactDOM from 'react-dom/client';
import { AppProviders } from './app/AppProviders';
import { AppRoutes } from './routes';
import { ThemeProvider } from './theme/ThemeProvider';
import './theme/tokens.css';
import './components/design-system.css';
import './layouts/app-shell.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </ThemeProvider>
  </React.StrictMode>,
);
