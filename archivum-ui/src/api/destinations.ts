/**
 * API client for destination endpoints.
 */

import type { Destination, CreateDestinationRequest, UpdateDestinationRequest } from '../types/destination';

const API_BASE = '/api/destinations';

/**
 * Fetch all destinations with real-time disk space information.
 */
export async function getAllDestinations(): Promise<Destination[]> {
  const response = await fetch(API_BASE);
  if (!response.ok) {
    throw new Error('Failed to fetch destinations');
  }
  return response.json();
}

/**
 * Fetch destination by ID with real-time disk space information.
 */
export async function getDestinationById(id: string): Promise<Destination> {
  const response = await fetch(`${API_BASE}/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch destination ${id}`);
  }
  return response.json();
}

/**
 * Fetch all active destinations ordered by priority.
 */
export async function getActiveDestinations(): Promise<Destination[]> {
  const response = await fetch(`${API_BASE}/active`);
  if (!response.ok) {
    throw new Error('Failed to fetch active destinations');
  }
  return response.json();
}

/**
 * Create a new destination.
 */
export async function createDestination(destination: CreateDestinationRequest): Promise<Destination> {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(destination)
  });
  if (!response.ok) {
    throw new Error('Failed to create destination');
  }
  return response.json();
}

/**
 * Update an existing destination.
 */
export async function updateDestination(id: string, destination: UpdateDestinationRequest): Promise<Destination> {
  const response = await fetch(`${API_BASE}/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(destination)
  });
  if (!response.ok) {
    throw new Error(`Failed to update destination ${id}`);
  }
  return response.json();
}

/**
 * Delete a destination.
 */
export async function deleteDestination(id: string): Promise<void> {
  const response = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    throw new Error(`Failed to delete destination ${id}`);
  }
}
