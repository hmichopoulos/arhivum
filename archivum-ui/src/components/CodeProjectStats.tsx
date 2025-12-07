/**
 * Statistics dashboard for code projects.
 */

import { useCodeProjectStats } from '../hooks/useCodeProjects';
import { ProjectType } from '../types/code-project';

export function CodeProjectStats() {
  const { data: stats, isLoading, error } = useCodeProjectStats();

  const formatSize = (bytes: number): string => {
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    return `${size.toFixed(2)} ${units[unitIndex]}`;
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">Loading statistics...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-500">Error loading stats: {(error as Error).message}</div>
      </div>
    );
  }

  if (!stats) return null;

  return (
    <div className="space-y-6">
      {/* Header */}
      <h1 className="text-2xl font-bold">Code Project Statistics</h1>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white border rounded-lg p-6">
          <div className="text-sm text-gray-600 mb-1">Total Projects</div>
          <div className="text-3xl font-bold text-gray-900">{stats.total.toLocaleString()}</div>
        </div>

        <div className="bg-white border rounded-lg p-6">
          <div className="text-sm text-gray-600 mb-1">Source Files</div>
          <div className="text-3xl font-bold text-gray-900">{stats.totalSourceFiles.toLocaleString()}</div>
        </div>

        <div className="bg-white border rounded-lg p-6">
          <div className="text-sm text-gray-600 mb-1">Total Size</div>
          <div className="text-3xl font-bold text-gray-900">{formatSize(stats.totalSize)}</div>
        </div>

        <div className="bg-white border rounded-lg p-6">
          <div className="text-sm text-gray-600 mb-1">Project Types</div>
          <div className="text-3xl font-bold text-gray-900">{Object.keys(stats.byType).length}</div>
        </div>
      </div>

      {/* Projects by Type */}
      <div className="bg-white border rounded-lg p-6">
        <h2 className="text-lg font-semibold mb-4">Projects by Type</h2>
        <div className="space-y-3">
          {Object.entries(stats.byType)
            .sort(([, a], [, b]) => (b as number) - (a as number))
            .map(([type, count]) => {
              const percentage = ((count as number) / stats.total) * 100;
              return (
                <div key={type}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="font-medium">{type}</span>
                    <span className="text-gray-600">
                      {count as number} ({percentage.toFixed(1)}%)
                    </span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-blue-600 h-2 rounded-full transition-all"
                      style={{ width: `${percentage}%` }}
                    />
                  </div>
                </div>
              );
            })}
        </div>
      </div>
    </div>
  );
}
