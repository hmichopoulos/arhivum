/**
 * React Query hooks for code projects.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/codeProjects';

export function useCodeProjects() {
  return useQuery({
    queryKey: ['codeProjects'],
    queryFn: api.getAllProjects
  });
}

export function useCodeProject(id: string) {
  return useQuery({
    queryKey: ['codeProjects', id],
    queryFn: () => api.getProjectById(id),
    enabled: !!id
  });
}

export function useProjectsBySource(sourceId: string) {
  return useQuery({
    queryKey: ['codeProjects', 'source', sourceId],
    queryFn: () => api.getProjectsBySource(sourceId),
    enabled: !!sourceId
  });
}

export function useDuplicateProjects() {
  return useQuery({
    queryKey: ['codeProjects', 'duplicates'],
    queryFn: api.getDuplicateProjects
  });
}

export function useProjectStats() {
  return useQuery({
    queryKey: ['codeProjects', 'stats'],
    queryFn: api.getProjectStats
  });
}

export function useDeleteProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.deleteProject,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['codeProjects'] });
    }
  });
}
