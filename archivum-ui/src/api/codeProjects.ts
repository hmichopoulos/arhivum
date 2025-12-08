/**
 * API client for code projects.
 */

import type { CodeProject, CodeProjectStats } from '../types/codeProject';

const API_BASE = '/api/code-projects';

export async function getAllProjects(): Promise<CodeProject[]> {
  const response = await fetch(API_BASE);
  if (!response.ok) {
    throw new Error('Failed to fetch code projects');
  }
  return response.json();
}

export async function getProjectById(id: string): Promise<CodeProject> {
  const response = await fetch(`${API_BASE}/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch project ${id}`);
  }
  return response.json();
}

export async function getProjectsBySource(sourceId: string): Promise<CodeProject[]> {
  const response = await fetch(`${API_BASE}/source/${sourceId}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch projects for source ${sourceId}`);
  }
  return response.json();
}

export async function getDuplicateProjects(): Promise<Record<string, CodeProject[]>> {
  const response = await fetch(`${API_BASE}/duplicates`);
  if (!response.ok) {
    throw new Error('Failed to fetch duplicate projects');
  }
  return response.json();
}

export async function getProjectStats(): Promise<CodeProjectStats> {
  const response = await fetch(`${API_BASE}/stats`);
  if (!response.ok) {
    throw new Error('Failed to fetch project stats');
  }
  return response.json();
}

export async function deleteProject(id: string): Promise<void> {
  const response = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    throw new Error(`Failed to delete project ${id}`);
  }
}
