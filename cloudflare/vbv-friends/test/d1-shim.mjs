// A just-big-enough stand-in for the parts of Cloudflare D1 the Worker uses, on top of node:sqlite,
// so the real schema and the real queries can be tested without a Cloudflare account.
import { DatabaseSync } from "node:sqlite";
import { readFileSync, readdirSync } from "node:fs";

export function createDb() {
  const sqlite = new DatabaseSync(":memory:");
  // Every migration, in file-name order, exactly as `wrangler d1 migrations apply` runs them.
  const dir = new URL("../migrations/", import.meta.url);
  for (const file of readdirSync(dir).filter((name) => name.endsWith(".sql")).sort()) {
    sqlite.exec(readFileSync(new URL(file, dir), "utf8"));
  }
  return {
    sqlite,
    prepare(sql) {
      let args = [];
      const stmt = {
        bind(...values) {
          args = values;
          return stmt;
        },
        async first() {
          const row = sqlite.prepare(sql).get(...args);
          return row === undefined ? null : { ...row };
        },
        async all() {
          return { results: sqlite.prepare(sql).all(...args).map((row) => ({ ...row })) };
        },
        async run() {
          const info = sqlite.prepare(sql).run(...args);
          return { success: true, meta: { changes: Number(info.changes) } };
        },
      };
      return stmt;
    },
  };
}
