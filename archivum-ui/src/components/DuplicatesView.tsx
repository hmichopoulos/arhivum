/**
 * Component displaying duplicate code projects grouped by content hash.
 */

import { useDuplicateProjects } from '../hooks/useCodeProjects';
import { ProjectType, type CodeProject } from '../types/codeProject';

export function DuplicatesView() {
  const { data: duplicateGroups, isLoading, error } = useDuplicateProjects();

  const getProjectTypeIcon = (type: ProjectType): string => {
    switch (type) {
      case ProjectType.MAVEN: return '☕';
      case ProjectType.GRADLE: return '🐘';
      case ProjectType.NPM: return '📦';
      case ProjectType.PYTHON: return '🐍';
      case ProjectType.GO: return '🐹';
      case ProjectType.RUST: return '🦀';
      case ProjectType.GIT: return '🔧';
      case ProjectType.GENERIC: return '📁';
      default: return '💻';
    }
  };

  if (isLoading) {
    return (
      <div className="text-center py-12">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <p className="text-gray-600">Loading duplicates...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 text-red-600">
        <p className="text-xl font-semibold">Error loading duplicates</p>
        <p className="text-sm mt-2">{(error as Error).message}</p>
      </div>
    );
  }

  if (!duplicateGroups || Object.keys(duplicateGroups).length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500 text-lg">No duplicate projects found</p>
        <p className="text-gray-400 text-sm mt-2">
          All detected code projects are unique
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {Object.entries(duplicateGroups).map(([hash, projects]) => (
        <DuplicateGroup key={hash} hash={hash} projects={projects} getIcon={getProjectTypeIcon} />
      ))}
    </div>
  );
}

type DuplicateGroupProps = {
  hash: string;
  projects: CodeProject[];
  getIcon: (type: ProjectType) => string;
};

function DuplicateGroup({ hash, projects, getIcon }: DuplicateGroupProps) {
  const primaryProject = projects[0];

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
      <div className="flex items-center gap-3 mb-4">
        <span className="text-2xl">{getIcon(primaryProject.identity.type)}</span>
        <div className="flex-1">
          <h3 className="font-semibold text-gray-900">
            {primaryProject.identity.name}
            {primaryProject.identity.version && (
              <span className="text-sm text-gray-500 ml-2">v{primaryProject.identity.version}</span>
            )}
          </h3>
          <p className="text-sm text-gray-500">
            Content hash: <code className="text-xs bg-gray-100 px-1 rounded">{hash.substring(0, 16)}...</code>
          </p>
        </div>
        <span className="px-3 py-1 bg-amber-100 text-amber-800 rounded-full text-sm font-medium">
          {projects.length} copies
        </span>
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium text-gray-700 mb-2">Found in:</p>
        {projects.map((project, index) => (
          <div
            key={project.id}
            className="flex items-center gap-2 text-sm text-gray-600 p-2 bg-gray-50 rounded"
          >
            <span className="text-gray-400 font-mono">{index + 1}.</span>
            <span className="truncate flex-1" title={project.rootPath}>
              📂 {project.rootPath}
            </span>
            <span className="text-xs text-gray-500 whitespace-nowrap">
              {new Date(project.scannedAt).toLocaleDateString()}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
