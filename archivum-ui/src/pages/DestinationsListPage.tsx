/**
 * Main page component displaying all migration destinations with real-time disk space.
 */

import { useState } from 'react';
import { useDestinations, useDeleteDestination, useUpdateDestination, useCreateDestination } from '../hooks/useDestinations';
import { DestinationCard } from '../components/DestinationCard';
import { DestinationForm } from '../components/DestinationForm';
import { DestinationType } from '../types/destination';
import type { CreateDestinationRequest } from '../types/destination';

export function DestinationsListPage() {
  const { data: destinations, isLoading, error } = useDestinations();
  const deleteDestination = useDeleteDestination();
  const updateDestination = useUpdateDestination();
  const createDestination = useCreateDestination();

  const [filterType, setFilterType] = useState<DestinationType | 'ALL'>('ALL');
  const [filterActive, setFilterActive] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [showCreateForm, setShowCreateForm] = useState(false);

  const handleDelete = (destinationId: string) => {
    deleteDestination.mutate(destinationId, {
      onSuccess: () => {
        // Query will be automatically invalidated by the mutation hook
      },
      onError: (error) => {
        alert(`Failed to delete destination: ${error.message}`);
      }
    });
  };

  const handleToggleActive = (destinationId: string, isActive: boolean) => {
    updateDestination.mutate(
      { id: destinationId, destination: { isActive } },
      {
        onError: (error) => {
          alert(`Failed to update destination: ${error.message}`);
        }
      }
    );
  };

  const handleCreate = (destination: CreateDestinationRequest) => {
    createDestination.mutate(destination, {
      onSuccess: () => {
        setShowCreateForm(false);
      },
      onError: (error) => {
        alert(`Failed to create destination: ${error.message}`);
      }
    });
  };

  // Filter destinations
  const filteredDestinations = destinations?.filter((destination) => {
    const matchesType = filterType === 'ALL' || destination.destinationType === filterType;
    const matchesActive =
      filterActive === 'ALL' ||
      (filterActive === 'ACTIVE' && destination.isActive) ||
      (filterActive === 'INACTIVE' && !destination.isActive);
    const matchesSearch =
      searchTerm === '' ||
      destination.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      destination.mountedPath.toLowerCase().includes(searchTerm.toLowerCase()) ||
      destination.physicalIdentifier?.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesType && matchesActive && matchesSearch;
  });

  // Calculate stats
  const stats = destinations
    ? {
        total: destinations.length,
        active: destinations.filter((d) => d.isActive).length,
        totalCapacity: destinations.reduce((sum, d) => sum + (d.totalCapacityBytes || 0), 0),
        totalUsed: destinations.reduce((sum, d) => sum + (d.usedSpaceBytes || 0), 0),
        totalAvailable: destinations.reduce((sum, d) => sum + (d.availableSpaceBytes || 0), 0)
      }
    : null;

  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading destinations...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center text-red-600">
          <p className="text-xl font-semibold mb-2">Error loading destinations</p>
          <p className="text-sm">{(error as Error).message}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Migration Destinations</h1>
            <p className="text-gray-600">
              Manage migration targets with real-time disk space monitoring
            </p>
          </div>
          <button
            onClick={() => setShowCreateForm(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="h-5 w-5"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 4v16m8-8H4"
              />
            </svg>
            <span>New Destination</span>
          </button>
        </div>

        {/* Stats Cards */}
        {stats && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Total Destinations</p>
              <p className="text-2xl font-bold text-gray-900">{stats.total}</p>
            </div>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Active</p>
              <p className="text-2xl font-bold text-green-600">{stats.active}</p>
            </div>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Total Capacity</p>
              <p className="text-2xl font-bold text-gray-900">{formatBytes(stats.totalCapacity)}</p>
            </div>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
              <p className="text-sm text-gray-500 mb-1">Available Space</p>
              <p className="text-2xl font-bold text-blue-600">{formatBytes(stats.totalAvailable)}</p>
            </div>
          </div>
        )}

        {/* Filters */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Search</label>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search by name, path, or hardware ID..."
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Type</label>
              <select
                value={filterType}
                onChange={(e) => setFilterType(e.target.value as DestinationType | 'ALL')}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="ALL">All Types</option>
                {Object.values(DestinationType).map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Status</label>
              <select
                value={filterActive}
                onChange={(e) => setFilterActive(e.target.value as 'ALL' | 'ACTIVE' | 'INACTIVE')}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">Active Only</option>
                <option value="INACTIVE">Inactive Only</option>
              </select>
            </div>
          </div>
        </div>

        {/* Destinations Grid */}
        {filteredDestinations && filteredDestinations.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {filteredDestinations.map((destination) => (
              <DestinationCard
                key={destination.id}
                destination={destination}
                onDelete={handleDelete}
                onToggleActive={handleToggleActive}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500 text-lg">No destinations found</p>
            <p className="text-gray-400 text-sm mt-2">
              Try adjusting your filters or create a new destination
            </p>
          </div>
        )}
      </div>

      {/* Create Destination Form */}
      {showCreateForm && (
        <DestinationForm
          onSubmit={handleCreate}
          onCancel={() => setShowCreateForm(false)}
          isSubmitting={createDestination.isPending}
        />
      )}
    </div>
  );
}
