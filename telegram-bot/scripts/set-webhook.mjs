const token = process.env.BOT_TOKEN;
const workerUrl = process.env.WORKER_URL;
const secret = process.env.WEBHOOK_SECRET;

if (!token || !workerUrl || !secret) throw new Error("Set BOT_TOKEN, WORKER_URL and WEBHOOK_SECRET in the environment first.");
const worker = new URL(workerUrl);
if (worker.protocol !== "https:" || worker.pathname !== "/" || worker.search || worker.hash) throw new Error("WORKER_URL must be an HTTPS origin without a path, query or fragment.");
if (!/^[A-Za-z0-9_-]{32,256}$/.test(secret)) throw new Error("WEBHOOK_SECRET must be 32-256 letters, digits, underscores or hyphens.");
const response = await fetch(`https://api.telegram.org/bot${token}/setWebhook`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    url: `${worker.origin}/telegram/webhook`,
    secret_token: secret,
    allowed_updates: ["message"],
  }),
});
const result = await response.json();
if (!response.ok || result.ok !== true) throw new Error("Telegram rejected the webhook settings.");
process.stdout.write("Webhook set.\n");
