-- Cloudflare D1 Migration: 0002_seed_data.sql
-- Seed Categories, Bots, Versions, and Credential Specifications

-- 1. Insert Categories
INSERT OR IGNORE INTO categories (id, name, slug) VALUES
('cat_utilities', 'Utilities', 'utilities'),
('cat_productivity', 'Productivity', 'productivity'),
('cat_media', 'Media & News', 'media'),
('cat_automation', 'Automation', 'automation');

-- 2. Insert Bots
INSERT OR IGNORE INTO bots (id, slug, name, description, long_description, icon_url, category, runtime, current_version, status, created_at, updated_at) VALUES
(
    'bot_ping_pong',
    'ping-pong-bot',
    'Ping Pong Runner',
    'Ultra-lightweight Telegram bot testing on-device latency and foreground execution.',
    'A proof-of-concept bot that runs locally on your Android device. It connects via HTTPS long-polling to the Telegram Bot API and immediately responds with "pong" when sent a "ping" message, displaying network roundtrip latency.',
    'https://images.unsplash.com/photo-1534158914592-062992fbe900?w=200',
    'utilities',
    'native_art',
    '1.0.0',
    'published',
    '2026-09-19T00:00:00Z',
    '2026-09-19T00:00:00Z'
),
(
    'bot_echo',
    'echo-assistant-bot',
    'Echo Assistant Bot',
    'Echoes incoming messages with latency metadata and system diagnostics.',
    'Echo Assistant Bot is designed to test your background service execution. It echoes any incoming message back to the sender and appends the Android device battery level and message processing time.',
    'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=200',
    'utilities',
    'native_art',
    '1.1.0',
    'published',
    '2026-09-19T01:00:00Z',
    '2026-09-19T01:00:00Z'
),
(
    'bot_rss',
    'rss-channel-broadcaster',
    'RSS Channel Broadcaster',
    'Monitors RSS/Atom feeds and broadcasts new posts to a Telegram channel.',
    'Automates content publishing to your Telegram public channel or group. Runs locally without cloud servers, checking your configured feeds at safe intervals.',
    'https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=200',
    'media',
    'native_art',
    '1.2.0',
    'published',
    '2026-09-19T02:00:00Z',
    '2026-09-19T02:00:00Z'
),
(
    'bot_gemini',
    'gemini-smart-assistant',
    'Gemini Smart Assistant',
    'AI assistant running through your personal Telegram bot with Google Gemini.',
    'Provides conversational intelligence, code explanation, and document analysis directly in Telegram chats. All API keys remain on your device.',
    'https://images.unsplash.com/photo-1677442136019-21780ecad995?w=200',
    'productivity',
    'native_art',
    '2.0.0',
    'published',
    '2026-09-19T03:00:00Z',
    '2026-09-19T03:00:00Z'
),
(
    'bot_draft_sample',
    'draft-experimental-bot',
    'Experimental Beta Bot',
    'Work-in-progress bot visible only to administrators for testing publication lifecycle.',
    'This bot is currently in draft status and will not appear in the public catalog until published via the Admin API.',
    'https://images.unsplash.com/photo-1518770660439-4636190af475?w=200',
    'automation',
    'native_art',
    '0.1.0',
    'draft',
    '2026-09-19T04:00:00Z',
    '2026-09-19T04:00:00Z'
);

-- 3. Insert Bot Versions
INSERT OR IGNORE INTO bot_versions (id, bot_id, version, package_key, package_size, sha256, release_notes, minimum_app_version, published_at) VALUES
(
    'ver_pp_100',
    'bot_ping_pong',
    '1.0.0',
    'packages/bot_ping_pong_1.0.0.botpkg',
    15420,
    '7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069',
    'Initial release with native ART long-polling and ping-pong command router.',
    1,
    '2026-09-19T00:00:00Z'
),
(
    'ver_echo_110',
    'bot_echo',
    '1.1.0',
    'packages/bot_echo_1.1.0.botpkg',
    24800,
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    'Added structured diagnostic headers and echo command support.',
    1,
    '2026-09-19T01:00:00Z'
),
(
    'ver_rss_120',
    'bot_rss',
    '1.2.0',
    'packages/bot_rss_1.2.0.botpkg',
    38912,
    'ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb',
    'Added support for Atom 1.0 feeds and instant preview links.',
    1,
    '2026-09-19T02:00:00Z'
),
(
    'ver_gemini_200',
    'bot_gemini',
    '2.0.0',
    'packages/bot_gemini_2.0.0.botpkg',
    45210,
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    'Upgraded to Gemini 1.5 Flash models with streaming output.',
    1,
    '2026-09-19T03:00:00Z'
),
(
    'ver_draft_010',
    'bot_draft_sample',
    '0.1.0',
    'packages/bot_draft_0.1.0.botpkg',
    10240,
    'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e',
    'Initial draft build for testing admin publish flow.',
    1,
    '2026-09-19T04:00:00Z'
);

-- 4. Insert Bot Credentials
INSERT OR IGNORE INTO bot_credentials (id, bot_id, key, display_name, description, required, secret, input_type) VALUES
-- Ping Pong Bot
(
    'cred_pp_token',
    'bot_ping_pong',
    'bot_token',
    'Telegram Bot Token',
    'Token obtained from @BotFather in Telegram.',
    1,
    1,
    'password'
),
-- Echo Assistant Bot
(
    'cred_echo_token',
    'bot_echo',
    'bot_token',
    'Telegram Bot Token',
    'Token obtained from @BotFather in Telegram.',
    1,
    1,
    'password'
),
-- RSS Channel Broadcaster
(
    'cred_rss_token',
    'bot_rss',
    'bot_token',
    'Telegram Bot Token',
    'Token obtained from @BotFather in Telegram.',
    1,
    1,
    'password'
),
(
    'cred_rss_chat_id',
    'bot_rss',
    'target_chat_id',
    'Target Channel / Chat ID',
    'Channel or Group ID (e.g. -1001234567890) where messages will be posted.',
    1,
    0,
    'text'
),
(
    'cred_rss_feed_url',
    'bot_rss',
    'feed_url',
    'RSS / Atom Feed URL',
    'HTTP/HTTPS URL of the RSS feed to monitor.',
    1,
    0,
    'url'
),
-- Gemini Smart Assistant
(
    'cred_gemini_token',
    'bot_gemini',
    'bot_token',
    'Telegram Bot Token',
    'Token obtained from @BotFather in Telegram.',
    1,
    1,
    'password'
),
(
    'cred_gemini_key',
    'bot_gemini',
    'gemini_api_key',
    'Google Gemini API Key',
    'Obtain a free Google AI Studio API key at https://aistudio.google.com',
    1,
    1,
    'password'
);
