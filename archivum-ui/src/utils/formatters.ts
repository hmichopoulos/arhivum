/**
 * Utility functions for formatting values.
 */

/**
 * Formats a byte count into a human-readable string.
 * @param bytes - The number of bytes to format
 * @returns A formatted string like "1.5 MB" or "0 B" for zero/undefined
 */
export function formatBytes(bytes?: number): string {
  if (!bytes || bytes === 0) return '0 B';

  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const clampedIndex = Math.min(i, sizes.length - 1);

  return `${(bytes / Math.pow(k, clampedIndex)).toFixed(1)} ${sizes[clampedIndex]}`;
}

/**
 * Formats an ISO date string into a localized date/time string.
 * @param dateString - The ISO date string to format
 * @returns A localized date/time string or 'N/A' for invalid input
 */
export function formatDate(dateString?: string): string {
  if (!dateString) return 'N/A';
  return new Date(dateString).toLocaleString();
}
