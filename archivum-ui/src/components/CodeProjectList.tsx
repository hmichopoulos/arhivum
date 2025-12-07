/**
 * List view for code projects with filtering and sorting.
 */

import { useState, useMemo } from 'react';
import { useCodeProjects } from '../hooks/useCodeProjects';
import { CodeProjectCard } from './CodeProjectCard';
import type { CodeProject } from '../types/code-project';
import { ProjectType } from '../types/code-project';

export function CodeProjectList() {
  const { data: projects, isLoading, error } = useCodeProjects();
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<ProjectType | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<'name' | 'size' | 'date'>('date');

  const filteredProjects = useMemo(() => {
    if (!projects) return [];

    let filtered = projects;

    // Apply search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(p =>
        p.identity.name.toLowerCase().includes(query) ||
        p.identity.identifier.toLowerCase().includes(query) ||
        p.rootPath.toLowerCase().includes(query)
      );
    }

    // Apply type filter
    if (typeFilter !== 'ALL') {
      filtered = filtered.filter(p => p.identity.type === typeFilter);
    }

    // Sort
    filtered = [...filtered].sort((a, b) => {
      switch (sortBy) {
        case 'name':
          return a.identity.name.localeCompare(b.identity.name);
        case 'size':
          return b.totalSizeBytes - a.totalSizeBytes;
        case 'date':
        default:
          return new Date(b.scannedAt).getTime() - new Date(a.scannedAt).getTime();
      }
    });

    return filtered;
  }, [projects, searchQuery, typeFilter, sortBy]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">Loading code projects...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-500">Error loading projects: {(error as Error).message}</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Code Projects</h1>
        <div className="text-sm text-gray-600">
          {filteredProjects.length} of {projects?.length || 0} projects
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 items-center">
        {/* Search */}
        <input
          type="text"
          placeholder="Search projects..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="flex-1 px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        />

        {/* Type Filter */}
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as ProjectType | 'ALL')}
          className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="ALL">All Types</option>
          {Object.values(ProjectType).map(type => (
            <option key={type} value={type}>{type}</option>
          ))}
        </select>

        {/* Sort */}
        <select
          value={sortBy}
          onChange={(e) => setSortBy(e.target.value as 'name' | 'size' | 'date')}
          className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="date">Sort by Date</option>
          <option value="name">Sort by Name</option>
          <option value="size">Sort by Size</option>
        </select>
      </div>

      {/* Projects Grid */}
      {filteredProjects.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          {searchQuery || typeFilter !== 'ALL' ? 'No projects match your filters' : 'No code projects found'}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredProjects.map(project => (
            <CodeProjectCard
              key={project.id}
              project={project}
              onClick={() => {/* Navigate to detail view */}}
            />
          ))}
        </div>
      )}
    </div>
  );
}
