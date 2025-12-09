/**
 * Tree view component for displaying hierarchical folder structure.
 */

import { useState } from 'react';
import { FolderNode, NodeType } from '../types/folder';
import { CodeProject, ProjectType } from '../types/codeProject';
import { ScannedFile } from '../types/file';
import { Zone } from '../types/zone';
import { getFileDuplicates } from '../api/files';
import { DuplicateLocationsModal } from './DuplicateLocationsModal';
import { ProjectDetailsModal } from './ProjectDetailsModal';
import { ZoneSelector } from './ZoneSelector';
import { getFileIcon, getProjectTypeIcon, FOLDER_ICON, OPEN_FOLDER_ICON } from '../utils/fileIcons';

type TreeViewProps = {
  tree: FolderNode;
  sourceId: string;
  codeProjects?: CodeProject[];
  onFileClick?: (fileId: string) => void;
  onTreeUpdate?: () => void;
};

export function TreeView({ tree, sourceId, codeProjects = [], onFileClick, onTreeUpdate }: TreeViewProps) {
  const [modalState, setModalState] = useState<{
    isOpen: boolean;
    fileId: string | null;
    duplicates: ScannedFile[];
    isLoading: boolean;
    error: string | null;
  }>({
    isOpen: false,
    fileId: null,
    duplicates: [],
    isLoading: false,
    error: null
  });

  const [projectModalState, setProjectModalState] = useState<{
    isOpen: boolean;
    project: CodeProject | null;
  }>({
    isOpen: false,
    project: null
  });

  const handleDuplicateClick = async (fileId: string) => {
    setModalState({ isOpen: true, fileId, duplicates: [], isLoading: true, error: null });

    try {
      const duplicates = await getFileDuplicates(fileId);
      setModalState((prev) => ({
        ...prev,
        duplicates,
        isLoading: false,
        error: null
      }));
    } catch (error) {
      console.error('Failed to fetch duplicates:', error);
      const errorMessage = error instanceof Error
        ? error.message
        : 'Failed to load duplicate file locations. Please try again.';
      setModalState((prev) => ({
        ...prev,
        isLoading: false,
        error: errorMessage
      }));
    }
  };

  const handleCloseModal = () => {
    setModalState({
      isOpen: false,
      fileId: null,
      duplicates: [],
      isLoading: false,
      error: null
    });
  };

  const handleProjectClick = (project: CodeProject) => {
    setProjectModalState({
      isOpen: true,
      project
    });
  };

  const handleCloseProjectModal = () => {
    setProjectModalState({
      isOpen: false,
      project: null
    });
  };

  const getProjectTypeColor = (type: ProjectType): string => {
    switch (type) {
      case ProjectType.MAVEN: return 'bg-orange-100 text-orange-800';
      case ProjectType.GRADLE: return 'bg-green-100 text-green-800';
      case ProjectType.NPM: return 'bg-red-100 text-red-800';
      case ProjectType.PYTHON: return 'bg-blue-100 text-blue-800';
      case ProjectType.GO: return 'bg-cyan-100 text-cyan-800';
      case ProjectType.RUST: return 'bg-amber-100 text-amber-800';
      case ProjectType.GIT: return 'bg-gray-100 text-gray-800';
      case ProjectType.GENERIC: return 'bg-purple-100 text-purple-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <>
      <div className="font-mono text-sm">
        {tree.children && tree.children.length > 0 ? (
          tree.children.map((node, index) => (
            <TreeNode
              key={node.path || index}
              node={node}
              level={0}
              sourceId={sourceId}
              codeProjects={codeProjects}
              onFileClick={onFileClick}
              onDuplicateClick={handleDuplicateClick}
              onProjectClick={handleProjectClick}
              getProjectTypeColor={getProjectTypeColor}
              onTreeUpdate={onTreeUpdate}
            />
          ))
        ) : (
          <div className="text-gray-500 py-4 text-center">No files in this source</div>
        )}
      </div>

      {modalState.fileId && (
        <DuplicateLocationsModal
          isOpen={modalState.isOpen}
          onClose={handleCloseModal}
          duplicates={modalState.duplicates}
          currentFileId={modalState.fileId}
          isLoading={modalState.isLoading}
          error={modalState.error}
        />
      )}

      <ProjectDetailsModal
        isOpen={projectModalState.isOpen}
        onClose={handleCloseProjectModal}
        project={projectModalState.project}
        getIcon={getProjectTypeIcon}
        getColor={getProjectTypeColor}
      />
    </>
  );
}

type TreeNodeProps = {
  node: FolderNode;
  level: number;
  sourceId: string;
  codeProjects: CodeProject[];
  onFileClick?: (fileId: string) => void;
  onDuplicateClick: (fileId: string) => void;
  onProjectClick: (project: CodeProject) => void;
  getProjectTypeColor: (type: ProjectType) => string;
  onTreeUpdate?: () => void;
};

function TreeNode({ node, level, sourceId, codeProjects, onFileClick, onDuplicateClick, onProjectClick, getProjectTypeColor, onTreeUpdate }: TreeNodeProps) {
  const [isExpanded, setIsExpanded] = useState(level < 2); // Auto-expand first 2 levels

  const formatBytes = (bytes?: number): string => {
    if (!bytes) return '';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  };

  // Check if this folder is a code project
  const codeProject = node.type === NodeType.FOLDER
    ? codeProjects.find(project => project.rootPath === node.path)
    : undefined;

  const indent = level * 20;

  if (node.type === NodeType.FILE) {
    return (
      <div
        className={`
          flex items-center gap-2 px-2 py-1 hover:bg-gray-100 rounded
          ${node.isDuplicate ? 'bg-yellow-50' : ''}
        `}
        style={{ paddingLeft: `${indent + 20}px` }}
      >
        <span>{getFileIcon(node.extension)}</span>
        <span
          className="flex-1 text-gray-900 cursor-pointer"
          onClick={() => node.fileId && onFileClick?.(node.fileId)}
        >
          {node.name}
        </span>
        {node.fileId && (
          <ZoneSelector
            fileId={node.fileId}
            currentZone={node.zone || Zone.UNKNOWN}
            isInherited={node.isInherited}
            onZoneChange={() => {
              onTreeUpdate?.();
            }}
          />
        )}
        {node.isDuplicate && node.fileId && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onDuplicateClick(node.fileId!);
            }}
            className="text-xs bg-yellow-200 text-yellow-800 px-2 py-0.5 rounded hover:bg-yellow-300 transition-colors cursor-pointer"
            title="Click to view all duplicate locations"
          >
            duplicate
          </button>
        )}
        <span className="text-xs text-gray-500">{formatBytes(node.size)}</span>
      </div>
    );
  }

  // Folder node
  return (
    <div>
      <div
        className="flex items-center gap-2 px-2 py-1 hover:bg-gray-50 rounded cursor-pointer"
        style={{ paddingLeft: `${indent}px` }}
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <span className="text-gray-500 w-4">
          {node.children.length > 0 ? (isExpanded ? '▼' : '▶') : '  '}
        </span>
        <span>{isExpanded ? OPEN_FOLDER_ICON : FOLDER_ICON}</span>
        <span className="flex-1 font-semibold text-gray-900">{node.name}</span>
        <ZoneSelector
          sourceId={sourceId}
          folderPath={node.path}
          currentZone={node.zone || Zone.UNKNOWN}
          isInherited={node.isInherited}
          onZoneChange={() => {
            onTreeUpdate?.(); // Refetch tree to get updated zones
          }}
        />
        {codeProject && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onProjectClick(codeProject);
            }}
            className={`flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium cursor-pointer hover:opacity-80 transition-opacity ${getProjectTypeColor(
              codeProject.identity.type
            )}`}
            title={`Click to view ${codeProject.identity.type} project details: ${codeProject.identity.name}`}
          >
            <span>{getProjectTypeIcon(codeProject.identity.type)}</span>
            <span>{codeProject.identity.type}</span>
          </button>
        )}
        {node.fileCount !== undefined && node.fileCount > 0 && (
          <span className="text-xs text-gray-500">
            {node.fileCount} {node.fileCount === 1 ? 'file' : 'files'}
          </span>
        )}
        {node.totalSize !== undefined && node.totalSize > 0 && (
          <span className="text-xs text-gray-500">{formatBytes(node.totalSize)}</span>
        )}
      </div>
      {isExpanded && node.children && (
        <div>
          {node.children.map((child, index) => (
            <TreeNode
              key={child.path || index}
              node={child}
              level={level + 1}
              sourceId={sourceId}
              codeProjects={codeProjects}
              onFileClick={onFileClick}
              onDuplicateClick={onDuplicateClick}
              onProjectClick={onProjectClick}
              getProjectTypeColor={getProjectTypeColor}
              onTreeUpdate={onTreeUpdate}
            />
          ))}
        </div>
      )}
    </div>
  );
}
