/**
 * Type definitions for File domain.
 */

export enum FileStatus {
  DISCOVERED = 'DISCOVERED',
  HASHED = 'HASHED',
  ANALYZED = 'ANALYZED',
  CLASSIFIED = 'CLASSIFIED',
  STAGED = 'STAGED',
  MIGRATED = 'MIGRATED'
}

export type ExifData = {
  cameraMake?: string;
  cameraModel?: string;
  dateTaken?: string;
  width?: number;
  height?: number;
  orientation?: number;
  latitude?: number;
  longitude?: number;
  altitude?: number;
  [key: string]: unknown;
};

export type ScannedFile = {
  id: string;
  sourceId: string;
  path: string;
  name: string;
  extension?: string;
  size: number;
  sha256?: string;
  modifiedAt?: string;
  createdAt?: string;
  accessedAt?: string;
  mimeType?: string;
  exif?: ExifData;
  status: FileStatus;
  isDuplicate: boolean;
  originalFileId?: string;
  scannedAt: string;
};

export type FileBatch = {
  sourceId: string;
  batchNumber: number;
  files: ScannedFile[];
};

export type FileBatchResult = {
  batchNumber: number;
  totalFiles: number;
  successCount: number;
  failureCount: number;
  successfulFileIds: string[];
  errors: FileError[];
};

export type FileError = {
  path: string;
  error: string;
};

export type FileFilters = {
  sourceId?: string;
  extension?: string;
  mimeType?: string;
  isDuplicate?: boolean;
  status?: FileStatus;
  minSize?: number;
  maxSize?: number;
  searchTerm?: string;
};
