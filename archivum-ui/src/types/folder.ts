/**
 * Types for folder tree structure.
 */

export enum NodeType {
  FOLDER = 'FOLDER',
  FILE = 'FILE'
}

export type FolderNode = {
  name: string;
  path: string;
  type: NodeType;
  fileId?: string;
  size?: number;
  extension?: string;
  isDuplicate?: boolean;
  children: FolderNode[];
  fileCount?: number;
  totalSize?: number;
};
