/**
 * React Query hooks for sources.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/sources';

/**
 * Hook to fetch all sources.
 */
export function useSources() {
  return useQuery({
    queryKey: ['sources'],
    queryFn: api.getAllSources
  });
}

/**
 * Hook to fetch source by ID.
 */
export function useSource(id: string) {
  return useQuery({
    queryKey: ['sources', id],
    queryFn: () => api.getSourceById(id),
    enabled: !!id
  });
}

/**
 * Hook to fetch source statistics.
 */
export function useSourceStats() {
  return useQuery({
    queryKey: ['sources', 'stats'],
    queryFn: api.getSourceStatistics
  });
}

/**
 * Hook to create a new source.
 */
export function useCreateSource() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.createSource,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sources'] });
    }
  });
}

/**
 * Hook to complete a scan.
 */
export function useCompleteScan() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ sourceId, completedAt }: { sourceId: string; completedAt: string }) =>
      api.completeScan(sourceId, completedAt),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['sources'] });
      queryClient.invalidateQueries({ queryKey: ['sources', variables.sourceId] });
    }
  });
}

/**
 * Hook to delete a source.
 */
export function useDeleteSource() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.deleteSource,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sources'] });
    }
  });
}

/**
 * Hook to fetch source folder tree.
 */
export function useSourceTree(sourceId: string) {
  return useQuery({
    queryKey: ['sources', sourceId, 'tree'],
    queryFn: () => api.getSourceTree(sourceId),
    enabled: !!sourceId
  });
}
