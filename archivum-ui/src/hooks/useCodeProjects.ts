/**
 * React Query hooks for code projects.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/code-projects';
import type { ProjectType } from '../types/code-project';

/**
 * Hook to fetch all code projects.
 */
export function useCodeProjects() {
  return useQuery({
    queryKey: ['code-projects'],
    queryFn: api.getAllProjects
  });
}

/**
 * Hook to fetch code project by ID.
 */
export function useCodeProject(id: string) {
  return useQuery({
    queryKey: ['code-projects', id],
    queryFn: () => api.getProjectById(id),
    enabled: !!id
  });
}

/**
 * Hook to fetch projects by source.
 */
export function useCodeProjectsBySource(sourceId: string) {
  return useQuery({
    queryKey: ['code-projects', 'source', sourceId],
    queryFn: () => api.getProjectsBySource(sourceId),
    enabled: !!sourceId
  });
}

/**
 * Hook to fetch projects by type.
 */
export function useCodeProjectsByType(type: ProjectType) {
  return useQuery({
    queryKey: ['code-projects', 'type', type],
    queryFn: () => api.getProjectsByType(type)
  });
}

/**
 * Hook to fetch duplicate projects.
 */
export function useCodeProjectDuplicates() {
  return useQuery({
    queryKey: ['code-projects', 'duplicates'],
    queryFn: api.getDuplicates
  });
}

/**
 * Hook to fetch project statistics.
 */
export function useCodeProjectStats() {
  return useQuery({
    queryKey: ['code-projects', 'stats'],
    queryFn: api.getStatistics
  });
}

/**
 * Hook to delete a code project.
 */
export function useDeleteCodeProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.deleteProject,
    onSuccess: () => {
      // Invalidate all code project queries
      queryClient.invalidateQueries({ queryKey: ['code-projects'] });
    }
  });
}
