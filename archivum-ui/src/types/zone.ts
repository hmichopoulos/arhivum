/**
 * Classification zones for files and folders.
 */
export enum Zone {
  MEDIA = 'MEDIA',
  DOCUMENTS = 'DOCUMENTS',
  BOOKS = 'BOOKS',
  SOFTWARE = 'SOFTWARE',
  BACKUP = 'BACKUP',
  CODE = 'CODE',
  UNKNOWN = 'UNKNOWN'
}

export const ZONE_LABELS: Record<Zone, string> = {
  [Zone.MEDIA]: 'Media',
  [Zone.DOCUMENTS]: 'Documents',
  [Zone.BOOKS]: 'Books',
  [Zone.SOFTWARE]: 'Software',
  [Zone.BACKUP]: 'Backup',
  [Zone.CODE]: 'Code',
  [Zone.UNKNOWN]: 'Unknown'
};

export const ZONE_COLORS: Record<Zone, string> = {
  [Zone.MEDIA]: 'bg-purple-100 text-purple-800',
  [Zone.DOCUMENTS]: 'bg-blue-100 text-blue-800',
  [Zone.BOOKS]: 'bg-green-100 text-green-800',
  [Zone.SOFTWARE]: 'bg-orange-100 text-orange-800',
  [Zone.BACKUP]: 'bg-gray-100 text-gray-800',
  [Zone.CODE]: 'bg-cyan-100 text-cyan-800',
  [Zone.UNKNOWN]: 'bg-red-100 text-red-800'
};
