export class SlidingWindowLimiter {
  constructor({ maxBuckets = 10_000 } = {}) {
    this.buckets = new Map();
    this.maxBuckets = maxBuckets;
    this.lastSweep = Date.now();
  }

  check(key, { limit, windowMs }) {
    const now = Date.now();
    if (now - this.lastSweep > 60_000 || this.buckets.size > this.maxBuckets) {
      this.#sweep(now, windowMs);
    }

    let entry = this.buckets.get(key);
    if (!entry || entry.resetAt <= now) {
      entry = { count: 0, resetAt: now + windowMs };
      this.buckets.set(key, entry);
    }

    entry.count += 1;
    const allowed = entry.count <= limit;
    const retryAfterSec = Math.max(1, Math.ceil((entry.resetAt - now) / 1000));
    return {
      allowed,
      remaining: Math.max(0, limit - entry.count),
      retryAfterSec,
    };
  }

  #sweep(now) {
    this.lastSweep = now;
    for (const [k, v] of this.buckets.entries()) {
      if (v.resetAt <= now) {
        this.buckets.delete(k);
      }
    }
    if (this.buckets.size > this.maxBuckets) {
      const overflow = this.buckets.size - Math.floor(this.maxBuckets * 0.75);
      let deleted = 0;
      for (const k of this.buckets.keys()) {
        this.buckets.delete(k);
        deleted += 1;
        if (deleted >= overflow) break;
      }
    }
  }
}
