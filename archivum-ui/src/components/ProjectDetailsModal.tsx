/**
 * Modal component to display detailed code project information.
 */

import { useEffect } from 'react';
import type { CodeProject, ProjectType } from '../types/codeProject';

type ProjectDetailsModalProps = {
  isOpen: boolean;
  onClose: () => void;
  project: CodeProject | null;
  getIcon: (type: ProjectType) => string;
  getColor: (type: ProjectType) => string;
};

export function ProjectDetailsModal({
  isOpen,
  onClose,
  project,
  getIcon,
  getColor
}: ProjectDetailsModalProps) {
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

  if (!isOpen || !project) return null;

  const formatBytes = (bytes: number): string => {
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleString();
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
            <div className="flex items-center gap-3">
              <span className="text-3xl">{getIcon(project.identity.type)}</span>
              <div>
                <h2 className="text-2xl font-bold text-gray-900">{project.identity.name}</h2>
                <span className={`inline-block mt-1 px-2 py-1 rounded text-xs font-medium ${getColor(project.identity.type)}`}>
                  {project.identity.type}
                </span>
              </div>
            </div>
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
            <div className="space-y-6">
              {/* Project Identity */}
              <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-3">Project Identity</h3>
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <p className="text-gray-500">Project Name</p>
                    <p className="font-medium text-gray-900">{project.identity.name}</p>
                  </div>
                  {project.identity.version && (
                    <div>
                      <p className="text-gray-500">Version</p>
                      <p className="font-medium text-gray-900">{project.identity.version}</p>
                    </div>
                  )}
                  {project.identity.groupId && (
                    <div>
                      <p className="text-gray-500">Group ID</p>
                      <p className="font-medium text-gray-900">{project.identity.groupId}</p>
                    </div>
                  )}
                  <div>
                    <p className="text-gray-500">Type</p>
                    <p className="font-medium text-gray-900">{project.identity.type}</p>
                  </div>
                  <div className="col-span-2">
                    <p className="text-gray-500">Identifier</p>
                    <p className="font-medium text-gray-900 font-mono text-xs break-all">{project.identity.identifier}</p>
                  </div>
                </div>
              </div>

              {/* Location */}
              <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-3">Location</h3>
                <div className="bg-gray-50 p-3 rounded border border-gray-200">
                  <p className="text-sm font-mono text-gray-900 break-all">{project.rootPath}</p>
                </div>
                {project.archivePath && (
                  <div className="mt-2">
                    <p className="text-sm text-gray-500">Archive Path</p>
                    <div className="bg-gray-50 p-3 rounded border border-gray-200 mt-1">
                      <p className="text-sm font-mono text-gray-900 break-all">{project.archivePath}</p>
                    </div>
                  </div>
                )}
              </div>

              {/* Statistics */}
              <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-3">Statistics</h3>
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <p className="text-gray-500">Source Files</p>
                    <p className="text-xl font-bold text-gray-900">{project.sourceFileCount.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-gray-500">Total Files</p>
                    <p className="text-xl font-bold text-gray-900">{project.totalFileCount.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-gray-500">Total Size</p>
                    <p className="text-xl font-bold text-gray-900">{formatBytes(project.totalSizeBytes)}</p>
                  </div>
                  <div>
                    <p className="text-gray-500">Scanned At</p>
                    <p className="text-sm font-medium text-gray-900">{formatDate(project.scannedAt)}</p>
                  </div>
                </div>
              </div>

              {/* Content Hash */}
              <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-3">Content Hash</h3>
                <div className="bg-gray-50 p-3 rounded border border-gray-200">
                  <p className="text-xs font-mono text-gray-900 break-all">{project.contentHash}</p>
                </div>
                <p className="text-xs text-gray-500 mt-2">
                  This hash identifies the unique content of the project and is used for deduplication.
                </p>
              </div>

              {/* Project Type Information */}
              {project.identity.type === 'GIT' && (
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                  <div className="flex items-start">
                    <svg className="w-5 h-5 text-blue-600 mr-2 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                    </svg>
                    <div className="flex-1">
                      <p className="text-sm font-medium text-blue-800">Git Repository</p>
                      <p className="text-xs text-blue-700 mt-1">
                        This project is tracked by Git. The repository contains version control history.
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>
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
