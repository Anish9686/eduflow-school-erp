import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import type { PropsWithChildren } from 'react';
import { ApiRequestError } from '../api/client';

export function AppProviders({ children }: PropsWithChildren) {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          if (error instanceof ApiRequestError && error.status > 0 && error.status < 500) return false;
          return failureCount < 1;
        },
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
      },
    },
  }));
  useEffect(() => {
    const clearUserScopedCache = () => queryClient.clear();
    window.addEventListener('erp:unauthorized', clearUserScopedCache);
    window.addEventListener('erp:authenticated', clearUserScopedCache);
    return () => {
      window.removeEventListener('erp:unauthorized', clearUserScopedCache);
      window.removeEventListener('erp:authenticated', clearUserScopedCache);
    };
  }, [queryClient]);
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
