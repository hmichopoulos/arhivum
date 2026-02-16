/**
 * Card component displaying source information.
 */

import type { Source } from '../types/source';
import { ScanStatus, SourceType } from '../types/source';

type SourceCardProps = {
  source: Source;
  onClick?: () => void;
  onDelete?: (id: string) => void;
};

export function SourceCard({ source, onClick, onDelete }: SourceCardProps) {
  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent card click
    if (window.confirm(`Are you sure you want to delete source "${source.name}"?\n\nThis will delete the source and all ${source.totalFiles.toLocaleString()} associated files from the database.`)) {
      onDelete?.(source.id);
    }
  };
  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  const formatDate = (dateString?: string): string => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleString();
  };

  const getStatusColor = (status: ScanStatus): string => {
    switch (status) {
      case ScanStatus.COMPLETED:
        return 'bg-green-100 text-green-800';
      case ScanStatus.SCANNING:
        return 'bg-blue-100 text-blue-800';
      case ScanStatus.FAILED:
        return 'bg-red-100 text-red-800';
      case ScanStatus.POSTPONED:
        return 'bg-yellow-100 text-yellow-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getTypeIcon = (type: SourceType): string => {
    switch (type) {
      case SourceType.DISK:
        return '💾';
      case SourceType.CLOUD:
        return '☁️';
      case SourceType.NETWORK:
        return '🌐';
      default:
        return '📁';
    }
  };

  const progress = source.totalFiles > 0
    ? (source.processedFiles / source.totalFiles) * 100
    : 0;

  return (
    <div
      onClick={onClick}
      className={`
        bg-white rounded-lg shadow-sm border border-gray-200 p-6
        transition-all duration-200 hover:shadow-md
        ${onClick ? 'cursor-pointer' : ''}
      `}
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <span className="text-3xl">{getTypeIcon(source.type)}</span>
          <div>
            <h3 className="text-lg font-semibold text-gray-900">
              {source.physicalId?.physicalLabel || source.name}
            </h3>
            <p className="text-sm text-gray-500">{source.rootPath}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(
              source.status
            )}`}
          >
            {source.status}
          </span>
          {onDelete && (
            <button
              onClick={handleDelete}
              className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
              title="Delete source"
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

      {/* Physical ID Info */}
      {source.physicalId && (
        <div className="mb-4 text-sm text-gray-600">
          {source.physicalId.physicalLabel && (
            <div className="flex items-center gap-2">
              <span className="font-medium">Label:</span>
              <span>{source.physicalId.physicalLabel}</span>
            </div>
          )}
          {source.physicalId.volumeLabel && (
            <div className="flex items-center gap-2">
              <span className="font-medium">Volume:</span>
              <span>{source.physicalId.volumeLabel}</span>
            </div>
          )}
        </div>
      )}

      {/* Progress Bar */}
      {source.status === ScanStatus.SCANNING && (
        <div className="mb-4">
          <div className="flex justify-between text-sm text-gray-600 mb-1">
            <span>Progress</span>
            <span>{progress.toFixed(1)}%</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-600 h-2 rounded-full transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}

      {/* Stats Grid */}
      <div className="grid grid-cols-2 gap-4 text-sm">
        <div>
          <p className="text-gray-500">Files</p>
          <p className="font-semibold text-gray-900">
            {source.processedFiles.toLocaleString()} / {source.totalFiles.toLocaleString()}
          </p>
        </div>
        <div>
          <p className="text-gray-500">Size</p>
          <p className="font-semibold text-gray-900">
            {formatBytes(source.processedSize)} / {formatBytes(source.totalSize)}
          </p>
        </div>
        <div>
          <p className="text-gray-500">Started</p>
          <p className="font-semibold text-gray-900">{formatDate(source.scanStartedAt)}</p>
        </div>
        <div>
          <p className="text-gray-500">Completed</p>
          <p className="font-semibold text-gray-900">{formatDate(source.scanCompletedAt)}</p>
        </div>
      </div>
    </div>
  );
}
