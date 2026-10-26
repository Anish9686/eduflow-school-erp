import { useQuery } from '@tanstack/react-query';
import { apiGet } from '../api/client';
import type { ApiInfo } from '../types/api';

export function useApiInfo() {
  return useQuery({
    queryKey: ['platform', 'api-info'],
    queryFn: ({ signal }) => apiGet<ApiInfo>('/api/v1/info', signal),
  });
}
