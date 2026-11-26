import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export class UploadError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.name = 'UploadError';
    this.status = status;
  }
}

export function detectImageMagic(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 16) return null;

  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47 &&
    buffer[4] === 0x0d &&
    buffer[5] === 0x0a &&
    buffer[6] === 0x1a &&
    buffer[7] === 0x0a
  ) {
    return { mimeType: 'image/png', ext: 'png' };
  }

  // JPEG: FF D8 FF
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return { mimeType: 'image/jpeg', ext: 'jpg' };
  }

  // WebP: RIFF....WEBP
  if (
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WEBP'
  ) {
    return { mimeType: 'image/webp', ext: 'webp' };
  }

  return null;
}

export function parseMultipartSingleFile(bodyBuffer, contentTypeHeader, { optionalFile = false } = {}) {
  const match = /boundary=(?:"([^"]+)"|([^;\s]+))/i.exec(contentTypeHeader || '');
  if (!match) {
    throw new UploadError('Cabecera Content-Type multipart invalida', 400);
  }
  const boundary = match[1] || match[2];
  if (!boundary || boundary.length > 120) {
    throw new UploadError('Boundary multipart invalido', 400);
  }

  const boundaryBuf = Buffer.from(`--${boundary}`, 'latin1');
  const parts = [];
  let pos = bodyBuffer.indexOf(boundaryBuf);
  if (pos === -1) {
    throw new UploadError('Cuerpo multipart vacio o malformado', 400);
  }

  while (pos !== -1) {
    const start = pos + boundaryBuf.length;
    if (bodyBuffer[start] === 0x2d && bodyBuffer[start + 1] === 0x2d) {
      break; // closing --boundary--
    }
    const lineEnd = bodyBuffer[start] === 0x0d && bodyBuffer[start + 1] === 0x0a ? start + 2 : start + 1;
    const nextPos = bodyBuffer.indexOf(boundaryBuf, lineEnd);
    if (nextPos === -1) break;

    let partEnd = nextPos;
    if (bodyBuffer[partEnd - 2] === 0x0d && bodyBuffer[partEnd - 1] === 0x0a) {
      partEnd -= 2;
    } else if (bodyBuffer[partEnd - 1] === 0x0a) {
      partEnd -= 1;
    }

    const partBuf = bodyBuffer.subarray(lineEnd, partEnd);
    const headerSep = partBuf.indexOf(Buffer.from('\r\n\r\n'));
    if (headerSep !== -1) {
      const headersRaw = partBuf.subarray(0, headerSep).toString('utf8');
      const dataBuf = partBuf.subarray(headerSep + 4);
      const dispLine = headersRaw
        .split(/\r?\n/)
        .find((h) => h.toLowerCase().startsWith('content-disposition:'));
      const nameMatch = dispLine && /name="([^"]+)"/.exec(dispLine);
      const fileMatch = dispLine && /filename="([^"]*)"/.exec(dispLine);
      if (nameMatch) {
        parts.push({
          fieldName: nameMatch[1],
          isFile: Boolean(fileMatch),
          data: dataBuf,
        });
      }
    }
    pos = nextPos;
  }

  const fields = Object.create(null);
  let fileBuffer = null;

  for (const part of parts) {
    if (part.isFile) {
      if (part.fieldName === 'image' && !fileBuffer && part.data.length > 0) {
        fileBuffer = part.data;
      }
    } else if (part.fieldName !== '__proto__' && part.fieldName !== 'constructor' && part.fieldName !== 'prototype') {
      fields[part.fieldName] = part.data.toString('utf8').trim();
    }
  }

  if (!optionalFile && (!fileBuffer || fileBuffer.length === 0)) {
    throw new UploadError('No se recibio ningun archivo en el campo image', 400);
  }

  return { fields, fileBuffer };
}

export function saveValidatedImage(fileBuffer, uploadDir, maxBytes) {
  if (!Buffer.isBuffer(fileBuffer) || fileBuffer.length === 0) {
    throw new UploadError('Archivo de imagen vacio', 400);
  }
  if (fileBuffer.length > maxBytes) {
    throw new UploadError(`La imagen excede el limite de ${Math.floor(maxBytes / (1024 * 1024))} MB`, 413);
  }

  const magic = detectImageMagic(fileBuffer);
  if (!magic) {
    throw new UploadError('Formato no permitido. Solo PNG, JPEG o WebP reales (SVG bloqueado por seguridad).', 415);
  }

  fs.mkdirSync(uploadDir, { recursive: true });
  const storageKey = `${crypto.randomUUID()}.${magic.ext}`;
  const targetPath = path.resolve(uploadDir, storageKey);
  const resolvedRoot = path.resolve(uploadDir);
  if (!targetPath.startsWith(resolvedRoot + path.sep)) {
    throw new UploadError('Ruta de destino invalida', 400);
  }

  fs.writeFileSync(targetPath, fileBuffer, { flag: 'wx' });
  const sha256 = crypto.createHash('sha256').update(fileBuffer).digest('hex');

  return {
    storageKey,
    mimeType: magic.mimeType,
    byteSize: fileBuffer.length,
    sha256,
  };
}

export function deleteStoredImageFile(uploadDir, storageKey) {
  const resolvedRoot = path.resolve(uploadDir);
  const targetPath = path.resolve(uploadDir, storageKey);
  if (!targetPath.startsWith(resolvedRoot + path.sep)) return;
  try {
    fs.unlinkSync(targetPath);
  } catch {
    // File already removed.
  }
}
