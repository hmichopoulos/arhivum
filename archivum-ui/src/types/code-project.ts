/**
 * TypeScript types for code projects.
 */

export enum ProjectType {
  MAVEN = 'MAVEN',
  GRADLE = 'GRADLE',
  NPM = 'NPM',
  GO = 'GO',
  PYTHON = 'PYTHON',
  RUST = 'RUST',
  GIT = 'GIT',
  GENERIC = 'GENERIC'
}

export interface ProjectIdentity {
  type: ProjectType;
  name: string;
  version?: string;
  groupId?: string;
  gitRemote?: string;
  gitBranch?: string;
  gitCommit?: string;
  identifier: string;
}

export interface CodeProject {
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
}

export interface CodeProjectStats {
  total: number;
  byType: Record<ProjectType, number>;
  totalSourceFiles: number;
  totalSize: number;
}

export interface DuplicateGroup {
  contentHash: string;
  projects: CodeProject[];
  count: number;
}
