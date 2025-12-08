/**
 * Main page component displaying all scanned sources.
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSources, useSourceStats } from '../hooks/useSources';
import { SourceCard } from '../components/SourceCard';
import { ScanStatus, SourceType } from '../types/source';

export function SourcesListPage() {
  const navigate = useNavigate();
  const { data: sources, isLoading, error } = useSources();
  const { data: stats } = useSourceStats();

  const [filterStatus, setFilterStatus] = useState<ScanStatus | 'ALL'>('ALL');
  const [filterType, setFilterType] = useState<SourceType | 'ALL'>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  // Filter sources
  const filteredSources = sources?.filter((source) => {
    const matchesStatus = filterStatus === 'ALL' || source.status === filterStatus;
    const matchesType = filterType === 'ALL' || source.type === filterType;
    const matchesSearch =
      searchTerm === '' ||
      source.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      source.rootPath.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesStatus && matchesType && matchesSearch;
  });

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading sources...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center text-red-600">
          <p className="text-xl font-semibold mb-2">Error loading sources</p>
          <p className="text-sm">{(error as Error).message}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Scanned Sources</h1>
          <p className="text-gray-600">Manage and browse all scanned disks and sources</p>
        </div>

        {/* Stats Cards */}
        {stats && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Total Sources</p>
              <p className="text-2xl font-bold text-gray-900">{stats.totalSources}</p>
            </div>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Completed Scans</p>
              <p className="text-2xl font-bold text-green-600">{stats.completedScans}</p>
            </div>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Total Files</p>
              <p className="text-2xl font-bold text-gray-900">
                {stats.totalFiles.toLocaleString()}
              </p>
            </div>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Total Size</p>
              <p className="text-2xl font-bold text-gray-900">
                {(stats.totalSize / 1024 / 1024 / 1024).toFixed(2)} GB
              </p>
            </div>
          </div>
        )}

        {/* Filters */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Search
              </label>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search by name or path..."
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Status
              </label>
              <select
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value as ScanStatus | 'ALL')}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="ALL">All Statuses</option>
                {Object.values(ScanStatus).map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Type</label>
              <select
                value={filterType}
                onChange={(e) => setFilterType(e.target.value as SourceType | 'ALL')}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="ALL">All Types</option>
                {Object.values(SourceType).map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Sources Grid */}
        {filteredSources && filteredSources.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {filteredSources.map((source) => (
              <SourceCard
                key={source.id}
                source={source}
                onClick={() => navigate(`/sources/${source.id}`)}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500 text-lg">No sources found</p>
            <p className="text-gray-400 text-sm mt-2">
              Try adjusting your filters or start a new scan
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
