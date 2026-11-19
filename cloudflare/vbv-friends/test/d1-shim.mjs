// A just-big-enough stand-in for the parts of Cloudflare D1 the Worker uses, on top of node:sqlite,
// so the real schema and the real queries can be tested without a Cloudflare account.
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";

export function createDb() {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("../migrations/0001_init.sql", import.meta.url), "utf8"));
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
