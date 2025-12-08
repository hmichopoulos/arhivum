/**
 * Type definitions for Source domain.
 */

export enum SourceType {
  DISK = 'DISK',
  CLOUD = 'CLOUD',
  NETWORK = 'NETWORK'
}

export enum ScanStatus {
  PENDING = 'PENDING',
  SCANNING = 'SCANNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  POSTPONED = 'POSTPONED'
}

export type PhysicalId = {
  diskUuid?: string;
  partitionUuid?: string;
  volumeLabel?: string;
  serialNumber?: string;
  mountPoint?: string;
  filesystemType?: string;
  capacity?: number;
  usedSpace?: number;
  physicalLabel?: string;
  notes?: string;
};

export type Source = {
  id: string;
  name: string;
  type: SourceType;
  rootPath: string;
  physicalId?: PhysicalId;
  status: ScanStatus;
  postponed: boolean;
  totalFiles: number;
  totalSize: number;
  processedFiles: number;
  processedSize: number;
  scanStartedAt?: string;
  scanCompletedAt?: string;
  createdAt: string;
  updatedAt: string;
  parentSourceId?: string;
};

export type SourceStats = {
  totalSources: number;
  completedScans: number;
  totalFiles: number;
  totalSize: number;
  byType: Record<SourceType, number>;
  byStatus: Record<ScanStatus, number>;
};
