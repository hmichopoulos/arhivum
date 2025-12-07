/**
 * Card component for displaying a single code project.
 */

import type { CodeProject } from '../types/code-project';

interface CodeProjectCardProps {
  project: CodeProject;
  onClick?: () => void;
}

export function CodeProjectCard({ project, onClick }: CodeProjectCardProps) {
  const { identity, sourceFileCount, totalSizeBytes } = project;

  const formatSize = (bytes: number): string => {
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    return `${size.toFixed(1)} ${units[unitIndex]}`;
  };

  const getTypeColor = (type: string): string => {
    const colors: Record<string, string> = {
      MAVEN: 'bg-orange-100 text-orange-800',
      GRADLE: 'bg-green-100 text-green-800',
      NPM: 'bg-red-100 text-red-800',
      GO: 'bg-cyan-100 text-cyan-800',
      PYTHON: 'bg-blue-100 text-blue-800',
      RUST: 'bg-orange-100 text-orange-800',
      GIT: 'bg-purple-100 text-purple-800',
      GENERIC: 'bg-gray-100 text-gray-800'
    };
    return colors[type] || 'bg-gray-100 text-gray-800';
  };

  return (
    <div
      className="border rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer"
      onClick={onClick}
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-2">
        <div className="flex-1">
          <h3 className="font-semibold text-lg truncate" title={identity.identifier}>
            {identity.name}
          </h3>
          {identity.version && (
            <p className="text-sm text-gray-600">v{identity.version}</p>
          )}
        </div>
        <span className={`px-2 py-1 rounded text-xs font-medium ${getTypeColor(identity.type)}`}>
          {identity.type}
        </span>
      </div>

      {/* Group ID for Maven/Gradle */}
      {identity.groupId && (
        <p className="text-xs text-gray-500 mb-2">{identity.groupId}</p>
      )}

      {/* Git info */}
      {identity.gitRemote && (
        <div className="text-xs text-gray-600 mb-2">
          <p className="truncate" title={identity.gitRemote}>
            {identity.gitRemote}
          </p>
          <p>
            {identity.gitBranch && `Branch: ${identity.gitBranch}`}
            {identity.gitCommit && ` (${identity.gitCommit})`}
          </p>
        </div>
      )}

      {/* Stats */}
      <div className="flex gap-4 text-sm text-gray-600 mt-3">
        <div>
          <span className="font-medium">{sourceFileCount.toLocaleString()}</span>
          <span className="text-gray-500 ml-1">files</span>
        </div>
        <div>
          <span className="font-medium">{formatSize(totalSizeBytes)}</span>
        </div>
      </div>

      {/* Path */}
      <p className="text-xs text-gray-400 mt-2 truncate" title={project.rootPath}>
        {project.rootPath}
      </p>
    </div>
  );
}
