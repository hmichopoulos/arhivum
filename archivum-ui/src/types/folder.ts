/**
 * Types for folder tree structure.
 */

import { Zone } from './zone';

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
  zone?: Zone;
  children: FolderNode[];
  fileCount?: number;
  totalSize?: number;
};
