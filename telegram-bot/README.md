# RepTrack Telegram Relay

Небольшой webhook-бот для Stage 3. Он связывает личные чаты тренера и подопечного, пересылает читаемый PDF-отчёт и зашифрованный `.rptresult`, а также передаёт зашифрованный `.rptprogram` от тренера подопечному. Собственного VPS и постоянно запущенного процесса нет: используются Cloudflare Workers и D1.

## Границы и данные

- Дневник и Stage 2 обмен работают локально и не зависят от Worker.
- Worker видит PDF и открытые поля `.rptdelivery`, а Telegram получает PDF и оба файла. `.rptresult` и `.rptprogram` зашифрованы для RepTrack; Worker проверяет только их адресный заголовок и не расшифровывает их.
- Worker не записывает файлы отчётов в D1. В базе остаются ID связи и устройств, роли, отпечаток доверенной связи, Telegram chat ID, время активности и короткое состояние доставки. Состояния входящих обновлений и пересылок очищаются примерно через 30 дней; активный маршрут удаляется адресной командой `/disconnect ID` или все маршруты чата — `/disconnect`.
- Подключение маршрута не проверяет Telegram-личность пользователя через аккаунт RepTrack. Обе стороны должны сами открыть личный чат с ботом и отправить файл `.rptrelay` из соответствующей доверенной связи. Не пересылайте этот файл посторонним.
- Отправка PDF означает передачу содержимого в Telegram. Получение файла тренером не означает, что он открыл его или импортировал `.rptresult`; импорт и принятие программы подтверждаются отдельно в RepTrack.

## Требования

- Node.js 20+ с `npm`/`npx` для Wrangler; синтаксические и модульные тесты работают через встроенный `node:test` и не требуют npm-пакетов.
- Бесплатный аккаунт Cloudflare с Workers и D1.
- Telegram-бот, созданный через `@BotFather`. Токен храните только в Cloudflare Secret и локально во время настройки webhook. Не добавляйте его в Git, не отправляйте в RepTrack чат и не передавайте в этот репозиторий.

Текущие квоты Workers Free: 100 000 запросов в день и 10 мс CPU на вызов; D1 Free: 5 млн прочитанных строк в день, 100 000 записанных строк в день и 5 ГБ базы. При превышении дневных лимитов D1 запросы могут завершаться ошибкой до следующего сброса. Бесплатный Worker также имеет ограничение памяти и времени CPU. Пакет для бота ограничен 16 МБ, а Telegram Bot API ограничивает скачивание файла ботом 20 МБ. Большие отчёты могут не пройти лимит CPU Worker, поэтому эти квоты не являются SLA. Бесплатные лимиты и условия провайдера могут меняться. См. [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/), [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/) и [Telegram Bot API](https://core.telegram.org/bots/api).

## Локальная проверка

Из этой папки:

```powershell
node --check src/index.mjs
node --test test/index.test.mjs
```

## Первое развёртывание

В PowerShell из `telegram-bot/`:

1. Создайте D1 базу:

   ```powershell
   npx wrangler d1 create reptrack-telegram-relay
   ```

   Скопируйте выданный `database_id` в `wrangler.toml`, заменив `REPLACE_WITH_D1_DATABASE_ID`.

2. Примените схему и сохраните Telegram токен как секрет Worker:

   ```powershell
   npx wrangler d1 execute reptrack-telegram-relay --remote --file=migrations/0001_init.sql
   npx wrangler secret put BOT_TOKEN
   npx wrangler secret put WEBHOOK_SECRET
   ```

   Wrangler запросит значения интерактивно. `WEBHOOK_SECRET` должен содержать только латинские буквы, цифры, `_` или `-`; используйте случайную строку не короче 32 символов.

3. Разверните Worker:

   ```powershell
   npx wrangler deploy
   ```

4. Укажите webhook Telegram. В PowerShell задайте значения без отображения секретов:

   ```powershell
   $secureToken = Read-Host 'Bot token' -AsSecureString
   $env:BOT_TOKEN = [System.Net.NetworkCredential]::new('', $secureToken).Password
   $env:WORKER_URL = Read-Host 'Worker URL'
   $secureWebhookSecret = Read-Host 'Webhook secret' -AsSecureString
   $env:WEBHOOK_SECRET = [System.Net.NetworkCredential]::new('', $secureWebhookSecret).Password
   node scripts/set-webhook.mjs
   Remove-Item Env:BOT_TOKEN, Env:WORKER_URL, Env:WEBHOOK_SECRET
   ```

   В Worker должен быть установлен тот же `WEBHOOK_SECRET`, что используется здесь. Скрипт не удаляет ожидающие Telegram обновления. Для смены токена сначала выполните `npx wrangler secret put BOT_TOKEN`, затем повторите установку webhook с новым токеном.

В Cloudflare Dashboard проверьте успешный статус Worker и D1. `/start`, `/help` и `/status` доступны в личном чате бота. После обновления кода повторите `npx wrangler deploy`; при изменении схемы сначала добавьте миграцию в `migrations/` и примените её к D1.

## Подключение устройств и обмен

1. Тренер и подопечный сначала связывают устройства в RepTrack и сверяют криптографический отпечаток по независимому каналу.
2. Оба открывают личный чат с ботом и нажимают Start.
3. В RepTrack каждый выбирает ту же доверенную связь в **Настройки → Защищённый обмен**, нажимает **Подключить эту связь к Telegram-боту** и отправляет полученный `.rptrelay` боту.
4. После подтверждения маршрута подопечный завершает тренировку и выбирает **Завершить и отправить тренеру**. В Sharesheet он подтверждает передачу `.rptdelivery` боту.
5. Бот отправляет тренеру PDF с кратким уведомлением и `.rptresult` для импорта. Чтобы передать программу, тренер отправляет созданный RepTrack `.rptprogram` боту.
6. `/status` показывает первые 8 символов ID активного маршрута или ожидающей регистрации. `/disconnect ID` удаляет только этот маршрут/регистрацию, а `/disconnect` без ID удаляет все маршруты текущего чата. Команда не отзывает криптографическую связь в приложении и не удаляет уже доставленные Telegram-файлы.

## Известные ограничения

- Worker исполняет каждый Telegram update синхронно и имеет ограничение бесплатного CPU. Незавершённый update получает HTTP 503, чтобы Telegram повторил доставку; после истечения минутного lease его можно перехватить. Завершённые update подтверждаются HTTP 200. Проверьте фактическую доставку типичного отчёта после развёртывания; модульные тесты не проверяют живой Bot API, D1 или Telegram retry.
- Telegram Bot API не предоставляет ключ идемпотентности для `sendDocument`: при сбое между отправкой файла и записью статуса возможен повтор файла после retry. Обычные повторные update и повторная отправка уже отмеченного пакета подавляются.
- Bot API может не прислать `file_size`; Worker в таком случае проверяет фактический размер после скачивания.
- Для Android нужны отдельные сборка и инструментальные тесты; команды указаны в `../android/README.md`.
