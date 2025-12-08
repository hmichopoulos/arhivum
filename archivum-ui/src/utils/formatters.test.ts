import { describe, it, expect } from 'vitest';
import { formatBytes, formatDate } from './formatters';

describe('formatBytes', () => {
  it('returns "0 B" for undefined', () => {
    expect(formatBytes(undefined)).toBe('0 B');
  });

  it('returns "0 B" for zero', () => {
    expect(formatBytes(0)).toBe('0 B');
  });

  it('formats bytes correctly', () => {
    expect(formatBytes(500)).toBe('500.0 B');
  });

  it('formats kilobytes correctly', () => {
    expect(formatBytes(1024)).toBe('1.0 KB');
    expect(formatBytes(1536)).toBe('1.5 KB');
  });

  it('formats megabytes correctly', () => {
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB');
    expect(formatBytes(1.5 * 1024 * 1024)).toBe('1.5 MB');
  });

  it('formats gigabytes correctly', () => {
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.0 GB');
    expect(formatBytes(2.5 * 1024 * 1024 * 1024)).toBe('2.5 GB');
  });

  it('formats terabytes correctly', () => {
    expect(formatBytes(1024 * 1024 * 1024 * 1024)).toBe('1.0 TB');
  });

  it('uses 1 decimal place precision', () => {
    expect(formatBytes(1234)).toBe('1.2 KB');
    expect(formatBytes(1289)).toBe('1.3 KB');
  });
});

describe('formatDate', () => {
  it('returns "N/A" for undefined', () => {
    expect(formatDate(undefined)).toBe('N/A');
  });

  it('returns "N/A" for empty string', () => {
    expect(formatDate('')).toBe('N/A');
  });

  it('formats valid ISO date string', () => {
    const result = formatDate('2025-01-15T10:30:00Z');
    expect(result).toBeTruthy();
    expect(result).not.toBe('N/A');
  });
});
