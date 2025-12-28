/**
 * Component displaying statistics about code projects.
 */

import { useProjectStats } from '../hooks/useCodeProjects';
import { ProjectType } from '../types/codeProject';

export function CodeProjectStats() {
  const { data: stats, isLoading, error } = useProjectStats();

  const getProjectTypeIcon = (type: ProjectType): string => {
    switch (type) {
      case ProjectType.MAVEN: return '☕';
      case ProjectType.GRADLE: return '🐘';
      case ProjectType.NPM: return '📦';
      case ProjectType.PYTHON: return '🐍';
      case ProjectType.GO: return '🐹';
      case ProjectType.RUST: return '🦀';
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
      case ProjectType.GENERIC: return 'bg-purple-100 text-purple-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  const formatBytes = (bytes: number): string => {
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  if (isLoading) {
    return (
      <div className="text-center py-12">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <p className="text-gray-600">Loading statistics...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 text-red-600">
        <p className="text-xl font-semibold">Error loading statistics</p>
        <p className="text-sm mt-2">{(error as Error).message}</p>
      </div>
    );
  }

  if (!stats) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500 text-lg">No statistics available</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard
          title="Total Projects"
          value={stats.total.toLocaleString()}
          icon="📊"
          color="bg-blue-50"
        />
        <StatCard
          title="Total Source Files"
          value={stats.totalSourceFiles.toLocaleString()}
          icon="📄"
          color="bg-green-50"
        />
        <StatCard
          title="Total Size"
          value={formatBytes(stats.totalSize)}
          icon="💾"
          color="bg-purple-50"
        />
      </div>

      {/* Projects by Type */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <h2 className="text-xl font-bold text-gray-900 mb-6">Projects by Type</h2>
        <div className="space-y-3">
          {Object.entries(stats.byType)
            .filter(([_, count]) => count > 0)
            .sort(([_, a], [__, b]) => b - a)
            .map(([type, count]) => (
              <ProjectTypeRow
                key={type}
                type={type as ProjectType}
                count={count}
                total={stats.total}
                getIcon={getProjectTypeIcon}
                getColor={getProjectTypeColor}
              />
            ))}
        </div>
      </div>
    </div>
  );
}

type StatCardProps = {
  title: string;
  value: string;
  icon: string;
  color: string;
};

function StatCard({ title, value, icon, color }: StatCardProps) {
  return (
    <div className={`${color} rounded-lg p-6`}>
      <div className="flex items-center gap-3">
        <span className="text-3xl">{icon}</span>
        <div>
          <p className="text-sm text-gray-600 font-medium">{title}</p>
          <p className="text-2xl font-bold text-gray-900">{value}</p>
        </div>
      </div>
    </div>
  );
}

type ProjectTypeRowProps = {
  type: ProjectType;
  count: number;
  total: number;
  getIcon: (type: ProjectType) => string;
  getColor: (type: ProjectType) => string;
};

function ProjectTypeRow({ type, count, total, getIcon, getColor }: ProjectTypeRowProps) {
  const percentage = ((count / total) * 100).toFixed(1);

  return (
    <div className="flex items-center gap-4">
      <span className="text-2xl">{getIcon(type)}</span>
      <div className="flex-1">
        <div className="flex items-center justify-between mb-1">
          <span className={`px-2 py-1 rounded text-xs font-medium ${getColor(type)}`}>
            {type}
          </span>
          <span className="text-sm font-medium text-gray-700">
            {count} ({percentage}%)
          </span>
        </div>
        <div className="w-full bg-gray-200 rounded-full h-2">
          <div
            className="bg-blue-600 h-2 rounded-full transition-all"
            style={{ width: `${percentage}%` }}
          />
        </div>
      </div>
    </div>
  );
}
