/**
 * Modal component to display detailed file information.
 */

import { useEffect } from 'react';
import { useFile } from '../hooks/useFiles';
import type { FileStatus } from '../types/file';

type FileDetailsModalProps = {
  isOpen: boolean;
  onClose: () => void;
  fileId: string | null;
};

export function FileDetailsModal({ isOpen, onClose, fileId }: FileDetailsModalProps) {
  const { data: file, isLoading, error } = useFile(fileId || '');

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const formatBytes = (bytes?: number): string => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  const formatDate = (dateString?: string): string => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleString();
  };

  const getStatusColor = (status: FileStatus): string => {
    switch (status) {
      case 'DISCOVERED':
        return 'bg-gray-100 text-gray-800';
      case 'HASHED':
        return 'bg-blue-100 text-blue-800';
      case 'ANALYZED':
        return 'bg-purple-100 text-purple-800';
      case 'CLASSIFIED':
        return 'bg-green-100 text-green-800';
      case 'STAGED':
        return 'bg-yellow-100 text-yellow-800';
      case 'MIGRATED':
        return 'bg-emerald-100 text-emerald-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black bg-opacity-50 transition-opacity"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="flex min-h-screen items-center justify-center p-4">
        <div
          className="relative bg-white rounded-lg shadow-xl max-w-3xl w-full max-h-[85vh] overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-6 border-b border-gray-200">
            <h2 className="text-2xl font-bold text-gray-900">File Details</h2>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 transition-colors"
            >
              <svg
                className="w-6 h-6"
                fill="none"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Content */}
          <div className="p-6 overflow-y-auto max-h-[calc(85vh-140px)]">
            {isLoading && (
              <div className="flex flex-col items-center justify-center py-12">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                <p className="text-gray-600">Loading file details...</p>
              </div>
            )}

            {error && !isLoading && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <div className="flex items-start">
                  <svg className="w-5 h-5 text-red-600 mr-3 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                  <div>
                    <h3 className="text-sm font-medium text-red-800">Error</h3>
                    <p className="text-sm text-red-700 mt-1">
                      {error instanceof Error ? error.message : 'Failed to load file details'}
                    </p>
                  </div>
                </div>
              </div>
            )}

            {file && !isLoading && (
              <div className="space-y-6">
                {/* Basic Information */}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-3">Basic Information</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <p className="text-gray-500">File Name</p>
                      <p className="font-medium text-gray-900 break-all">{file.name}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Extension</p>
                      <p className="font-medium text-gray-900">{file.extension || 'N/A'}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Size</p>
                      <p className="font-medium text-gray-900">{formatBytes(file.size)}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Status</p>
                      <span className={`inline-block px-2 py-0.5 rounded text-xs ${getStatusColor(file.status)}`}>
                        {file.status}
                      </span>
                    </div>
                    <div>
                      <p className="text-gray-500">MIME Type</p>
                      <p className="font-medium text-gray-900">{file.mimeType || 'N/A'}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Duplicate</p>
                      <p className="font-medium text-gray-900">{file.isDuplicate ? 'Yes' : 'No'}</p>
                    </div>
                  </div>
                </div>

                {/* Path */}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-3">Location</h3>
                  <div className="bg-gray-50 p-3 rounded border border-gray-200">
                    <p className="text-sm font-mono text-gray-900 break-all">{file.path}</p>
                  </div>
                </div>

                {/* Dates */}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-3">Timestamps</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <p className="text-gray-500">Modified</p>
                      <p className="font-medium text-gray-900">{formatDate(file.modifiedAt)}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Created</p>
                      <p className="font-medium text-gray-900">{formatDate(file.createdAt)}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Accessed</p>
                      <p className="font-medium text-gray-900">{formatDate(file.accessedAt)}</p>
                    </div>
                    <div>
                      <p className="text-gray-500">Scanned</p>
                      <p className="font-medium text-gray-900">{formatDate(file.scannedAt)}</p>
                    </div>
                  </div>
                </div>

                {/* SHA-256 Hash */}
                {file.sha256 && (
                  <div>
                    <h3 className="text-lg font-semibold text-gray-900 mb-3">Hash</h3>
                    <div className="bg-gray-50 p-3 rounded border border-gray-200">
                      <p className="text-xs font-mono text-gray-900 break-all">{file.sha256}</p>
                    </div>
                  </div>
                )}

                {/* EXIF Data */}
                {file.exif && Object.keys(file.exif).length > 0 && (
                  <div>
                    <h3 className="text-lg font-semibold text-gray-900 mb-3">EXIF Metadata</h3>
                    <div className="grid grid-cols-2 gap-4 text-sm">
                      {file.exif.cameraMake && (
                        <div>
                          <p className="text-gray-500">Camera Make</p>
                          <p className="font-medium text-gray-900">{file.exif.cameraMake}</p>
                        </div>
                      )}
                      {file.exif.cameraModel && (
                        <div>
                          <p className="text-gray-500">Camera Model</p>
                          <p className="font-medium text-gray-900">{file.exif.cameraModel}</p>
                        </div>
                      )}
                      {file.exif.dateTaken && (
                        <div>
                          <p className="text-gray-500">Date Taken</p>
                          <p className="font-medium text-gray-900">{formatDate(file.exif.dateTaken)}</p>
                        </div>
                      )}
                      {file.exif.width && file.exif.height && (
                        <div>
                          <p className="text-gray-500">Dimensions</p>
                          <p className="font-medium text-gray-900">
                            {file.exif.width} × {file.exif.height}
                          </p>
                        </div>
                      )}
                      {(file.exif.latitude !== undefined && file.exif.longitude !== undefined) && (
                        <div className="col-span-2">
                          <p className="text-gray-500">GPS Coordinates</p>
                          <p className="font-medium text-gray-900">
                            {file.exif.latitude?.toFixed(6)}, {file.exif.longitude?.toFixed(6)}
                            {file.exif.altitude && ` (${file.exif.altitude}m)`}
                          </p>
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end p-6 border-t border-gray-200 bg-gray-50">
            <button
              onClick={onClose}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
