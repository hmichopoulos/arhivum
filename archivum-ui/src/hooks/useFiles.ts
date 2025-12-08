/**
 * React Query hooks for files.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/files';

/**
 * Hook to fetch file by ID.
 */
export function useFile(id: string) {
  return useQuery({
    queryKey: ['files', id],
    queryFn: () => api.getFileById(id),
    enabled: !!id
  });
}

/**
 * Hook to query files with filters.
 */
export function useFiles(params: {
  sourceId?: string;
  extension?: string;
  mimeType?: string;
  isDuplicate?: boolean;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ['files', 'query', params],
    queryFn: () => api.queryFiles(params),
    enabled: !!params.sourceId // Require at least a sourceId to query
  });
}

/**
 * Hook to ingest a file batch (used by scanner).
 */
export function useIngestFileBatch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.ingestFileBatch,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['files'] });
      queryClient.invalidateQueries({ queryKey: ['sources'] });
    }
  });
}
