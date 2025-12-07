/**
 * API client for code project endpoints.
 */

import type { CodeProject, CodeProjectStats, DuplicateGroup } from '../types/code-project';
import { ProjectType } from '../types/code-project';

const API_BASE = '/api/code-projects';

/**
 * Fetch all code projects (ordered by most recent).
 */
export async function getAllProjects(): Promise<CodeProject[]> {
  const response = await fetch(API_BASE);
  if (!response.ok) {
    throw new Error('Failed to fetch code projects');
  }
  return response.json();
}

/**
 * Fetch code project by ID.
 */
export async function getProjectById(id: string): Promise<CodeProject> {
  const response = await fetch(`${API_BASE}/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch code project ${id}`);
  }
  return response.json();
}

/**
 * Fetch projects by source ID.
 */
export async function getProjectsBySource(sourceId: string): Promise<CodeProject[]> {
  const response = await fetch(`${API_BASE}/source/${sourceId}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch projects for source ${sourceId}`);
  }
  return response.json();
}

/**
 * Fetch projects by type.
 */
export async function getProjectsByType(type: ProjectType): Promise<CodeProject[]> {
  const response = await fetch(`${API_BASE}/type/${type}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch projects of type ${type}`);
  }
  return response.json();
}

/**
 * Fetch duplicate projects grouped by content hash.
 */
export async function getDuplicates(): Promise<DuplicateGroup[]> {
  const response = await fetch(`${API_BASE}/duplicates`);
  if (!response.ok) {
    throw new Error('Failed to fetch duplicate projects');
  }
  const data: Record<string, CodeProject[]> = await response.json();

  // Convert map to array of DuplicateGroup
  return Object.entries(data).map(([contentHash, projects]) => ({
    contentHash,
    projects,
    count: projects.length
  }));
}

/**
 * Fetch code project statistics.
 */
export async function getStatistics(): Promise<CodeProjectStats> {
  const response = await fetch(`${API_BASE}/stats`);
  if (!response.ok) {
    throw new Error('Failed to fetch project statistics');
  }
  return response.json();
}

/**
 * Delete code project by ID.
 */
export async function deleteProject(id: string): Promise<void> {
  const response = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    throw new Error(`Failed to delete code project ${id}`);
  }
}
