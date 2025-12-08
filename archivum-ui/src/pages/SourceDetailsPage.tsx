/**
 * Source details page with file browser.
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSource, useSourceTree } from '../hooks/useSources';
import { useProjectsBySource } from '../hooks/useCodeProjects';
import { TreeView } from '../components/TreeView';
import { FileDetailsModal } from '../components/FileDetailsModal';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { ScanStatus } from '../types/source';

type SourceDetailsPageProps = {
  sourceId: string;
};

export function SourceDetailsPage({ sourceId }: SourceDetailsPageProps) {
  const navigate = useNavigate();
  const { data: source, isLoading: sourceLoading } = useSource(sourceId);
  const { data: tree, isLoading: treeLoading } = useSourceTree(sourceId);
  const { data: codeProjects = [] } = useProjectsBySource(sourceId);
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

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

  const handleFileClick = (fileId: string) => {
    setSelectedFileId(fileId);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedFileId(null);
  };

  if (sourceLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading source details...</p>
        </div>
      </div>
    );
  }

  if (!source) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center text-red-600">
          <p className="text-xl font-semibold">Source not found</p>
        </div>
      </div>
    );
  }

  const progress = source.totalFiles > 0
    ? (source.processedFiles / source.totalFiles) * 100
    : 0;

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* Breadcrumb */}
        <div className="mb-4">
          <button
            onClick={() => navigate('/sources')}
            className="text-blue-600 hover:text-blue-800 flex items-center gap-2"
          >
            ← Back to Sources
          </button>
        </div>

        {/* Source Header */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 mb-2">
                {source.physicalId?.physicalLabel || source.name}
              </h1>
              <p className="text-gray-600">{source.rootPath}</p>
            </div>
            <span
              className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(
                source.status
              )}`}
            >
              {source.status}
            </span>
          </div>

          {/* Physical ID Details */}
          {source.physicalId && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4 text-sm">
              {source.physicalId.physicalLabel && (
                <div>
                  <p className="text-gray-500">Physical Label</p>
                  <p className="font-medium text-gray-900">
                    {source.physicalId.physicalLabel}
                  </p>
                </div>
              )}
              {source.physicalId.volumeLabel && (
                <div>
                  <p className="text-gray-500">Volume Label</p>
                  <p className="font-medium text-gray-900">
                    {source.physicalId.volumeLabel}
                  </p>
                </div>
              )}
              {source.physicalId.serialNumber && (
                <div>
                  <p className="text-gray-500">Serial Number</p>
                  <p className="font-medium text-gray-900">
                    {source.physicalId.serialNumber}
                  </p>
                </div>
              )}
              {source.physicalId.filesystemType && (
                <div>
                  <p className="text-gray-500">Filesystem</p>
                  <p className="font-medium text-gray-900">
                    {source.physicalId.filesystemType}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Progress Bar (if scanning) */}
          {source.status === ScanStatus.SCANNING && (
            <div className="mb-4">
              <div className="flex justify-between text-sm text-gray-600 mb-1">
                <span>Scan Progress</span>
                <span>{progress.toFixed(1)}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-3">
                <div
                  className="bg-blue-600 h-3 rounded-full transition-all duration-300"
                  style={{ width: `${progress}%` }}
                />
              </div>
            </div>
          )}

          {/* Stats Grid */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-gray-500">Files Processed</p>
              <p className="text-xl font-bold text-gray-900">
                {source.processedFiles.toLocaleString()} /{' '}
                {source.totalFiles.toLocaleString()}
              </p>
            </div>
            <div>
              <p className="text-gray-500">Size Processed</p>
              <p className="text-xl font-bold text-gray-900">
                {formatBytes(source.processedSize)} / {formatBytes(source.totalSize)}
              </p>
            </div>
            <div>
              <p className="text-gray-500">Scan Started</p>
              <p className="text-xl font-bold text-gray-900">
                {formatDate(source.scanStartedAt)}
              </p>
            </div>
            <div>
              <p className="text-gray-500">Scan Completed</p>
              <p className="text-xl font-bold text-gray-900">
                {formatDate(source.scanCompletedAt)}
              </p>
            </div>
          </div>
        </div>

        {/* Files Section */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-900">Files & Folders</h2>
          </div>

          {treeLoading ? (
            <div className="text-center py-12">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4"></div>
              <p className="text-gray-600">Loading files...</p>
            </div>
          ) : tree ? (
            <ErrorBoundary
              fallback={(error, _errorInfo, reset) => (
                <div className="bg-red-50 border border-red-200 rounded-lg p-6">
                  <div className="flex items-start">
                    <svg className="w-6 h-6 text-red-600 mr-3 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    <div className="flex-1">
                      <h3 className="text-sm font-medium text-red-800 mb-1">Failed to render file tree</h3>
                      <p className="text-sm text-red-700 mb-3">
                        {error.message}
                      </p>
                      <button
                        onClick={reset}
                        className="px-3 py-1.5 bg-red-600 text-white text-sm rounded hover:bg-red-700 transition-colors"
                      >
                        Try again
                      </button>
                    </div>
                  </div>
                </div>
              )}
            >
              <TreeView tree={tree} codeProjects={codeProjects} onFileClick={handleFileClick} />
            </ErrorBoundary>
          ) : (
            <div className="text-center py-12 text-gray-500">
              <p>No files found</p>
            </div>
          )}
        </div>
      </div>

      {/* File Details Modal */}
      <FileDetailsModal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        fileId={selectedFileId}
      />
    </div>
  );
}
