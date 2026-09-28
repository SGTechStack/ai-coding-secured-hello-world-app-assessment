import fs from 'node:fs';
import { env } from './env';

/**
 * Reads the backend's structured (ECS JSON-lines) log file. Used to check
 * the PRD's audit-logging and "never log passwords" requirements.
 * Captures the file offset at scenario start so assertions only look at
 * lines produced during the scenario.
 */
export class BackendLog {
  private readonly startOffset: number;

  constructor(readonly file = env.backendLogFile) {
    this.startOffset = BackendLog.available(file) ? fs.statSync(file).size : 0;
  }

  static available(file = env.backendLogFile): boolean {
    return fs.existsSync(file);
  }

  /** Log text written since this scenario started. */
  sinceStart(): string {
    if (!BackendLog.available(this.file)) return '';
    const fd = fs.openSync(this.file, 'r');
    try {
      const size = fs.fstatSync(fd).size;
      // Handle rotation/truncation by reading the whole file.
      const from = size >= this.startOffset ? this.startOffset : 0;
      const buf = Buffer.alloc(size - from);
      fs.readSync(fd, buf, 0, buf.length, from);
      return buf.toString('utf8');
    } finally {
      fs.closeSync(fd);
    }
  }

  /** ECS "message" fields written since scenario start. */
  messages(): string[] {
    return this.sinceStart()
      .split('\n')
      .filter(Boolean)
      .map((line) => {
        try {
          return String((JSON.parse(line) as { message?: unknown }).message ?? line);
        } catch {
          return line;
        }
      });
  }
}
