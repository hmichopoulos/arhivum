/**
 * Modal component to display duplicate file locations.
 */

import { useEffect } from 'react';
import type { ScannedFile } from '../types/file';
import { formatBytes, formatDate } from '../utils/formatters';

type DuplicateLocationsModalProps = {
  isOpen: boolean;
  onClose: () => void;
  duplicates: ScannedFile[];
  currentFileId: string;
  isLoading?: boolean;
  error?: string | null;
};

export function DuplicateLocationsModal({
  isOpen,
  onClose,
  duplicates,
  currentFileId,
  isLoading = false,
  error = null
}: DuplicateLocationsModalProps) {
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
          className="relative bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[80vh] overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-6 border-b border-gray-200">
            <h2 className="text-2xl font-bold text-gray-900">
              Duplicate File Locations
            </h2>
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
          <div className="p-6 overflow-y-auto max-h-[calc(80vh-140px)]">
            {isLoading && (
              <div className="flex flex-col items-center justify-center py-12">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                <p className="text-gray-600">Loading duplicate locations...</p>
              </div>
            )}

            {error && !isLoading && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
                <div className="flex items-start">
                  <svg className="w-5 h-5 text-red-600 mr-3 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                  <div>
                    <h3 className="text-sm font-medium text-red-800">Error</h3>
                    <p className="text-sm text-red-700 mt-1">{error}</p>
                  </div>
                </div>
              </div>
            )}

            {!isLoading && !error && (
              <>
                <p className="text-sm text-gray-600 mb-4">
                  Found {duplicates.length} {duplicates.length === 1 ? 'copy' : 'copies'} of this file:
                </p>

                <div className="space-y-3">
                  {duplicates.map((file) => (
                <div
                  key={file.id}
                  className={`p-4 rounded-lg border ${
                    file.id === currentFileId
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 bg-gray-50'
                  }`}
                >
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1">
                      <p className="font-medium text-gray-900 break-all">
                        {file.path}
                      </p>
                      {file.id === currentFileId && (
                        <span className="inline-block mt-1 px-2 py-0.5 bg-blue-200 text-blue-800 text-xs rounded">
                          Current file
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-gray-500">Size:</span>
                      <span className="ml-2 text-gray-900 font-medium">
                        {formatBytes(file.size)}
                      </span>
                    </div>
                    <div>
                      <span className="text-gray-500">Modified:</span>
                      <span className="ml-2 text-gray-900 font-medium">
                        {formatDate(file.modifiedAt)}
                      </span>
                    </div>
                    <div className="col-span-2">
                      <span className="text-gray-500">SHA-256:</span>
                      <code className="ml-2 text-xs bg-gray-200 px-2 py-1 rounded font-mono">
                        {file.sha256?.substring(0, 16)}...
                      </code>
                    </div>
                  </div>
                </div>
              ))}
                </div>
              </>
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
