/**
 * Table component displaying files with sorting and filtering.
 */

import { useState } from 'react';
import type { ScannedFile } from '../types/file';
import { FileStatus } from '../types/file';

type FileTableProps = {
  files: ScannedFile[];
  onFileClick?: (file: ScannedFile) => void;
};

type SortField = 'name' | 'size' | 'scannedAt' | 'status';
type SortDirection = 'asc' | 'desc';

export function FileTable({ files, onFileClick }: FileTableProps) {
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  };

  const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleString();
  };

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const sortedFiles = [...files].sort((a, b) => {
    let comparison = 0;

    switch (sortField) {
      case 'name':
        comparison = a.name.localeCompare(b.name);
        break;
      case 'size':
        comparison = a.size - b.size;
        break;
      case 'scannedAt':
        comparison =
          new Date(a.scannedAt).getTime() - new Date(b.scannedAt).getTime();
        break;
      case 'status':
        comparison = a.status.localeCompare(b.status);
        break;
    }

    return sortDirection === 'asc' ? comparison : -comparison;
  });

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) return <span className="text-gray-400">↕</span>;
    return sortDirection === 'asc' ? (
      <span className="text-blue-600">↑</span>
    ) : (
      <span className="text-blue-600">↓</span>
    );
  };

  const getStatusColor = (status: FileStatus): string => {
    switch (status) {
      case FileStatus.MIGRATED:
        return 'bg-green-100 text-green-800';
      case FileStatus.STAGED:
        return 'bg-blue-100 text-blue-800';
      case FileStatus.CLASSIFIED:
        return 'bg-purple-100 text-purple-800';
      case FileStatus.ANALYZED:
        return 'bg-yellow-100 text-yellow-800';
      case FileStatus.HASHED:
        return 'bg-gray-100 text-gray-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getFileIcon = (extension?: string): string => {
    if (!extension) return '📄';
    const ext = extension.toLowerCase();
    if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) return '🖼️';
    if (['mp4', 'avi', 'mkv', 'mov', 'wmv'].includes(ext)) return '🎥';
    if (['mp3', 'wav', 'flac', 'ogg', 'aac'].includes(ext)) return '🎵';
    if (['pdf'].includes(ext)) return '📕';
    if (['doc', 'docx', 'txt', 'rtf'].includes(ext)) return '📝';
    if (['xls', 'xlsx', 'csv'].includes(ext)) return '📊';
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '📦';
    if (['js', 'ts', 'jsx', 'tsx', 'py', 'java', 'cpp', 'c'].includes(ext)) return '💻';
    return '📄';
  };

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Type
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('name')}
              >
                <div className="flex items-center gap-2">
                  Name
                  <SortIcon field="name" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('size')}
              >
                <div className="flex items-center gap-2">
                  Size
                  <SortIcon field="size" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('status')}
              >
                <div className="flex items-center gap-2">
                  Status
                  <SortIcon field="status" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('scannedAt')}
              >
                <div className="flex items-center gap-2">
                  Scanned
                  <SortIcon field="scannedAt" />
                </div>
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Duplicate
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {sortedFiles.map((file) => (
              <tr
                key={file.id}
                onClick={() => onFileClick?.(file)}
                className={`
                  transition-colors
                  ${onFileClick ? 'cursor-pointer hover:bg-gray-50' : ''}
                  ${file.isDuplicate ? 'bg-yellow-50' : ''}
                `}
              >
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className="text-2xl">{getFileIcon(file.extension)}</span>
                </td>
                <td className="px-6 py-4">
                  <div className="text-sm font-medium text-gray-900">{file.name}</div>
                  <div className="text-xs text-gray-500 truncate max-w-md">
                    {file.path}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-gray-900">{formatBytes(file.size)}</div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span
                    className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getStatusColor(
                      file.status
                    )}`}
                  >
                    {file.status}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {formatDate(file.scannedAt)}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-center">
                  {file.isDuplicate && <span className="text-yellow-600">⚠️</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {sortedFiles.length === 0 && (
        <div className="text-center py-12 text-gray-500">
          <p>No files found</p>
        </div>
      )}
    </div>
  );
}
