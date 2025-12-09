/**
 * Component displaying list of all code projects.
 */

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useCodeProjects } from '../hooks/useCodeProjects';
import { ProjectType, type CodeProject } from '../types/codeProject';
import { ProjectDetailsModal } from './ProjectDetailsModal';

export function CodeProjectList() {
  const { data: projects, isLoading, error } = useCodeProjects();
  const [selectedProject, setSelectedProject] = useState<CodeProject | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

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

  const formatBytes = (bytes: number): string => {
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  };

  if (isLoading) {
    return (
      <div className="text-center py-12">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <p className="text-gray-600">Loading projects...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 text-red-600">
        <p className="text-xl font-semibold">Error loading projects</p>
        <p className="text-sm mt-2">{(error as Error).message}</p>
      </div>
    );
  }

  if (!projects || projects.length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500 text-lg">No code projects found</p>
        <p className="text-gray-400 text-sm mt-2">
          Scan directories containing code to detect projects
        </p>
      </div>
    );
  }

  const handleProjectClick = (project: CodeProject) => {
    setSelectedProject(project);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedProject(null);
  };

  const filteredProjects = projects?.filter(project => {
    if (!searchTerm) return true;

    const searchLower = searchTerm.toLowerCase();
    const identity = project.identity;

    return (
      identity.name.toLowerCase().includes(searchLower) ||
      identity.type.toLowerCase().includes(searchLower) ||
      project.rootPath.toLowerCase().includes(searchLower) ||
      identity.version?.toLowerCase().includes(searchLower) ||
      identity.groupId?.toLowerCase().includes(searchLower) ||
      identity.identifier.toLowerCase().includes(searchLower)
    );
  }) || [];

  return (
    <>
      {/* Search input */}
      <div className="mb-6">
        <input
          type="text"
          placeholder="Search projects by name, type, path, version..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        {searchTerm && (
          <p className="text-sm text-gray-600 mt-2">
            Found {filteredProjects.length} project{filteredProjects.length !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {filteredProjects.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-gray-500 text-lg">No projects match your search</p>
          <p className="text-gray-400 text-sm mt-2">
            Try a different search term
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredProjects.map((project) => (
            <ProjectCard key={project.id} project={project}
              getIcon={getProjectTypeIcon}
              getColor={getProjectTypeColor}
              formatBytes={formatBytes}
              onProjectClick={handleProjectClick} />
          ))}
        </div>
      )}

      <ProjectDetailsModal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        project={selectedProject}
        getIcon={getProjectTypeIcon}
        getColor={getProjectTypeColor}
      />
    </>
  );
}

type ProjectCardProps = {
  project: CodeProject;
  getIcon: (type: ProjectType) => string;
  getColor: (type: ProjectType) => string;
  formatBytes: (bytes: number) => string;
  onProjectClick: (project: CodeProject) => void;
};

function ProjectCard({ project, getIcon, getColor, formatBytes, onProjectClick }: ProjectCardProps) {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 hover:shadow-md transition-shadow">
      <div className="flex items-start gap-3 mb-3">
        <span className="text-2xl">{getIcon(project.identity.type)}</span>
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-gray-900 truncate">
            {project.identity.name}
          </h3>
          {project.identity.version && (
            <p className="text-sm text-gray-500">v{project.identity.version}</p>
          )}
        </div>
        <button
          onClick={() => onProjectClick(project)}
          className={`px-2 py-1 rounded text-xs font-medium cursor-pointer hover:opacity-80 transition-opacity ${getColor(project.identity.type)}`}
          title="Click to view project details"
        >
          {project.identity.type}
        </button>
      </div>

      <div className="text-sm text-gray-600 mb-3">
        <p className="truncate" title={project.rootPath}>
          📂 {project.rootPath}
        </p>
        <Link
          to={`/sources/${project.sourceId}`}
          className="text-xs text-blue-600 hover:text-blue-800 hover:underline inline-flex items-center gap-1 mt-1"
          onClick={(e) => e.stopPropagation()}
        >
          View Source →
        </Link>
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm">
        <div>
          <p className="text-gray-500">Source Files</p>
          <p className="font-medium text-gray-900">{project.sourceFileCount}</p>
        </div>
        <div>
          <p className="text-gray-500">Total Size</p>
          <p className="font-medium text-gray-900">{formatBytes(project.totalSizeBytes)}</p>
        </div>
      </div>
    </div>
  );
}
