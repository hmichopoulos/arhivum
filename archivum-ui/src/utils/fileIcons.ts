/**
 * File icon utilities for displaying file type indicators.
 * Centralizes icon mapping logic for maintainability.
 */

import { ProjectType } from '../types/codeProject';

/**
 * File extension to icon mapping configuration.
 * Groups extensions by file type category.
 */
const FILE_ICON_MAP: Record<string, string[]> = {
  '🖼️': ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico'],
  '🎬': ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm', 'm4v'],
  '🎵': ['mp3', 'wav', 'flac', 'aac', 'ogg', 'wma', 'm4a'],
  '📝': ['txt', 'md', 'doc', 'docx', 'odt', 'rtf'],
  '📊': ['pdf', 'xls', 'xlsx', 'csv', 'ods'],
  '📦': ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'],
  '⚙️': ['exe', 'msi', 'app', 'dmg', 'deb', 'rpm'],
  '💻': ['js', 'ts', 'py', 'java', 'cpp', 'c', 'go', 'rs', 'rb', 'php', 'swift', 'kt'],
};

/**
 * Default icon for unknown file types.
 */
const DEFAULT_FILE_ICON = '📄';

/**
 * Default folder icon (closed).
 */
export const FOLDER_ICON = '📁';

/**
 * Open folder icon.
 */
export const OPEN_FOLDER_ICON = '📂';

/**
 * Get icon for a file based on its extension.
 *
 * @param extension File extension (with or without dot)
 * @returns Icon emoji string
 */
export function getFileIcon(extension?: string): string {
  if (!extension) return DEFAULT_FILE_ICON;

  const ext = extension.toLowerCase().replace(/^\./, '');

  for (const [icon, extensions] of Object.entries(FILE_ICON_MAP)) {
    if (extensions.includes(ext)) {
      return icon;
    }
  }

  return DEFAULT_FILE_ICON;
}

/**
 * Project type to icon mapping.
 */
const PROJECT_TYPE_ICONS: Record<ProjectType, string> = {
  [ProjectType.MAVEN]: '☕',
  [ProjectType.GRADLE]: '🐘',
  [ProjectType.NPM]: '📦',
  [ProjectType.PYTHON]: '🐍',
  [ProjectType.GO]: '🐹',
  [ProjectType.RUST]: '🦀',
  [ProjectType.GENERIC]: '📁',
};

/**
 * Get icon for a code project type.
 *
 * @param type Project type
 * @returns Icon emoji string
 */
export function getProjectTypeIcon(type: ProjectType): string {
  return PROJECT_TYPE_ICONS[type] || '📁';
}
