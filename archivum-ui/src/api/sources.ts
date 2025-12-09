/**
 * API client for source endpoints.
 */

import type { Source, SourceStats } from '../types/source';
import type { FolderNode } from '../types/folder';
import { Zone } from '../types/zone';

const API_BASE = '/api/sources';

/**
 * Fetch all sources.
 */
export async function getAllSources(): Promise<Source[]> {
  const response = await fetch(API_BASE);
  if (!response.ok) {
    throw new Error('Failed to fetch sources');
  }
  return response.json();
}

/**
 * Fetch source by ID.
 */
export async function getSourceById(id: string): Promise<Source> {
  const response = await fetch(`${API_BASE}/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch source ${id}`);
  }
  return response.json();
}

/**
 * Create a new source (called by scanner).
 */
export async function createSource(source: Partial<Source>): Promise<Source> {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(source)
  });
  if (!response.ok) {
    throw new Error('Failed to create source');
  }
  return response.json();
}

/**
 * Mark scan as complete.
 */
export async function completeScan(
  sourceId: string,
  completedAt: string
): Promise<Source> {
  const response = await fetch(`${API_BASE}/${sourceId}/complete`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ completedAt })
  });
  if (!response.ok) {
    throw new Error(`Failed to complete scan for source ${sourceId}`);
  }
  return response.json();
}

/**
 * Delete source and all its files.
 */
export async function deleteSource(id: string): Promise<void> {
  const response = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    throw new Error(`Failed to delete source ${id}`);
  }
}

/**
 * Get source statistics.
 */
export async function getSourceStatistics(): Promise<SourceStats> {
  const response = await fetch(`${API_BASE}/stats`);
  if (!response.ok) {
    throw new Error('Failed to fetch source statistics');
  }
  return response.json();
}

/**
 * Get folder tree for a source.
 */
export async function getSourceTree(sourceId: string): Promise<FolderNode> {
  const response = await fetch(`${API_BASE}/${sourceId}/tree`);
  if (!response.ok) {
    throw new Error(`Failed to fetch tree for source ${sourceId}`);
  }
  return response.json();
}

/**
 * Update the zone classification for a folder.
 */
export async function updateFolderZone(sourceId: string, folderPath: string, zone: Zone): Promise<void> {
  const response = await fetch(`${API_BASE}/${sourceId}/folders/zone`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ folderPath, zone })
  });
  if (!response.ok) {
    throw new Error(`Failed to update zone for folder ${folderPath}`);
  }
}
