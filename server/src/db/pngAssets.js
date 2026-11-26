import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i += 1) {
    c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf = Buffer.allocUnsafe(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.allocUnsafe(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function encodePng(width, height, rgba) {
  const raw = Buffer.allocUnsafe(height * (1 + width * 4));
  for (let y = 0; y < height; y += 1) {
    const rowStart = y * (1 + width * 4);
    raw[rowStart] = 0; // filter type 0
    rgba.copy(raw, rowStart + 1, y * width * 4, (y + 1) * width * 4);
  }

  const ihdr = Buffer.allocUnsafe(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const compressed = zlib.deflateSync(raw, { level: 6 });
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', compressed),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

function hexToRgb(hex) {
  const clean = hex.replace('#', '');
  return [
    Number.parseInt(clean.slice(0, 2), 16),
    Number.parseInt(clean.slice(2, 4), 16),
    Number.parseInt(clean.slice(4, 6), 16),
  ];
}

class Canvas {
  constructor(width, height) {
    this.w = width;
    this.h = height;
    this.buf = Buffer.alloc(width * height * 4);
  }

  blendPixel(x, y, r, g, b, a) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || a <= 0) return;
    const idx = (y * this.w + x) * 4;
    const alpha = Math.min(1, a);
    const inv = 1 - alpha;
    this.buf[idx] = Math.round(this.buf[idx] * inv + r * alpha);
    this.buf[idx + 1] = Math.round(this.buf[idx + 1] * inv + g * alpha);
    this.buf[idx + 2] = Math.round(this.buf[idx + 2] * inv + b * alpha);
    this.buf[idx + 3] = 255;
  }

  fillGradientBg(topRgb, botRgb, glowRgb) {
    const cx = this.w * 0.5;
    const cy = this.h * 0.44;
    const maxR = this.w * 0.65;
    for (let y = 0; y < this.h; y += 1) {
      const t = y / (this.h - 1);
      const baseR = topRgb[0] * (1 - t) + botRgb[0] * t;
      const baseG = topRgb[1] * (1 - t) + botRgb[1] * t;
      const baseB = topRgb[2] * (1 - t) + botRgb[2] * t;
      for (let x = 0; x < this.w; x += 1) {
        const dx = x - cx;
        const dy = y - cy;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const glow = Math.max(0, 1 - dist / maxR);
        const gPow = glow * glow * 0.42;
        const idx = (y * this.w + x) * 4;
        this.buf[idx] = Math.min(255, Math.round(baseR * (1 - gPow) + glowRgb[0] * gPow));
        this.buf[idx + 1] = Math.min(255, Math.round(baseG * (1 - gPow) + glowRgb[1] * gPow));
        this.buf[idx + 2] = Math.min(255, Math.round(baseB * (1 - gPow) + glowRgb[2] * gPow));
        this.buf[idx + 3] = 255;
      }
    }
  }

  fillRoundRect(x0, y0, w, h, r, rgb, alpha = 1) {
    const x1 = x0 + w;
    const y1 = y0 + h;
    const minX = Math.max(0, Math.floor(x0 - 1));
    const maxX = Math.min(this.w - 1, Math.ceil(x1 + 1));
    const minY = Math.max(0, Math.floor(y0 - 1));
    const maxY = Math.min(this.h - 1, Math.ceil(y1 + 1));
    for (let y = minY; y <= maxY; y += 1) {
      for (let x = minX; x <= maxX; x += 1) {
        const qx = Math.max(x0 + r - x, 0, x - (x1 - r));
        const qy = Math.max(y0 + r - y, 0, y - (y1 - r));
        const dist = Math.sqrt(qx * qx + qy * qy) - r;
        const cov = Math.max(0, Math.min(1, 0.5 - dist));
        if (cov > 0) this.blendPixel(x, y, rgb[0], rgb[1], rgb[2], cov * alpha);
      }
    }
  }

  fillCircle(cx, cy, r, rgb, alpha = 1) {
    const minX = Math.max(0, Math.floor(cx - r - 1));
    const maxX = Math.min(this.w - 1, Math.ceil(cx + r + 1));
    const minY = Math.max(0, Math.floor(cy - r - 1));
    const maxY = Math.min(this.h - 1, Math.ceil(cy + r + 1));
    for (let y = minY; y <= maxY; y += 1) {
      for (let x = minX; x <= maxX; x += 1) {
        const d = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2) - r;
        const cov = Math.max(0, Math.min(1, 0.5 - d));
        if (cov > 0) this.blendPixel(x, y, rgb[0], rgb[1], rgb[2], cov * alpha);
      }
    }
  }

  strokeCircle(cx, cy, r, thickness, rgb, alpha = 1) {
    const half = thickness * 0.5;
    const minX = Math.max(0, Math.floor(cx - r - half - 2));
    const maxX = Math.min(this.w - 1, Math.ceil(cx + r + half + 2));
    const minY = Math.max(0, Math.floor(cy - r - half - 2));
    const maxY = Math.min(this.h - 1, Math.ceil(cy + r + half + 2));
    for (let y = minY; y <= maxY; y += 1) {
      for (let x = minX; x <= maxX; x += 1) {
        const d = Math.abs(Math.sqrt((x - cx) ** 2 + (y - cy) ** 2) - r) - half;
        const cov = Math.max(0, Math.min(1, 0.5 - d));
        if (cov > 0) this.blendPixel(x, y, rgb[0], rgb[1], rgb[2], cov * alpha);
      }
    }
  }

  fillLine(x0, y0, x1, y1, thickness, rgb, alpha = 1) {
    const half = thickness * 0.5;
    const minX = Math.max(0, Math.floor(Math.min(x0, x1) - half - 2));
    const maxX = Math.min(this.w - 1, Math.ceil(Math.max(x0, x1) + half + 2));
    const minY = Math.max(0, Math.floor(Math.min(y0, y1) - half - 2));
    const maxY = Math.min(this.h - 1, Math.ceil(Math.max(y0, y1) + half + 2));
    const dx = x1 - x0;
    const dy = y1 - y0;
    const len2 = dx * dx + dy * dy || 1;
    for (let y = minY; y <= maxY; y += 1) {
      for (let x = minX; x <= maxX; x += 1) {
        const t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / len2));
        const px = x0 + t * dx;
        const py = y0 + t * dy;
        const d = Math.sqrt((x - px) ** 2 + (y - py) ** 2) - half;
        const cov = Math.max(0, Math.min(1, 0.5 - d));
        if (cov > 0) this.blendPixel(x, y, rgb[0], rgb[1], rgb[2], cov * alpha);
      }
    }
  }
}

function renderProductPng(kind, accentHex) {
  const c = new Canvas(400, 400);
  const accent = hexToRgb(accentHex);
  const darkTop = [22, 24, 30];
  const darkBot = [14, 15, 20];
  const surface = [40, 44, 54];
  const lightSurface = [238, 242, 248];
  const muted = [132, 142, 164];

  c.fillGradientBg(darkTop, darkBot, accent);
  c.fillRoundRect(34, 34, 332, 332, 46, [255, 255, 255], 0.05);
  c.fillRoundRect(46, 46, 308, 308, 38, [18, 20, 26], 0.72);

  switch (kind) {
    case 'keyboard': {
      c.fillRoundRect(68, 126, 264, 148, 22, surface, 1);
      c.fillRoundRect(74, 132, 252, 136, 18, [26, 29, 36], 1);
      for (let row = 0; row < 4; row += 1) {
        for (let col = 0; col < 10; col += 1) {
          if (row === 3 && col >= 2 && col <= 6) continue;
          const isEsc = row === 0 && col === 0;
          const kw = isEsc ? 26 : 19;
          const color = isEsc ? accent : col === 9 && row === 2 ? [110, 120, 145] : lightSurface;
          c.fillRoundRect(84 + col * 23, 144 + row * 28, kw, 21, 5, color, isEsc ? 1 : 0.88);
        }
      }
      c.fillRoundRect(130, 228, 110, 21, 6, lightSurface, 0.9);
      break;
    }
    case 'cable': {
      c.strokeCircle(200, 196, 76, 16, surface, 1);
      c.strokeCircle(200, 196, 76, 9, accent, 0.92);
      c.fillRoundRect(114, 248, 42, 54, 10, lightSurface, 0.92);
      c.fillRoundRect(244, 248, 42, 54, 10, lightSurface, 0.92);
      c.fillRoundRect(123, 296, 24, 20, 4, muted, 1);
      c.fillRoundRect(253, 296, 24, 20, 4, muted, 1);
      break;
    }
    case 'keycap': {
      c.fillRoundRect(110, 110, 180, 180, 34, surface, 1);
      c.fillRoundRect(126, 120, 148, 148, 26, accent, 0.95);
      c.fillRoundRect(154, 152, 92, 18, 8, [255, 255, 255], 0.92);
      c.fillRoundRect(154, 184, 60, 18, 8, [255, 255, 255], 0.72);
      break;
    }
    case 'lamp': {
      c.fillRoundRect(112, 290, 176, 22, 11, surface, 1);
      c.fillRoundRect(190, 142, 20, 156, 10, muted, 1);
      c.fillRoundRect(88, 118, 224, 30, 15, lightSurface, 0.95);
      c.fillRoundRect(102, 146, 196, 14, 7, accent, 0.95);
      c.fillCircle(200, 194, 48, accent, 0.22);
      break;
    }
    case 'deskmat': {
      c.fillRoundRect(68, 124, 264, 154, 24, surface, 1);
      c.fillRoundRect(76, 132, 248, 138, 18, [25, 28, 36], 1);
      c.strokeCircle(155, 200, 56, 3, accent, 0.75);
      c.strokeCircle(155, 200, 34, 3, accent, 0.55);
      c.strokeCircle(252, 188, 44, 3, lightSurface, 0.35);
      c.fillRoundRect(244, 236, 62, 16, 8, accent, 0.9);
      break;
    }
    case 'hub': {
      c.fillRoundRect(92, 158, 216, 84, 24, lightSurface, 0.92);
      c.fillRoundRect(106, 170, 188, 60, 18, surface, 1);
      for (let i = 0; i < 4; i += 1) {
        c.fillRoundRect(122 + i * 42, 190, 26, 18, 6, i === 0 ? accent : muted, 0.95);
      }
      c.fillCircle(200, 138, 10, accent, 0.95);
      break;
    }
    case 'hoodie': {
      c.fillCircle(200, 128, 44, surface, 1);
      c.fillRoundRect(114, 136, 172, 158, 32, surface, 1);
      c.fillRoundRect(82, 146, 52, 126, 22, surface, 0.96);
      c.fillRoundRect(266, 146, 52, 126, 22, surface, 0.96);
      c.fillCircle(200, 134, 24, [18, 20, 26], 1);
      c.fillRoundRect(156, 182, 88, 34, 12, accent, 0.92);
      c.fillRoundRect(148, 236, 104, 38, 14, [26, 29, 36], 0.9);
      break;
    }
    case 'cap': {
      c.fillCircle(196, 194, 78, surface, 1);
      c.fillRoundRect(118, 192, 156, 56, 18, surface, 1);
      c.fillRoundRect(172, 220, 148, 32, 16, accent, 0.95);
      c.fillCircle(196, 118, 11, lightSurface, 0.9);
      c.fillRoundRect(174, 164, 42, 24, 8, lightSurface, 0.85);
      break;
    }
    case 'tote': {
      c.strokeCircle(200, 144, 42, 12, lightSurface, 0.88);
      c.fillRoundRect(112, 148, 176, 166, 22, lightSurface, 0.92);
      c.fillRoundRect(136, 194, 128, 66, 14, accent, 0.92);
      c.fillRoundRect(154, 214, 92, 12, 6, [255, 255, 255], 0.9);
      break;
    }
    case 'thermos': {
      c.fillRoundRect(166, 94, 68, 34, 12, muted, 1);
      c.fillRoundRect(146, 122, 108, 188, 32, surface, 1);
      c.fillRoundRect(146, 174, 108, 64, 6, accent, 0.94);
      c.fillRoundRect(168, 198, 64, 14, 7, [255, 255, 255], 0.92);
      break;
    }
    case 'organizer': {
      c.fillRoundRect(88, 124, 224, 152, 28, surface, 1);
      c.fillRoundRect(104, 140, 90, 56, 14, [24, 27, 34], 1);
      c.fillRoundRect(206, 140, 90, 56, 14, accent, 0.9);
      c.fillRoundRect(104, 208, 192, 52, 14, lightSurface, 0.88);
      break;
    }
    default: {
      // Holographic stickers pack
      c.fillRoundRect(104, 112, 124, 124, 28, accent, 0.92);
      c.fillRoundRect(172, 164, 124, 124, 28, lightSurface, 0.9);
      c.fillCircle(234, 226, 32, accent, 0.95);
      c.fillLine(132, 174, 196, 174, 10, [255, 255, 255], 0.9);
      break;
    }
  }

  return encodePng(400, 400, c.buf);
}

export function ensureSeedPngAsset(seedAssetsDir, filename, kind, accentHex) {
  fs.mkdirSync(seedAssetsDir, { recursive: true });
  const target = path.join(seedAssetsDir, filename);
  if (!fs.existsSync(target) || fs.statSync(target).size === 0) {
    const png = renderProductPng(kind, accentHex);
    const tmp = `${target}.${process.pid}.${crypto.randomBytes(4).toString('hex')}.tmp`;
    fs.writeFileSync(tmp, png);
    try {
      fs.renameSync(tmp, target);
    } catch {
      try {
        fs.unlinkSync(tmp);
      } catch {}
    }
  }
  return target;
}

export function copySeedAssetToUploads(seedAssetPath, uploadDir) {
  fs.mkdirSync(uploadDir, { recursive: true });
  const bytes = fs.readFileSync(seedAssetPath);
  const storageKey = `${crypto.randomUUID()}.png`;
  fs.writeFileSync(path.join(uploadDir, storageKey), bytes);
  const sha256 = crypto.createHash('sha256').update(bytes).digest('hex');
  return {
    storageKey,
    mimeType: 'image/png',
    byteSize: bytes.length,
    sha256,
  };
}
