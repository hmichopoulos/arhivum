/**
 * Card component displaying destination information with real-time disk space.
 */

import type { Destination } from '../types/destination';
import { DestinationType } from '../types/destination';

type DestinationCardProps = {
  destination: Destination;
  onClick?: () => void;
  onDelete?: (id: string) => void;
  onToggleActive?: (id: string, isActive: boolean) => void;
};

export function DestinationCard({ destination, onClick, onDelete, onToggleActive }: DestinationCardProps) {
  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm(`Are you sure you want to delete destination "${destination.name}"?`)) {
      onDelete?.(destination.id);
    }
  };

  const handleToggleActive = (e: React.MouseEvent) => {
    e.stopPropagation();
    onToggleActive?.(destination.id, !destination.isActive);
  };

  const formatBytes = (bytes?: number): string => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  const getTypeIcon = (type: DestinationType): string => {
    switch (type) {
      case DestinationType.NAS:
        return '🗄️';
      case DestinationType.WAREHOUSE:
        return '🏢';
      case DestinationType.STAGING:
        return '📦';
      default:
        return '📁';
    }
  };

  const getTypeColor = (type: DestinationType): string => {
    switch (type) {
      case DestinationType.NAS:
        return 'bg-blue-100 text-blue-800';
      case DestinationType.WAREHOUSE:
        return 'bg-purple-100 text-purple-800';
      case DestinationType.STAGING:
        return 'bg-yellow-100 text-yellow-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getUsageColor = (percentage?: number): string => {
    if (!percentage) return 'bg-gray-400';
    if (percentage < 70) return 'bg-green-500';
    if (percentage < 85) return 'bg-yellow-500';
    return 'bg-red-500';
  };

  return (
    <div
      onClick={onClick}
      className={`
        bg-white rounded-lg shadow-sm border border-gray-200 p-6
        transition-all duration-200 hover:shadow-md
        ${onClick ? 'cursor-pointer' : ''}
        ${!destination.isActive ? 'opacity-60' : ''}
      `}
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <span className="text-3xl">{getTypeIcon(destination.destinationType)}</span>
          <div>
            <h3 className="text-lg font-semibold text-gray-900">
              {destination.name}
            </h3>
            <p className="text-sm text-gray-500">{destination.mountedPath}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`px-3 py-1 rounded-full text-xs font-medium ${getTypeColor(
              destination.destinationType
            )}`}
          >
            {destination.destinationType}
          </span>
          {onToggleActive && (
            <button
              onClick={handleToggleActive}
              className={`p-2 rounded-md transition-colors ${
                destination.isActive
                  ? 'text-green-600 hover:bg-green-50'
                  : 'text-gray-400 hover:bg-gray-50'
              }`}
              title={destination.isActive ? 'Deactivate' : 'Activate'}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                {destination.isActive ? (
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                ) : (
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                )}
              </svg>
            </button>
          )}
          {onDelete && (
            <button
              onClick={handleDelete}
              className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
              title="Delete destination"
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
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Physical Identifier */}
      {destination.physicalIdentifier && (
        <div className="mb-4 text-sm text-gray-600">
          <div className="flex items-center gap-2">
            <span className="font-medium">Hardware ID:</span>
            <span className="font-mono text-xs">{destination.physicalIdentifier}</span>
          </div>
        </div>
      )}

      {/* Accessibility Status */}
      {!destination.isAccessible && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md">
          <div className="flex items-center gap-2 text-red-800 text-sm">
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
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            <span className="font-medium">Path not accessible</span>
          </div>
          {destination.errorMessage && (
            <p className="text-xs text-red-600 mt-1 ml-7">{destination.errorMessage}</p>
          )}
        </div>
      )}

      {/* Disk Space Usage */}
      {destination.isAccessible && destination.usagePercentage !== undefined && (
        <div className="mb-4">
          <div className="flex justify-between text-sm text-gray-600 mb-2">
            <span>Disk Usage</span>
            <span className="font-medium">{destination.usagePercentage.toFixed(1)}%</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-3 mb-2">
            <div
              className={`h-3 rounded-full transition-all duration-300 ${getUsageColor(
                destination.usagePercentage
              )}`}
              style={{ width: `${Math.min(destination.usagePercentage, 100)}%` }}
            />
          </div>
          <div className="flex justify-between text-xs text-gray-500">
            <span>{formatBytes(destination.usedSpaceBytes)} used</span>
            <span>{formatBytes(destination.availableSpaceBytes)} available</span>
          </div>
        </div>
      )}

      {/* Stats Grid */}
      <div className="grid grid-cols-2 gap-4 text-sm">
        <div>
          <p className="text-gray-500">Priority</p>
          <p className="font-semibold text-gray-900">{destination.priority}</p>
        </div>
        <div>
          <p className="text-gray-500">Status</p>
          <p className={`font-semibold ${destination.isActive ? 'text-green-600' : 'text-gray-400'}`}>
            {destination.isActive ? 'Active' : 'Inactive'}
          </p>
        </div>
        {destination.totalCapacityBytes && (
          <div className="col-span-2">
            <p className="text-gray-500">Total Capacity</p>
            <p className="font-semibold text-gray-900">
              {formatBytes(destination.totalCapacityBytes)}
            </p>
          </div>
        )}
      </div>

      {/* Description */}
      {destination.description && (
        <div className="mt-4 pt-4 border-t border-gray-200">
          <p className="text-sm text-gray-600 italic">{destination.description}</p>
        </div>
      )}
    </div>
  );
}
