const maxTelegramFileBytes = 16 * 1024 * 1024;
const maxUncompressedBytes = 20 * 1024 * 1024;
const maxPdfBytes = 4 * 1024 * 1024;
const maxResultBytes = 8 * 1024 * 1024;
const updateLeaseMs = 60_000;
const dayMs = 24 * 60 * 60 * 1000;

export default {
  async fetch(request, env) {
    if (request.method !== "POST" || new URL(request.url).pathname !== "/telegram/webhook") {
      return new Response("Not found", { status: 404 });
    }
    if (!env.BOT_TOKEN || !env.WEBHOOK_SECRET || !env.DB) return new Response("Not configured", { status: 503 });
    if (request.headers.get("X-Telegram-Bot-Api-Secret-Token") !== env.WEBHOOK_SECRET) return new Response("Unauthorized", { status: 401 });

    let update;
    let claimedAt = null;
    try {
      update = await request.json();
      if (!Number.isSafeInteger(update.update_id)) return Response.json({ ok: false }, { status: 400 });
      const claim = await claimUpdate(env.DB, update.update_id);
      if (claim.status === "DONE") return Response.json({ ok: true });
      if (claim.status === "BUSY") return Response.json({ ok: false }, { status: 503, headers: { "Retry-After": "5" } });
      claimedAt = claim.claimedAt;
      await handleUpdate(update, env);
      await finishUpdate(env.DB, update.update_id, "DONE", claimedAt);
      return Response.json({ ok: true });
    } catch {
      if (Number.isSafeInteger(update?.update_id) && claimedAt !== null) await finishUpdate(env.DB, update.update_id, "FAILED", claimedAt).catch(() => {});
      return Response.json({ ok: false }, { status: 500 });
    }
  },
};

export function parseRouteRegistration(bytes) {
  if (bytes.byteLength > 64_000) throw new Error("Registration file is too large");
  const value = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  if (value.format !== "reptrack-telegram-route" || value.version !== 1) throw new Error("Unknown route registration format");
  if (!isId(value.linkId) || !isId(value.localDeviceId) || !isId(value.remoteDeviceId)) throw new Error("Invalid route identifiers");
  if (value.role !== "TRAINER" && value.role !== "TRAINEE") throw new Error("Invalid route role");
  if (!/^(?:[0-9A-F]{4} ){4}[0-9A-F]{4}$/.test(value.fingerprint)) throw new Error("Invalid link fingerprint");
  return {
    link_id: value.linkId,
    role: value.role,
    local_device_id: value.localDeviceId,
    remote_device_id: value.remoteDeviceId,
    fingerprint: value.fingerprint,
  };
}

export async function parseDelivery(bytes) {
  if (bytes.byteLength < 18 || bytes.byteLength > maxTelegramFileBytes) throw new Error("Delivery file size is invalid");
  if (bytes[0] !== 0x1f || bytes[1] !== 0x8b) throw new Error("Delivery file is not gzip data");
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
  const reader = stream.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxUncompressedBytes) throw new Error("Delivery contents are too large");
      chunks.push(value);
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally { reader.releaseLock(); }
  const plain = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { plain.set(chunk, offset); offset += chunk.byteLength; }
  const value = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(plain));
  plain.fill(0);
  if (value.format !== "reptrack-telegram-delivery" || value.version !== 1) throw new Error("Unknown delivery format");
  if (!isId(value.linkId) || !isId(value.packageId) || !isId(value.sessionId) || !isId(value.senderDeviceId) || !isId(value.recipientDeviceId)) throw new Error("Invalid delivery identifiers");
  if (typeof value.planName !== "string" || value.planName.length > 80) throw new Error("Invalid workout name");
  if (!Number.isSafeInteger(value.startedAt) || !Number.isSafeInteger(value.endedAt) || value.endedAt < value.startedAt) throw new Error("Invalid workout dates");
  if (!Number.isSafeInteger(value.durationMinutes) || value.durationMinutes < 0 || !Number.isSafeInteger(value.exerciseCount) || value.exerciseCount < 0 || !Number.isSafeInteger(value.completedWorkSets) || value.completedWorkSets < 0) throw new Error("Invalid workout summary");
  const reportPdf = decodeBase64(value.reportPdf, maxPdfBytes);
  const resultPackage = decodeBase64(value.resultPackage, maxResultBytes);
  if (new TextDecoder().decode(reportPdf.subarray(0, 4)) !== "%PDF") throw new Error("Invalid PDF report");
  const header = parseResultHeader(resultPackage);
  if (header.packageId !== value.packageId || header.linkId !== value.linkId || header.senderDeviceId !== value.senderDeviceId || header.recipientDeviceId !== value.recipientDeviceId) throw new Error("Delivery metadata does not match the encrypted report");
  return { ...value, reportPdf, resultPackage };
}

export function parseResultHeader(bytes) {
  return parseExchangeHeader(bytes, "result");
}

export function parseExchangeHeader(bytes, type) {
  if (bytes.byteLength < 1 || bytes.byteLength > maxResultBytes) throw new Error("Encrypted package size is invalid");
  const root = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  if (root.format !== "reptrack-exchange" || root.version !== 1) throw new Error("Unknown encrypted report format");
  const headerBytes = decodeBase64(root.aad, 4096);
  const header = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(headerBytes));
  const nonce = decodeBase64(root.nonce, 12);
  const ciphertext = decodeBase64(root.ciphertext, maxResultBytes);
  if (nonce.byteLength !== 12 || ciphertext.byteLength < 16 || header.version !== 1 || header.type !== type) throw new Error("Invalid encrypted package");
  if (type === "program" && (!Number.isSafeInteger(header.programVersion) || header.programVersion < 1)) throw new Error("Invalid program version");
  if (type === "result" && header.programVersion !== null) throw new Error("Invalid result version metadata");
  if (!isId(header.packageId) || !isId(header.linkId) || !isId(header.senderDeviceId) || !isId(header.recipientDeviceId)) throw new Error("Invalid report header identifiers");
  if (!Number.isSafeInteger(header.createdAt) || header.createdAt > Date.now() + 5 * 60_000 || !Number.isSafeInteger(header.expiresAt) || header.expiresAt <= Date.now() || header.expiresAt <= header.createdAt || header.expiresAt - header.createdAt > 30 * dayMs) throw new Error("Encrypted report has expired");
  return header;
}

async function handleUpdate(update, env) {
  const message = update.message;
  if (!message) return;
  const chatId = String(message.chat?.id ?? "");
  if (!chatId || message.chat?.type !== "private") {
    if (chatId) await sendMessage(env, chatId, "Используй RepTrack-бота в личном чате.");
    return;
  }
  const text = typeof message.text === "string" ? message.text.trim() : "";
  const [rawCommand, argument] = text.split(/\s+/, 2);
  const command = (rawCommand ?? "").split("@")[0].toLowerCase();
  if (command === "/start") return sendMessage(env, chatId, "RepTrack Relay.\n\nВ приложении открой Настройки → Защищённый обмен и отправь в этот чат файл регистрации каждой связи. Оба участника должны сделать это со своих устройств. После подтверждения бот сможет пересылать отчёты и программы.\n\n/help — помощь\n/status — состояние связей\n/disconnect ID — отключить одну связь\n/disconnect — отключить все связи этого чата");
  if (command === "/help") return sendMessage(env, chatId, "Открой бота и нажми Start. Затем в RepTrack отправь сюда файл «Подключить эту связь к Telegram-боту» с обоих устройств. После сообщения «Связь активна» подопечный сможет завершить тренировку и отправить читаемый PDF и зашифрованный пакет импорта. Тренер также может отправить зашифрованный .rptprogram.\n\n/status показывает ID и отпечаток маршрута. /disconnect ID удаляет указанный маршрут, /disconnect без ID отключает все маршруты этого Telegram-чата. Локальная криптографическая связь в приложении от этого не меняется.");
  if (command === "/status") {
    const result = await env.DB.prepare("SELECT link_id,fingerprint,CASE WHEN trainer_chat_id=? THEN 'Тренер' ELSE 'Подопечный' END AS role FROM telegram_routes WHERE trainer_chat_id=? OR trainee_chat_id=? ORDER BY created_at").bind(chatId, chatId, chatId).all();
    const routes = result.results ?? [];
    const list = routes.map(route => `${route.link_id.slice(0, 8)} · ${route.role} · ${route.fingerprint}`).join("\n");
    const registrations = await env.DB.prepare("SELECT link_id,role,fingerprint FROM relay_registrations WHERE chat_id=? ORDER BY created_at").bind(chatId).all();
    const activeIds = new Set(routes.map(route => route.link_id));
    const pending = (registrations.results ?? []).filter(row => !activeIds.has(row.link_id));
    const pendingText = pending.length ? `\nЖдут второго участника:\n${pending.map(row => `${row.link_id.slice(0, 8)} · ${row.role === "TRAINER" ? "Тренер" : "Подопечный"} · ${row.fingerprint}`).join("\n")}` : "";
    return sendMessage(env, chatId, `Активных маршрутов: ${routes.length}.${list ? `\n${list}` : ""}${pendingText}\nБот хранит только маршруты между Telegram-чатами, не отчёты.`);
  }
  if (command === "/disconnect") return disconnectChat(env, chatId, argument);

  if (message.document) {
    const name = (message.document.file_name ?? "").toLowerCase();
    if (!name.endsWith(".rptrelay") && !name.endsWith(".rptdelivery") && !name.endsWith(".rptprogram")) return sendMessage(env, chatId, "Этот файл бот не обрабатывает. Отправь .rptrelay для настройки связи, .rptdelivery с отчётом или .rptprogram с программой.");
    const declaredSize = message.document.file_size;
    if (declaredSize !== undefined && (!Number.isSafeInteger(declaredSize) || declaredSize < 1 || declaredSize > maxTelegramFileBytes)) return sendMessage(env, chatId, "Размер файла не подходит для пересылки.");
    const file = await downloadDocument(env, message.document);
    if (name.endsWith(".rptrelay")) {
      let registration;
      try { registration = parseRouteRegistration(file); }
      catch { return sendMessage(env, chatId, "Файл регистрации повреждён или не является файлом RepTrack."); }
      return registerRoute(env, chatId, registration);
    }
    if (name.endsWith(".rptdelivery")) {
      let delivery;
      try { delivery = await parseDelivery(file); }
      catch { return sendMessage(env, chatId, "Отчёт повреждён, устарел или не совпадает с зашифрованным пакетом."); }
      return deliverWorkout(env, chatId, message, delivery);
    }
    let header;
    try { header = parseExchangeHeader(file, "program"); }
    catch { return sendMessage(env, chatId, "Файл программы повреждён или устарел."); }
    return deliverProgram(env, chatId, file, header);
  }
  if (text) return sendMessage(env, chatId, "Нужен файл регистрации, отчёт или программа из RepTrack. Напиши /help для инструкции.");
}

async function registerRoute(env, chatId, registration) {
  const db = env.DB;
  const existing = await db.prepare("SELECT chat_id FROM relay_registrations WHERE link_id=? AND role=?").bind(registration.link_id, registration.role).first();
  if (existing && existing.chat_id !== chatId) return sendMessage(env, chatId, "Эта сторона связи уже зарегистрирована в другом чате. Отключи там маршрут командой /disconnect перед переносом.");
  const oppositeRole = registration.role === "TRAINER" ? "TRAINEE" : "TRAINER";
  const opposite = await db.prepare("SELECT * FROM relay_registrations WHERE link_id=? AND role=?").bind(registration.link_id, oppositeRole).first();
  if (opposite && !matchesPair(registration, opposite)) return sendMessage(env, chatId, "Файл относится к другой связи или устройству. Проверь, что обе стороны отправили регистрацию одной и той же связи.");
  const now = Date.now();
  await db.prepare("INSERT INTO relay_registrations(link_id,role,local_device_id,remote_device_id,fingerprint,chat_id,created_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(link_id,role) DO UPDATE SET created_at=excluded.created_at").bind(registration.link_id, registration.role, registration.local_device_id, registration.remote_device_id, registration.fingerprint, chatId, now).run();

  const trainer = registration.role === "TRAINER" ? await currentRegistration(db, registration.link_id, "TRAINER") : opposite;
  const trainee = registration.role === "TRAINEE" ? await currentRegistration(db, registration.link_id, "TRAINEE") : opposite;
  if (!trainer || !trainee) return sendMessage(env, chatId, "Файл регистрации принят. Жду второй участник этой связи.");
  if (!matchesPair(trainer, trainee)) return sendMessage(env, chatId, "Файл регистрации не совпадает с регистрацией второго устройства.");
  if (trainer.chat_id === trainee.chat_id) return sendMessage(env, chatId, "Для тренера и подопечного нужны отдельные личные чаты с ботом.");
  const oldRoute = await db.prepare("SELECT trainer_chat_id,trainee_chat_id FROM telegram_routes WHERE link_id=?").bind(registration.link_id).first();
  if (oldRoute && (oldRoute.trainer_chat_id !== trainer.chat_id || oldRoute.trainee_chat_id !== trainee.chat_id)) return sendMessage(env, chatId, "Для переноса маршрута сначала отключи старый маршрут командой /disconnect.");
  await db.prepare("INSERT INTO telegram_routes(link_id,trainer_chat_id,trainee_chat_id,trainer_device_id,trainee_device_id,fingerprint,created_at,last_seen_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(link_id) DO UPDATE SET last_seen_at=excluded.last_seen_at").bind(registration.link_id, trainer.chat_id, trainee.chat_id, trainer.local_device_id, trainee.local_device_id, trainer.fingerprint, now, now).run();
  await Promise.all([
    sendMessage(env, trainer.chat_id, "Связь RepTrack с подопечным подключена к Telegram-боту. Бот может видеть PDF-отчёты; зашифрованные пакеты импорта он не расшифровывает."),
    sendMessage(env, trainee.chat_id, "Связь RepTrack с тренером подключена к Telegram-боту. Отправить результат можно при завершении тренировки.")
  ]);
}

async function deliverWorkout(env, senderChatId, message, delivery) {
  const route = await env.DB.prepare("SELECT * FROM telegram_routes WHERE link_id=?").bind(delivery.linkId).first();
  if (!route || route.trainee_chat_id !== senderChatId || route.trainee_device_id !== delivery.senderDeviceId || route.trainer_device_id !== delivery.recipientDeviceId) return sendMessage(env, senderChatId, "Для этой связи нет активного маршрута бота. Проверь регистрацию с обоих устройств в /status.");
  const status = await prepareForward(env.DB, delivery.packageId, delivery.linkId, "RESULT");
  if (status === "DONE") return sendMessage(env, senderChatId, "Этот отчёт уже передан тренеру. Повторная пересылка не создала копий.");
  const firstName = cleanText(message.from?.first_name ?? "Подопечный", 64);
  const date = new Date(delivery.endedAt).toLocaleString("ru-RU", { dateStyle: "medium", timeStyle: "short" });
  const caption = `${firstName} завершил тренировку «${cleanText(delivery.planName, 80)}».\n${date} · ${delivery.exerciseCount} упражнений · ${delivery.completedWorkSets} рабочих подходов.\nЭто читаемый отчёт. Следующим файлом придёт зашифрованный пакет для импорта в RepTrack.`;
  if (status === "RECEIVED") {
    await sendDocument(env, route.trainer_chat_id, delivery.reportPdf, `RepTrack-${delivery.sessionId}.pdf`, "application/pdf", caption);
    await env.DB.prepare("UPDATE telegram_forwards SET status='PDF_SENT',updated_at=? WHERE package_id=?").bind(Date.now(), delivery.packageId).run();
  }
  await sendDocument(env, route.trainer_chat_id, delivery.resultPackage, `RepTrack-${delivery.packageId}.rptresult`, "application/vnd.reptrack.result", "Зашифрованный пакет импорта. Открой файл в RepTrack и подтверди импорт после просмотра.");
  await env.DB.prepare("UPDATE telegram_forwards SET status='DONE',updated_at=? WHERE package_id=?").bind(Date.now(), delivery.packageId).run();
  await env.DB.prepare("UPDATE telegram_routes SET last_seen_at=? WHERE link_id=?").bind(Date.now(), delivery.linkId).run();
  await sendMessage(env, senderChatId, "Отчёт передан тренеру: PDF и зашифрованный пакет импорта отправлены. Это подтверждает доставку в чат, но не импорт в RepTrack.");
}

async function deliverProgram(env, senderChatId, bytes, header) {
  const route = await env.DB.prepare("SELECT * FROM telegram_routes WHERE link_id=?").bind(header.linkId).first();
  if (!route || route.trainer_chat_id !== senderChatId || route.trainer_device_id !== header.senderDeviceId || route.trainee_device_id !== header.recipientDeviceId) return sendMessage(env, senderChatId, "Для этой связи нет активного маршрута бота. Проверь регистрацию с обоих устройств в /status.");
  const status = await prepareForward(env.DB, header.packageId, header.linkId, "PROGRAM");
  if (status === "DONE") return sendMessage(env, senderChatId, "Эта версия программы уже доставлена подопечному.");
  await sendDocument(env, route.trainee_chat_id, bytes, `RepTrack-${header.packageId}.rptprogram`, "application/vnd.reptrack.program", `Тренер отправил зашифрованную программу, версия ${header.programVersion}. Открой файл в RepTrack, чтобы посмотреть изменения и решить, принимать ли их.`);
  await env.DB.prepare("UPDATE telegram_forwards SET status='DONE',updated_at=? WHERE package_id=?").bind(Date.now(), header.packageId).run();
  await env.DB.prepare("UPDATE telegram_routes SET last_seen_at=? WHERE link_id=?").bind(Date.now(), header.linkId).run();
  await sendMessage(env, senderChatId, "Зашифрованная программа доставлена подопечному. Бот не подтверждает просмотр или принятие обновления.");
}

async function prepareForward(db, packageId, linkId, kind) {
  const now = Date.now();
  await db.prepare("DELETE FROM telegram_forwards WHERE updated_at<?").bind(now - 30 * dayMs).run();
  await db.prepare("INSERT OR IGNORE INTO telegram_forwards(package_id,link_id,kind,status,updated_at) VALUES(?,?,?,'RECEIVED',?)").bind(packageId, linkId, kind, now).run();
  const row = await db.prepare("SELECT link_id,kind,status FROM telegram_forwards WHERE package_id=?").bind(packageId).first();
  if (row?.link_id !== linkId || row?.kind !== kind) throw new Error("Package ID already belongs to another route");
  return row.status;
}

async function disconnectChat(env, chatId, linkPrefix) {
  if (linkPrefix) {
    if (!/^[0-9a-f-]{8,36}$/i.test(linkPrefix)) return sendMessage(env, chatId, "Укажи первые 8 символов ID из /status: /disconnect ID.");
    const result = await env.DB.prepare("SELECT link_id,trainer_chat_id,trainee_chat_id FROM telegram_routes WHERE link_id LIKE ? AND (trainer_chat_id=? OR trainee_chat_id=?)").bind(`${linkPrefix}%`, chatId, chatId).all();
    const routes = result.results ?? [];
    if (routes.length === 0) {
      const pending = await env.DB.prepare("SELECT link_id FROM relay_registrations WHERE link_id LIKE ? AND chat_id=?").bind(`${linkPrefix}%`, chatId).all();
      if ((pending.results ?? []).length === 1) {
        const [registration] = pending.results;
        await env.DB.prepare("DELETE FROM relay_registrations WHERE link_id=? AND chat_id=?").bind(registration.link_id, chatId).run();
        return sendMessage(env, chatId, `Ожидающая регистрация ${registration.link_id.slice(0, 8)} удалена.`);
      }
      if ((pending.results ?? []).length > 1) return sendMessage(env, chatId, "Этот ID совпал с несколькими регистрациями. Укажи больше символов ID из /status.");
      return sendMessage(env, chatId, "Маршрут с таким ID в этом чате не найден. Проверь /status.");
    }
    if (routes.length > 1) return sendMessage(env, chatId, "Этот ID совпал с несколькими маршрутами. Укажи больше символов ID из /status.");
    const [route] = routes;
    const peer = route.trainer_chat_id === chatId ? route.trainee_chat_id : route.trainer_chat_id;
    await env.DB.batch([
      env.DB.prepare("DELETE FROM telegram_routes WHERE link_id=?").bind(route.link_id),
      env.DB.prepare("DELETE FROM relay_registrations WHERE link_id=? AND chat_id=?").bind(route.link_id, chatId),
    ]);
    await sendMessage(env, chatId, `Маршрут ${route.link_id.slice(0, 8)} отключён. Криптографическая связь RepTrack не изменена.`);
    await sendMessage(env, peer, "Второй участник отключил этот Telegram-маршрут. Криптографическая связь RepTrack не была изменена.");
    return;
  }
  const routes = await env.DB.prepare("SELECT trainer_chat_id,trainee_chat_id FROM telegram_routes WHERE trainer_chat_id=? OR trainee_chat_id=?").bind(chatId, chatId).all();
  const peers = [...new Set((routes.results ?? []).flatMap(row => [row.trainer_chat_id, row.trainee_chat_id]).filter(id => id !== chatId))];
  await env.DB.batch([
    env.DB.prepare("DELETE FROM telegram_routes WHERE trainer_chat_id=? OR trainee_chat_id=?").bind(chatId, chatId),
    env.DB.prepare("DELETE FROM relay_registrations WHERE chat_id=?").bind(chatId),
  ]);
  await sendMessage(env, chatId, "Пересылка этого Telegram-чата отключена. Связь в RepTrack и уже доставленные файлы не изменены.");
  await Promise.all(peers.map(peer => sendMessage(env, peer, "Второй участник отключил маршрут Telegram. Криптографическая связь RepTrack не была изменена.")));
}

async function downloadDocument(env, document) {
  if (document.file_size !== undefined && (!Number.isSafeInteger(document.file_size) || document.file_size < 1 || document.file_size > maxTelegramFileBytes)) throw new Error("Telegram file size is invalid");
  const info = await telegramCall(env, "getFile", { file_id: document.file_id });
  const path = info.file_path;
  if (typeof path !== "string" || path.split("/").some(part => part === "..") || !/^[A-Za-z0-9_./-]+$/.test(path)) throw new Error("Telegram file path is invalid");
  const response = await fetch(`https://api.telegram.org/file/bot${env.BOT_TOKEN}/${path}`);
  if (!response.ok) throw new Error("Telegram file download failed");
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (bytes.byteLength < 1 || bytes.byteLength > maxTelegramFileBytes) throw new Error("Telegram file size is invalid");
  return bytes;
}

async function sendDocument(env, chatId, bytes, name, mime, caption) {
  const form = new FormData();
  form.set("chat_id", String(chatId));
  form.set("caption", cleanText(caption, 1000));
  form.set("document", new File([bytes], name, { type: mime }));
  await telegramCall(env, "sendDocument", form);
}

async function sendMessage(env, chatId, text) {
  return telegramCall(env, "sendMessage", { chat_id: String(chatId), text: cleanText(text, 4000) });
}

async function telegramCall(env, method, body) {
  const form = body instanceof FormData ? body : JSON.stringify(body);
  const response = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: body instanceof FormData ? undefined : { "Content-Type": "application/json" },
    body: form,
  });
  const result = await response.json();
  if (!response.ok || result.ok !== true) throw new Error(`Telegram ${method} failed`);
  return result.result;
}

export async function claimUpdate(db, updateId) {
  const now = Date.now();
  await db.prepare("DELETE FROM telegram_updates WHERE claimed_at<?").bind(now - 30 * dayMs).run();
  const inserted = await db.prepare("INSERT OR IGNORE INTO telegram_updates(update_id,status,claimed_at) VALUES(?,'PROCESSING',?)").bind(updateId, now).run();
  if (inserted.meta?.changes === 1) return { status: "CLAIMED", claimedAt: now };
  const current = await db.prepare("SELECT status,claimed_at FROM telegram_updates WHERE update_id=?").bind(updateId).first();
  if (current?.status === "DONE") return { status: "DONE" };
  if (current?.status === "PROCESSING" && now - current.claimed_at < updateLeaseMs) return { status: "BUSY" };
  const reclaimed = await db.prepare("UPDATE telegram_updates SET status='PROCESSING',claimed_at=? WHERE update_id=? AND status!='DONE' AND (status='FAILED' OR claimed_at<=?)").bind(now, updateId, now - updateLeaseMs).run();
  return reclaimed.meta?.changes === 1 ? { status: "CLAIMED", claimedAt: now } : { status: "BUSY" };
}

export async function finishUpdate(db, updateId, status, claimedAt) {
  await db.prepare("UPDATE telegram_updates SET status=?,claimed_at=? WHERE update_id=? AND status='PROCESSING' AND claimed_at=?").bind(status, Date.now(), updateId, claimedAt).run();
}

async function currentRegistration(db, linkId, role) {
  return db.prepare("SELECT * FROM relay_registrations WHERE link_id=? AND role=?").bind(linkId, role).first();
}

export function matchesPair(a, b) {
  const trainer = a.role === "TRAINER" ? a : b;
  const trainee = a.role === "TRAINEE" ? a : b;
  return trainer.role === "TRAINER" && trainee.role === "TRAINEE"
    && trainer.local_device_id === trainee.remote_device_id
    && trainer.remote_device_id === trainee.local_device_id
    && trainer.fingerprint === trainee.fingerprint;
}

function decodeBase64(value, maxBytes) {
  if (typeof value !== "string" || value.length > Math.ceil(maxBytes * 4 / 3) + 4 || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) throw new Error("Invalid base64 data");
  const binary = atob(value);
  if (binary.length < 1 || binary.length > maxBytes) throw new Error("Decoded data size is invalid");
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

function cleanText(value, maxLength) {
  return String(value).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, " ").slice(0, maxLength).trim();
}

function isId(value) {
  return typeof value === "string" && /^[0-9a-f-]{36}$/i.test(value);
}
