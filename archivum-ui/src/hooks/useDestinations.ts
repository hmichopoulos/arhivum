/**
 * React Query hooks for destinations.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/destinations';
import type { CreateDestinationRequest, UpdateDestinationRequest } from '../types/destination';

/**
 * Hook to fetch all destinations with real-time disk space information.
 */
export function useDestinations() {
  return useQuery({
    queryKey: ['destinations'],
    queryFn: api.getAllDestinations,
    refetchInterval: 30000 // Refresh every 30s for real-time disk space updates
  });
}

/**
 * Hook to fetch destination by ID with real-time disk space information.
 */
export function useDestination(id: string) {
  return useQuery({
    queryKey: ['destinations', id],
    queryFn: () => api.getDestinationById(id),
    enabled: !!id,
    refetchInterval: 30000 // Refresh every 30s for real-time disk space updates
  });
}

/**
 * Hook to fetch all active destinations ordered by priority.
 */
export function useActiveDestinations() {
  return useQuery({
    queryKey: ['destinations', 'active'],
    queryFn: api.getActiveDestinations,
    refetchInterval: 30000 // Refresh every 30s for real-time disk space updates
  });
}

/**
 * Hook to create a new destination.
 */
export function useCreateDestination() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (destination: CreateDestinationRequest) => api.createDestination(destination),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['destinations'] });
    }
  });
}

/**
 * Hook to update an existing destination.
 */
export function useUpdateDestination() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, destination }: { id: string; destination: UpdateDestinationRequest }) =>
      api.updateDestination(id, destination),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['destinations'] });
      queryClient.invalidateQueries({ queryKey: ['destinations', variables.id] });
    }
  });
}

/**
 * Hook to delete a destination.
 */
export function useDeleteDestination() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: api.deleteDestination,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['destinations'] });
    }
  });
}
