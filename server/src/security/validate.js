const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype']);

export function sanitizePlainObject(value, depth = 0) {
  if (depth > 8) throw new ValidationError('Payload demasiado profundo');
  if (Array.isArray(value)) {
    return value.map((item) => sanitizePlainObject(item, depth + 1));
  }
  if (value !== null && typeof value === 'object') {
    const clean = Object.create(null);
    for (const [key, val] of Object.entries(value)) {
      if (DANGEROUS_KEYS.has(key)) {
        throw new ValidationError('Propiedad no permitida en payload');
      }
      clean[key] = sanitizePlainObject(val, depth + 1);
    }
    return clean;
  }
  return value;
}

export class ValidationError extends Error {
  constructor(message) {
    super(message);
    this.name = 'ValidationError';
  }
}

export function requireString(value, field, { min = 1, max = 200, pattern = null, trim = true } = {}) {
  if (typeof value !== 'string') {
    throw new ValidationError(`${field} debe ser texto`);
  }
  const normalized = trim ? value.trim() : value;
  if (normalized.length < min || normalized.length > max) {
    throw new ValidationError(`${field} debe tener entre ${min} y ${max} caracteres`);
  }
  if (pattern && !pattern.test(normalized)) {
    throw new ValidationError(`${field} tiene formato invalido`);
  }
  return normalized;
}

export function optionalString(value, field, { max = 1000, fallback = '' } = {}) {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'string') {
    throw new ValidationError(`${field} debe ser texto`);
  }
  const normalized = value.trim();
  if (normalized.length > max) {
    throw new ValidationError(`${field} excede ${max} caracteres`);
  }
  return normalized;
}

export function requireInt(value, field, { min = 0, max = 1_000_000_000 } = {}) {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) {
    throw new ValidationError(`${field} debe ser un entero entre ${min} y ${max}`);
  }
  return value;
}

export function optionalPositiveInt(value, field, { min = 1, max = 1_000_000_000 } = {}) {
  if (value === undefined || value === null || value === '') return null;
  return requireInt(value, field, { min, max });
}

export function requireBoolean(value, field) {
  if (typeof value !== 'boolean') {
    throw new ValidationError(`${field} debe ser booleano`);
  }
  return value;
}

export function slugify(text) {
  return String(text || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64);
}

export const USERNAME_RE = /^[a-zA-Z0-9_.-]{3,32}$/;
export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
export const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]?$/;
export const HEX_COLOR_RE = /^#[0-9a-fA-F]{6}$/;
export const MEDIA_KEY_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(png|jpg|webp)$/;
export const FOUNDER_TAGS = new Set(['Enrique', 'Edgarvis', 'Leonel', 'Aníbal', 'Crew']);
export const ORDER_STATUSES = new Set(['pending', 'preparing', 'shipped', 'delivered', 'cancelled']);
