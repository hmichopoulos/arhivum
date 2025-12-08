/**
 * Types for code projects.
 */

export enum ProjectType {
  MAVEN = 'MAVEN',
  GRADLE = 'GRADLE',
  NPM = 'NPM',
  PYTHON = 'PYTHON',
  GO = 'GO',
  RUST = 'RUST',
  GIT = 'GIT',
  GENERIC = 'GENERIC'
}

export type ProjectIdentity = {
  type: ProjectType;
  name: string;
  version?: string;
  groupId?: string;
  identifier: string;
};

export type CodeProject = {
  id: string;
  sourceId: string;
  rootPath: string;
  identity: ProjectIdentity;
  contentHash: string;
  sourceFileCount: number;
  totalFileCount: number;
  totalSizeBytes: number;
  scannedAt: string;
  archivePath?: string;
};

export type CodeProjectStats = {
  total: number;
  byType: Record<ProjectType, number>;
  totalSourceFiles: number;
  totalSize: number;
};
