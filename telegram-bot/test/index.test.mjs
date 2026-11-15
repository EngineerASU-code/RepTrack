import assert from "node:assert/strict";
import test from "node:test";
import { gzipSync } from "node:zlib";
import worker, { claimUpdate, finishUpdate, matchesPair, parseDelivery, parseExchangeHeader, parseRouteRegistration } from "../src/index.mjs";

function updateDb() {
  const rows = new Map();
  return {
    rows,
    prepare(sql) {
      return { bind(...args) { return {
        async run() {
          if (sql.startsWith("DELETE FROM telegram_updates")) return { meta: { changes: 0 } };
          if (sql.startsWith("INSERT OR IGNORE")) {
            if (rows.has(args[0])) return { meta: { changes: 0 } };
            rows.set(args[0], { status: "PROCESSING", claimed_at: args[1] }); return { meta: { changes: 1 } };
          }
          if (sql.startsWith("UPDATE telegram_updates")) {
            if (sql.includes("claimed_at=? WHERE update_id=? AND status='PROCESSING' AND claimed_at=?")) {
              const row = rows.get(args[2]);
              if (row?.status === "PROCESSING" && row.claimed_at === args[3]) {
                row.status = args[0]; row.claimed_at = args[1]; return { meta: { changes: 1 } };
              }
              return { meta: { changes: 0 } };
            }
            const row = rows.get(args[1]);
            if (row && row.status !== "DONE" && (row.status === "FAILED" || row.claimed_at <= args[2])) {
              row.status = "PROCESSING"; row.claimed_at = args[0]; return { meta: { changes: 1 } };
            }
            return { meta: { changes: 0 } };
          }
          throw new Error(`Unexpected SQL: ${sql}`);
        },
        async first() { return rows.get(args[0]) ?? null; },
      }; } };
    },
  };
}

const linkId = "11111111-1111-4111-8111-111111111111";
const trainerDeviceId = "22222222-2222-4222-8222-222222222222";
const traineeDeviceId = "33333333-3333-4333-8333-333333333333";
const packageId = "44444444-4444-4444-8444-444444444444";
const sessionId = "55555555-5555-4555-8555-555555555555";
const fingerprint = "A1B2 C3D4 E5F6 1020 3040";

test("route registration parses role and link identity", () => {
  const registration = parseRouteRegistration(new TextEncoder().encode(JSON.stringify({
    format: "reptrack-telegram-route", version: 1, linkId, role: "TRAINER",
    localDeviceId: trainerDeviceId, remoteDeviceId: traineeDeviceId, fingerprint,
  })));
  assert.equal(registration.role, "TRAINER");
  assert.equal(registration.local_device_id, trainerDeviceId);
});

test("webhook updates distinguish done, busy, and atomically reclaimable leases", async () => {
  const db = updateDb();
  assert.equal((await claimUpdate(db, 10)).status, "CLAIMED");
  assert.equal((await claimUpdate(db, 10)).status, "BUSY");
  db.rows.get(10).status = "DONE";
  assert.equal((await claimUpdate(db, 10)).status, "DONE");
  assert.equal((await claimUpdate(db, 11)).status, "CLAIMED");
  db.rows.get(11).claimed_at = 0;
  const results = await Promise.all([claimUpdate(db, 11), claimUpdate(db, 11)]);
  assert.deepEqual(results.map(result => result.status).sort(), ["BUSY", "CLAIMED"]);
  const firstLease = results.find(result => result.status === "CLAIMED");
  firstLease.claimedAt = 0;
  db.rows.get(11).claimed_at = 0;
  const replacement = await claimUpdate(db, 11);
  await finishUpdate(db, 11, "DONE", firstLease.claimedAt);
  assert.equal(db.rows.get(11).status, "PROCESSING");
  await finishUpdate(db, 11, "DONE", replacement.claimedAt);
  assert.equal(db.rows.get(11).status, "DONE");
});

test("webhook ACKs completed work, retries a live lease, and processes a reclaimed update", async () => {
  const DB = updateDb();
  const env = { DB, BOT_TOKEN: "test", WEBHOOK_SECRET: "secret" };
  const send = () => worker.fetch(new Request("https://worker.test/telegram/webhook", {
    method: "POST", headers: { "X-Telegram-Bot-Api-Secret-Token": "secret", "Content-Type": "application/json" }, body: JSON.stringify({ update_id: 20 }),
  }), env);
  const first = await send();
  assert.equal(first.status, 200);
  assert.equal(DB.rows.get(20).status, "DONE");
  assert.equal((await send()).status, 200);
  DB.rows.set(21, { status: "PROCESSING", claimed_at: Date.now() });
  const busy = await worker.fetch(new Request("https://worker.test/telegram/webhook", {
    method: "POST", headers: { "X-Telegram-Bot-Api-Secret-Token": "secret", "Content-Type": "application/json" }, body: JSON.stringify({ update_id: 21 }),
  }), env);
  assert.equal(busy.status, 503);
  DB.rows.get(21).claimed_at = 0;
  const reclaimed = await worker.fetch(new Request("https://worker.test/telegram/webhook", {
    method: "POST", headers: { "X-Telegram-Bot-Api-Secret-Token": "secret", "Content-Type": "application/json" }, body: JSON.stringify({ update_id: 21 }),
  }), env);
  assert.equal(reclaimed.status, 200);
  assert.equal(DB.rows.get(21).status, "DONE");
});

test("route registration only joins opposite ends of the same trusted device pair", () => {
  const trainer = parseRouteRegistration(new TextEncoder().encode(JSON.stringify({
    format: "reptrack-telegram-route", version: 1, linkId, role: "TRAINER",
    localDeviceId: trainerDeviceId, remoteDeviceId: traineeDeviceId, fingerprint,
  })));
  const trainee = parseRouteRegistration(new TextEncoder().encode(JSON.stringify({
    format: "reptrack-telegram-route", version: 1, linkId, role: "TRAINEE",
    localDeviceId: traineeDeviceId, remoteDeviceId: trainerDeviceId, fingerprint,
  })));
  assert.equal(matchesPair(trainer, trainee), true);
  assert.equal(matchesPair(trainer, { ...trainee, local_device_id: trainerDeviceId }), false);
});

test("delivery bundle separates a readable PDF and the addressed encrypted package", async () => {
  const now = Date.now();
  const header = {
    packageId, linkId, senderDeviceId: traineeDeviceId, recipientDeviceId: trainerDeviceId,
    type: "result", createdAt: now, expiresAt: now + 30 * 24 * 60 * 60 * 1000, version: 1, programVersion: null,
  };
  const encrypted = {
    format: "reptrack-exchange", version: 1,
    aad: Buffer.from(JSON.stringify(header)).toString("base64"),
    nonce: Buffer.alloc(12, 7).toString("base64"),
    ciphertext: Buffer.alloc(32, 9).toString("base64"),
  };
  const pdf = Buffer.from("%PDF-1.7\nreadable report");
  const bundle = gzipSync(Buffer.from(JSON.stringify({
    format: "reptrack-telegram-delivery", version: 1, linkId, packageId,
    senderDeviceId: traineeDeviceId, recipientDeviceId: trainerDeviceId, sessionId,
    planName: "Тренировка", startedAt: now - 60_000, endedAt: now,
    durationMinutes: 1, exerciseCount: 2, completedWorkSets: 5,
    reportPdf: pdf.toString("base64"), resultPackage: Buffer.from(JSON.stringify(encrypted)).toString("base64"),
  })));

  const delivery = await parseDelivery(new Uint8Array(bundle));
  assert.equal(delivery.linkId, linkId);
  assert.deepEqual(Buffer.from(delivery.reportPdf), pdf);
  assert.deepEqual(Buffer.from(delivery.resultPackage), Buffer.from(JSON.stringify(encrypted)));
});

test("delivery parser rejects a mismatched envelope recipient", async () => {
  const now = Date.now();
  const header = {
    packageId, linkId, senderDeviceId: traineeDeviceId,
    recipientDeviceId: "66666666-6666-4666-8666-666666666666", type: "result",
    createdAt: now, expiresAt: now + 60_000, version: 1, programVersion: null,
  };
  const encrypted = {
    format: "reptrack-exchange", version: 1,
    aad: Buffer.from(JSON.stringify(header)).toString("base64"),
    nonce: Buffer.alloc(12).toString("base64"), ciphertext: Buffer.alloc(16).toString("base64"),
  };
  const bundle = gzipSync(Buffer.from(JSON.stringify({
    format: "reptrack-telegram-delivery", version: 1, linkId, packageId,
    senderDeviceId: traineeDeviceId, recipientDeviceId: trainerDeviceId, sessionId,
    planName: "Тренировка", startedAt: now - 60_000, endedAt: now,
    durationMinutes: 1, exerciseCount: 0, completedWorkSets: 0,
    reportPdf: Buffer.from("%PDF-test").toString("base64"), resultPackage: Buffer.from(JSON.stringify(encrypted)).toString("base64"),
  })));
  await assert.rejects(parseDelivery(new Uint8Array(bundle)), /does not match/);
});

test("program routing accepts a current package only when its header has a version", () => {
  const now = Date.now();
  const header = {
    packageId, linkId, senderDeviceId: trainerDeviceId, recipientDeviceId: traineeDeviceId,
    type: "program", createdAt: now, expiresAt: now + 60_000, version: 1, programVersion: 4,
  };
  const bytes = new TextEncoder().encode(JSON.stringify({
    format: "reptrack-exchange", version: 1,
    aad: Buffer.from(JSON.stringify(header)).toString("base64"),
    nonce: Buffer.alloc(12).toString("base64"), ciphertext: Buffer.alloc(16).toString("base64"),
  }));

  assert.equal(parseExchangeHeader(bytes, "program").programVersion, 4);
  assert.throws(() => parseExchangeHeader(bytes, "result"), /Invalid encrypted package/);
});
