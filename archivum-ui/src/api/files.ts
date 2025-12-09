/**
 * API client for file endpoints.
 */

import type { ScannedFile, FileBatch, FileBatchResult } from '../types/file';
import type { Zone } from '../types/zone';

const API_BASE = '/api/files';

/**
 * Fetch file by ID.
 */
export async function getFileById(id: string): Promise<ScannedFile> {
  const response = await fetch(`${API_BASE}/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch file ${id}`);
  }
  return response.json();
}

/**
 * Ingest a batch of files (called by scanner).
 */
export async function ingestFileBatch(batch: FileBatch): Promise<FileBatchResult> {
  const response = await fetch(`${API_BASE}/batch`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(batch)
  });
  if (!response.ok) {
    throw new Error('Failed to ingest file batch');
  }
  return response.json();
}

/**
 * Query files with filters (pagination support).
 */
export async function queryFiles(params: {
  sourceId?: string;
  extension?: string;
  mimeType?: string;
  isDuplicate?: boolean;
  page?: number;
  size?: number;
}): Promise<ScannedFile[]> {
  const searchParams = new URLSearchParams();

  if (params.sourceId) searchParams.append('sourceId', params.sourceId);
  if (params.extension) searchParams.append('extension', params.extension);
  if (params.mimeType) searchParams.append('mimeType', params.mimeType);
  if (params.isDuplicate !== undefined) {
    searchParams.append('isDuplicate', params.isDuplicate.toString());
  }
  if (params.page !== undefined) searchParams.append('page', params.page.toString());
  if (params.size !== undefined) searchParams.append('size', params.size.toString());

  const response = await fetch(`${API_BASE}?${searchParams.toString()}`);
  if (!response.ok) {
    throw new Error('Failed to query files');
  }
  return response.json();
}

/**
 * Get all duplicate files for a given file ID.
 */
export async function getFileDuplicates(fileId: string): Promise<ScannedFile[]> {
  const response = await fetch(`${API_BASE}/${fileId}/duplicates`);
  if (!response.ok) {
    throw new Error(`Failed to fetch duplicates for file ${fileId}`);
  }
  return response.json();
}

/**
 * Update the zone classification for a file.
 */
export async function updateFileZone(fileId: string, zone: Zone): Promise<ScannedFile> {
  const response = await fetch(`${API_BASE}/${fileId}/zone`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ zone })
  });
  if (!response.ok) {
    throw new Error(`Failed to update zone for file ${fileId}`);
  }
  return response.json();
}
