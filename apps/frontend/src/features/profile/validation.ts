export function isValidUrl(v: string): boolean {
  try {
    const u = new URL(v);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

export function isValidEmail(v: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
}

/** Empty string → null so the backend receives explicit nulls instead of blanks. */
export const blankToNull = (v: string | null | undefined): string | null => {
  const t = (v ?? '').trim();
  return t ? t : null;
};
