/**
 * View for displaying duplicate code projects.
 */

import { useCodeProjectDuplicates } from '../hooks/useCodeProjects';
import { CodeProjectCard } from './CodeProjectCard';

export function DuplicatesView() {
  const { data: duplicates, isLoading, error } = useCodeProjectDuplicates();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">Loading duplicates...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-500">Error loading duplicates: {(error as Error).message}</div>
      </div>
    );
  }

  if (!duplicates || duplicates.length === 0) {
    return (
      <div className="text-center py-12">
        <div className="text-gray-500 text-lg">No duplicate projects found</div>
        <div className="text-gray-400 text-sm mt-2">All code projects are unique!</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Duplicate Code Projects</h1>
        <div className="text-sm text-gray-600">
          {duplicates.length} duplicate group(s)
        </div>
      </div>

      {/* Duplicate Groups */}
      {duplicates.map((group, index) => (
        <div key={group.contentHash} className="border rounded-lg p-6 bg-gray-50">
          {/* Group Header */}
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Group {index + 1}: {group.projects[0].identity.identifier}
            </h2>
            <div className="flex gap-4 text-sm text-gray-600 mt-1">
              <span>{group.count} copies</span>
              <span className="text-gray-400">•</span>
              <span className="font-mono text-xs">{group.contentHash.substring(0, 16)}...</span>
            </div>
          </div>

          {/* Info Banner */}
          <div className="bg-yellow-50 border border-yellow-200 rounded p-3 mb-4">
            <div className="flex items-start gap-2">
              <span className="text-yellow-600">⚠️</span>
              <div className="text-sm text-yellow-800">
                <p className="font-medium">Exact duplicates detected</p>
                <p className="text-yellow-700 mt-1">
                  These projects have identical content (same content hash).
                  Consider keeping only one copy.
                </p>
              </div>
            </div>
          </div>

          {/* Projects in Group */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {group.projects.map(project => (
              <CodeProjectCard key={project.id} project={project} />
            ))}
          </div>

          {/* Actions */}
          <div className="mt-4 flex gap-2 justify-end">
            <button className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900">
              Keep All
            </button>
            <button className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700">
              Review & Resolve
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
