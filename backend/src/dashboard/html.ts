/**
 * Web-Based Admin Dashboard SPA for TapBot Store Management
 * Embedded Single Page Application served directly by Cloudflare Worker
 */

export function renderDashboardHtml(): string {
    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TapBot Store Admin</title>
    <style>
        :root {
            --bg-base: #090d16;
            --bg-surface: #0f172a;
            --bg-surface-hover: #1e293b;
            --bg-card: rgba(15, 23, 42, 0.75);
            --border-subtle: #1e293b;
            --border-prominent: #334155;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --text-dim: #64748b;
            --accent-cyan: #38bdf8;
            --accent-indigo: #6366f1;
            --accent-purple: #a855f7;
            --status-published: #10b981;
            --status-published-bg: rgba(16, 185, 129, 0.12);
            --status-published-border: rgba(16, 185, 129, 0.35);
            --status-draft: #94a3b8;
            --status-draft-bg: rgba(148, 163, 184, 0.12);
            --status-draft-border: rgba(148, 163, 184, 0.3);
            --status-review: #f59e0b;
            --status-review-bg: rgba(245, 158, 11, 0.12);
            --status-review-border: rgba(245, 158, 11, 0.35);
            --status-unpublished: #c084fc;
            --status-unpublished-bg: rgba(192, 132, 252, 0.12);
            --status-unpublished-border: rgba(192, 132, 252, 0.35);
            --status-archived: #f43f5e;
            --status-archived-bg: rgba(244, 63, 94, 0.12);
            --status-archived-border: rgba(244, 63, 94, 0.35);
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Inter', 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-base);
            color: var(--text-main);
            min-height: 100vh;
            line-height: 1.5;
            background-image: 
                radial-gradient(circle at 15% 10%, rgba(99, 102, 241, 0.08) 0%, transparent 40%),
                radial-gradient(circle at 85% 80%, rgba(56, 189, 248, 0.06) 0%, transparent 40%);
            background-attachment: fixed;
        }

        .mono {
            font-family: ui-monospace, 'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace;
        }

        /* Top Navbar */
        header {
            position: sticky;
            top: 0;
            z-index: 40;
            background: rgba(9, 13, 22, 0.85);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--border-subtle);
            padding: 0.75rem 1.5rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .nav-brand {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .brand-badge {
            background: linear-gradient(135deg, #6366f1, #38bdf8);
            width: 32px;
            height: 32px;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            color: #fff;
            box-shadow: 0 0 16px rgba(99, 102, 241, 0.4);
        }

        .brand-title {
            font-weight: 700;
            font-size: 1.15rem;
            letter-spacing: -0.02em;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .brand-tag {
            font-size: 0.7rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            padding: 0.15rem 0.45rem;
            border-radius: 4px;
            background: var(--bg-surface-hover);
            color: var(--accent-cyan);
            border: 1px solid var(--border-subtle);
        }

        .nav-actions {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        /* Buttons */
        .btn {
            display: inline-flex;
            align-items: center;
            gap: 0.45rem;
            font-size: 0.85rem;
            font-weight: 600;
            padding: 0.5rem 0.95rem;
            border-radius: 6px;
            cursor: pointer;
            border: 1px solid transparent;
            transition: all 0.15s ease;
            text-decoration: none;
            user-select: none;
        }

        .btn-primary {
            background: linear-gradient(135deg, #4f46e5, #6366f1);
            color: #ffffff;
            box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
        }
        .btn-primary:hover {
            filter: brightness(1.1);
            transform: translateY(-1px);
        }

        .btn-secondary {
            background: var(--bg-surface);
            color: var(--text-main);
            border-color: var(--border-subtle);
        }
        .btn-secondary:hover {
            background: var(--bg-surface-hover);
            border-color: var(--border-prominent);
        }

        .btn-success {
            background: #059669;
            color: #ffffff;
        }
        .btn-success:hover {
            background: #10b981;
        }

        .btn-danger {
            background: rgba(244, 63, 94, 0.15);
            color: #fda4af;
            border-color: rgba(244, 63, 94, 0.3);
        }
        .btn-danger:hover {
            background: rgba(244, 63, 94, 0.3);
            color: #fff;
        }

        .btn-sm {
            padding: 0.3rem 0.6rem;
            font-size: 0.78rem;
        }

        /* Layout Container */
        .container {
            max-width: 1240px;
            margin: 0 auto;
            padding: 1.75rem 1.25rem 4rem 1.25rem;
        }

        /* Metrics Grid */
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 1rem;
            margin-bottom: 2rem;
        }

        .metric-card {
            background: var(--bg-card);
            backdrop-filter: blur(12px);
            border: 1px solid var(--border-subtle);
            border-radius: 10px;
            padding: 1.15rem;
            position: relative;
            overflow: hidden;
            transition: border-color 0.2s;
        }
        .metric-card:hover {
            border-color: var(--border-prominent);
        }

        .metric-title {
            font-size: 0.78rem;
            font-weight: 600;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.04em;
            margin-bottom: 0.35rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .metric-value {
            font-size: 1.85rem;
            font-weight: 700;
            letter-spacing: -0.02em;
            color: var(--text-main);
        }

        .metric-subtitle {
            font-size: 0.72rem;
            color: var(--text-dim);
            margin-top: 0.25rem;
        }

        .pulse-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            display: inline-block;
            background: var(--status-published);
            box-shadow: 0 0 8px var(--status-published);
        }

        /* Bot Management Section */
        .section-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 1.25rem;
            flex-wrap: wrap;
            gap: 1rem;
        }

        .section-title {
            font-size: 1.35rem;
            font-weight: 700;
            letter-spacing: -0.02em;
        }

        .filters-toolbar {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            flex-wrap: wrap;
        }

        .filter-tabs {
            display: flex;
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: 8px;
            padding: 3px;
        }

        .filter-tab {
            padding: 0.35rem 0.75rem;
            font-size: 0.8rem;
            font-weight: 500;
            color: var(--text-muted);
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .filter-tab.active {
            background: var(--bg-surface-hover);
            color: var(--text-main);
            font-weight: 600;
        }

        .search-box {
            position: relative;
        }

        .search-input {
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            padding: 0.45rem 0.85rem 0.45rem 2rem;
            border-radius: 6px;
            font-size: 0.85rem;
            outline: none;
            transition: border-color 0.2s;
            width: 220px;
        }
        .search-input:focus {
            border-color: var(--accent-cyan);
        }

        .search-icon {
            position: absolute;
            left: 0.65rem;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-dim);
            font-size: 0.85rem;
        }

        /* Table Card */
        .table-card {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            border-radius: 10px;
            overflow: hidden;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
        }

        .table-responsive {
            overflow-x: auto;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 0.86rem;
        }

        th {
            background: rgba(15, 23, 42, 0.95);
            padding: 0.85rem 1rem;
            font-weight: 600;
            font-size: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
            border-bottom: 1px solid var(--border-subtle);
        }

        td {
            padding: 0.95rem 1rem;
            border-bottom: 1px solid var(--border-subtle);
            vertical-align: middle;
        }

        tr:last-child td {
            border-bottom: none;
        }

        tr:hover td {
            background: rgba(30, 41, 59, 0.4);
        }

        .bot-cell {
            display: flex;
            align-items: center;
            gap: 0.85rem;
        }

        .bot-icon {
            width: 38px;
            height: 38px;
            border-radius: 8px;
            background: var(--bg-surface-hover);
            border: 1px solid var(--border-subtle);
            object-fit: cover;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            color: var(--accent-cyan);
            font-size: 1rem;
            flex-shrink: 0;
        }

        .bot-name {
            font-weight: 600;
            color: var(--text-main);
            margin-bottom: 0.15rem;
        }

        .bot-slug {
            font-size: 0.74rem;
            color: var(--text-dim);
        }

        /* Badges */
        .badge {
            display: inline-flex;
            align-items: center;
            gap: 0.35rem;
            padding: 0.25rem 0.55rem;
            border-radius: 9999px;
            font-size: 0.72rem;
            font-weight: 600;
            letter-spacing: 0.03em;
            text-transform: uppercase;
        }

        .badge-published {
            background: var(--status-published-bg);
            color: var(--status-published);
            border: 1px solid var(--status-published-border);
        }

        .badge-draft {
            background: var(--status-draft-bg);
            color: var(--status-draft);
            border: 1px solid var(--status-draft-border);
        }

        .badge-review {
            background: var(--status-review-bg);
            color: var(--status-review);
            border: 1px solid var(--status-review-border);
        }

        .badge-unpublished {
            background: var(--status-unpublished-bg);
            color: var(--status-unpublished);
            border: 1px solid var(--status-unpublished-border);
        }

        .badge-archived {
            background: var(--status-archived-bg);
            color: var(--status-archived);
            border: 1px solid var(--status-archived-border);
        }

        .badge-tag {
            background: rgba(30, 41, 59, 0.6);
            border: 1px solid var(--border-subtle);
            color: var(--text-muted);
            padding: 0.15rem 0.45rem;
            border-radius: 4px;
            font-size: 0.72rem;
        }

        /* Action Buttons */
        .actions-cell {
            display: flex;
            align-items: center;
            gap: 0.4rem;
            flex-wrap: wrap;
        }

        /* Modals */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(3, 7, 18, 0.8);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            z-index: 100;
            display: none;
            align-items: center;
            justify-content: center;
            padding: 1rem;
        }

        .modal-overlay.active {
            display: flex;
        }

        .modal-box {
            background: var(--bg-surface);
            border: 1px solid var(--border-prominent);
            border-radius: 12px;
            width: 100%;
            max-width: 580px;
            max-height: 90vh;
            overflow-y: auto;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
            display: flex;
            flex-direction: column;
        }

        .modal-box-lg {
            max-width: 820px;
        }

        .modal-header {
            padding: 1.15rem 1.35rem;
            border-bottom: 1px solid var(--border-subtle);
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .modal-title {
            font-size: 1.1rem;
            font-weight: 700;
            color: var(--text-main);
        }

        .modal-close {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 1.25rem;
            cursor: pointer;
            padding: 0.25rem;
            border-radius: 4px;
        }
        .modal-close:hover {
            color: var(--text-main);
        }

        .modal-body {
            padding: 1.35rem;
            flex: 1;
            overflow-y: auto;
        }

        .modal-footer {
            padding: 1rem 1.35rem;
            border-top: 1px solid var(--border-subtle);
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 0.75rem;
            background: rgba(9, 13, 22, 0.5);
        }

        /* Form Controls */
        .form-group {
            margin-bottom: 1rem;
        }

        .form-label {
            display: block;
            font-size: 0.8rem;
            font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 0.35rem;
        }

        .form-control {
            width: 100%;
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            padding: 0.55rem 0.85rem;
            border-radius: 6px;
            font-size: 0.86rem;
            outline: none;
            transition: border-color 0.2s;
        }
        .form-control:focus {
            border-color: var(--accent-cyan);
        }

        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1rem;
        }

        textarea.form-control {
            min-height: 80px;
            resize: vertical;
        }

        /* File Upload Dropzone */
        .dropzone {
            border: 2px dashed var(--border-prominent);
            border-radius: 8px;
            padding: 1.5rem;
            text-align: center;
            background: rgba(9, 13, 22, 0.4);
            cursor: pointer;
            transition: all 0.2s;
        }
        .dropzone:hover, .dropzone.dragover {
            border-color: var(--accent-cyan);
            background: rgba(56, 189, 248, 0.05);
        }

        .dropzone-icon {
            font-size: 2rem;
            margin-bottom: 0.5rem;
            color: var(--accent-cyan);
        }

        .dropzone-text {
            font-size: 0.84rem;
            color: var(--text-muted);
        }

        .dropzone-subtext {
            font-size: 0.74rem;
            color: var(--text-dim);
            margin-top: 0.25rem;
        }

        /* Drawer (Audit Log) */
        .drawer {
            position: fixed;
            top: 0;
            right: -480px;
            width: 480px;
            max-width: 90vw;
            height: 100vh;
            background: var(--bg-surface);
            border-left: 1px solid var(--border-prominent);
            box-shadow: -10px 0 30px rgba(0, 0, 0, 0.5);
            z-index: 90;
            transition: right 0.25s cubic-bezier(0.16, 1, 0.3, 1);
            display: flex;
            flex-direction: column;
        }

        .drawer.active {
            right: 0;
        }

        .drawer-header {
            padding: 1.15rem 1.25rem;
            border-bottom: 1px solid var(--border-subtle);
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .drawer-body {
            padding: 1rem;
            flex: 1;
            overflow-y: auto;
        }

        .audit-item {
            padding: 0.85rem;
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            border-radius: 8px;
            margin-bottom: 0.75rem;
            font-size: 0.8rem;
        }

        .audit-meta {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 0.35rem;
        }

        .audit-action {
            font-weight: 700;
            color: var(--accent-cyan);
        }

        .audit-time {
            font-size: 0.7rem;
            color: var(--text-dim);
        }

        .audit-details {
            background: rgba(15, 23, 42, 0.6);
            padding: 0.45rem;
            border-radius: 4px;
            font-size: 0.74rem;
            color: var(--text-muted);
            overflow-x: auto;
            margin-top: 0.35rem;
        }

        /* Toasts */
        .toast-container {
            position: fixed;
            bottom: 1.5rem;
            right: 1.5rem;
            z-index: 120;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }

        .toast {
            background: var(--bg-surface);
            border: 1px solid var(--border-prominent);
            border-radius: 8px;
            padding: 0.75rem 1.1rem;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
            color: var(--text-main);
            font-size: 0.85rem;
            display: flex;
            align-items: center;
            gap: 0.6rem;
            animation: toastIn 0.2s ease-out;
            max-width: 360px;
        }
        .toast-success { border-color: var(--status-published); }
        .toast-error { border-color: var(--status-archived); }

        @keyframes toastIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }

        /* Empty State */
        .empty-state {
            padding: 3.5rem 1rem;
            text-align: center;
            color: var(--text-dim);
        }

        .empty-icon {
            font-size: 2.5rem;
            margin-bottom: 0.75rem;
            opacity: 0.6;
        }

        /* Dropdown Lifecycle Select */
        .lifecycle-select {
            background: var(--bg-surface);
            color: var(--text-main);
            border: 1px solid var(--border-subtle);
            border-radius: 6px;
            padding: 0.3rem 0.5rem;
            font-size: 0.75rem;
            font-weight: 600;
            cursor: pointer;
            outline: none;
        }
        .lifecycle-select:focus {
            border-color: var(--accent-cyan);
        }

        /* Responsive */
        @media (max-width: 768px) {
            .form-row { grid-template-columns: 1fr; }
            .section-header { flex-direction: column; align-items: flex-start; }
            .filters-toolbar { width: 100%; justify-content: space-between; }
            .search-input { width: 100%; }
        }
    </style>
</head>
<body>

    <!-- Header Navigation -->
    <header>
        <div class="nav-brand">
            <div class="brand-badge">⚡</div>
            <div>
                <div class="brand-title">
                    TapBot Store Admin
                    <span class="brand-tag">D1 & R2 Backend</span>
                </div>
            </div>
        </div>
        <div class="nav-actions">
            <button class="btn btn-secondary btn-sm" onclick="loadDashboardData()" title="Refresh store data">
                🔄 Refresh
            </button>
            <button class="btn btn-secondary btn-sm" onclick="toggleAuditDrawer()" title="View admin audit logs">
                📜 Audit Trail
            </button>
            <button class="btn btn-danger btn-sm" onclick="handleLogout()" title="Sign out of admin session">
                🚪 Logout
            </button>
        </div>
    </header>

    <!-- Main Container -->
    <main class="container">

        <!-- Top Metrics Bento Grid -->
        <section class="metrics-grid">
            <div class="metric-card">
                <div class="metric-title">Total Bots</div>
                <div class="metric-value mono" id="stat-total-bots">-</div>
                <div class="metric-subtitle">Across all lifecycle states</div>
            </div>
            <div class="metric-card">
                <div class="metric-title">
                    Published Bots
                    <span class="pulse-dot"></span>
                </div>
                <div class="metric-value mono" style="color: var(--status-published);" id="stat-published-bots">-</div>
                <div class="metric-subtitle">Visible to Android clients</div>
            </div>
            <div class="metric-card">
                <div class="metric-title">Drafts & In-Review</div>
                <div class="metric-value mono" style="color: var(--status-review);" id="stat-draft-bots">-</div>
                <div class="metric-subtitle">Pending publisher release</div>
            </div>
            <div class="metric-card">
                <div class="metric-title">Total Versions</div>
                <div class="metric-value mono" style="color: var(--accent-cyan);" id="stat-total-versions">-</div>
                <div class="metric-subtitle">Packaged bot releases</div>
            </div>
            <div class="metric-card">
                <div class="metric-title">Installs / Downloads</div>
                <div class="metric-value" style="font-size: 1.15rem; color: var(--text-dim);" id="stat-downloads">Not available</div>
                <div class="metric-subtitle">Analytics tracking not implemented</div>
            </div>
        </section>

        <!-- Bot Management Section -->
        <section>
            <div class="section-header">
                <div>
                    <h2 class="section-title">Bot Catalog Management</h2>
                    <p style="font-size: 0.82rem; color: var(--text-dim);">Create, package, configure, and publish bots directly to the Bot Store.</p>
                </div>
                <div class="filters-toolbar">
                    <div class="filter-tabs">
                        <div class="filter-tab active" data-filter="all" onclick="setFilter('all', this)">All</div>
                        <div class="filter-tab" data-filter="published" onclick="setFilter('published', this)">Published</div>
                        <div class="filter-tab" data-filter="draft" onclick="setFilter('draft', this)">Draft</div>
                        <div class="filter-tab" data-filter="review" onclick="setFilter('review', this)">Review</div>
                        <div class="filter-tab" data-filter="unpublished" onclick="setFilter('unpublished', this)">Unpublished</div>
                        <div class="filter-tab" data-filter="archived" onclick="setFilter('archived', this)">Archived</div>
                    </div>
                    <div class="search-box">
                        <span class="search-icon">🔍</span>
                        <input type="text" id="search-input" class="search-input" placeholder="Search bots..." oninput="handleSearch()">
                    </div>
                    <button class="btn btn-primary" onclick="openCreateBotModal()">
                        ➕ Create Bot
                    </button>
                </div>
            </div>

            <!-- Bot Catalog Table -->
            <div class="table-card">
                <div class="table-responsive">
                    <table id="bots-table">
                        <thead>
                            <tr>
                                <th>Bot</th>
                                <th>Category</th>
                                <th>Runtime</th>
                                <th>Version</th>
                                <th>Lifecycle State</th>
                                <th>Credentials</th>
                                <th style="text-align: right;">Actions</th>
                            </tr>
                        </thead>
                        <tbody id="bots-table-body">
                            <tr>
                                <td colspan="7" class="empty-state">
                                    <div class="empty-icon">⏳</div>
                                    <div>Loading bot store records...</div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </section>
    </main>

    <!-- ================================================================= -->
    <!-- MODALS & DRAWERS                                                  -->
    <!-- ================================================================= -->

    <!-- 1. Admin Authentication Modal (zero secrets in source) -->
    <div id="auth-modal" class="modal-overlay">
        <div class="modal-box" style="max-width: 420px;">
            <div class="modal-header">
                <div class="modal-title">🔐 Publisher Authentication</div>
            </div>
            <div class="modal-body">
                <p style="font-size: 0.84rem; color: var(--text-muted); margin-bottom: 1rem;">
                    Enter your Cloudflare Worker Admin API Key to manage the Bot Store. The key is stored only in this tab's session storage.
                </p>
                <div class="form-group">
                    <label class="form-label" for="auth-key-input">Admin API Key</label>
                    <input type="password" id="auth-key-input" class="form-control mono" placeholder="Enter API secret key" autocomplete="current-password">
                </div>
                <div id="auth-error" style="color: var(--status-archived); font-size: 0.8rem; display: none; margin-top: 0.5rem;"></div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-primary" style="width: 100%;" onclick="handleAuthSubmit()">
                    Authorize & Enter Dashboard
                </button>
            </div>
        </div>
    </div>

    <!-- 2. Create / Edit Bot Modal -->
    <div id="bot-modal" class="modal-overlay">
        <div class="modal-box">
            <div class="modal-header">
                <div class="modal-title" id="bot-modal-title">Create Bot</div>
                <button class="modal-close" onclick="closeModal('bot-modal')">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="bot-form-id">
                <div class="form-row">
                    <div class="form-group">
                        <label class="form-label">Bot Name *</label>
                        <input type="text" id="bot-form-name" class="form-control" placeholder="e.g. Ping Pong Runner" oninput="handleNameSlugAutoFill()">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Slug (Unique ID) *</label>
                        <input type="text" id="bot-form-slug" class="form-control mono" placeholder="e.g. ping-pong-bot">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label class="form-label">Category *</label>
                        <select id="bot-form-category" class="form-control">
                            <option value="utilities">Utilities</option>
                            <option value="productivity">Productivity</option>
                            <option value="media">Media & Music</option>
                            <option value="ai">AI & Chat</option>
                            <option value="fun">Fun & Games</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Runtime *</label>
                        <select id="bot-form-runtime" class="form-control mono">
                            <option value="native_art">native_art (Android ART Coroutines)</option>
                            <option value="native_go">native_go (Compiled ELF)</option>
                            <option value="python">python (Chaquo/Chaquopy)</option>
                            <option value="node">node (NodeJS)</option>
                        </select>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Short Description *</label>
                    <input type="text" id="bot-form-description" class="form-control" placeholder="Brief summary of what this bot does">
                </div>
                <div class="form-group">
                    <label class="form-label">Long Description (Optional)</label>
                    <textarea id="bot-form-long-description" class="form-control" placeholder="Detailed user guide and documentation"></textarea>
                </div>
                <div class="form-group">
                    <label class="form-label">Bot Icon</label>
                    <div style="display: flex; gap: 0.5rem; margin-bottom: 0.5rem;">
                        <input type="text" id="bot-form-icon-url" class="form-control" placeholder="Icon URL (e.g. https://... or upload below)">
                        <button type="button" class="btn btn-secondary btn-sm" onclick="document.getElementById('icon-file-input').click()">Upload File</button>
                        <input type="file" id="icon-file-input" accept="image/*" style="display: none;" onchange="handleIconFileUpload(this)">
                    </div>
                    <div id="icon-preview-wrap" style="display: none; align-items: center; gap: 0.5rem;">
                        <img id="icon-preview-img" src="" alt="Icon preview" style="width: 32px; height: 32px; border-radius: 6px; object-fit: cover;">
                        <span style="font-size: 0.75rem; color: var(--status-published);">✓ Icon uploaded successfully</span>
                    </div>
                </div>
                <div class="form-group" id="bot-form-status-group">
                    <label class="form-label">Lifecycle Status</label>
                    <select id="bot-form-status" class="form-control mono">
                        <option value="draft">DRAFT (Hidden from store)</option>
                        <option value="review">REVIEW (Under staging testing)</option>
                        <option value="published">PUBLISHED (Live on Android App)</option>
                        <option value="unpublished">UNPUBLISHED (Temporarily disabled)</option>
                        <option value="archived">ARCHIVED (Deprecated)</option>
                    </select>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal('bot-modal')">Cancel</button>
                <button class="btn btn-primary" onclick="saveBotForm()">Save Bot</button>
            </div>
        </div>
    </div>

    <!-- 3. Version Management Modal -->
    <div id="version-modal" class="modal-overlay">
        <div class="modal-box modal-box-lg">
            <div class="modal-header">
                <div class="modal-title" id="version-modal-title">Package & Version Management</div>
                <button class="modal-close" onclick="closeModal('version-modal')">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="ver-bot-id">

                <!-- Existing Versions List -->
                <h4 style="font-size: 0.95rem; margin-bottom: 0.65rem; color: var(--text-main);">Existing Releases</h4>
                <div class="table-responsive" style="margin-bottom: 1.5rem; max-height: 200px; border: 1px solid var(--border-subtle); border-radius: 8px;">
                    <table>
                        <thead>
                            <tr>
                                <th>Version</th>
                                <th>Status</th>
                                <th>Size</th>
                                <th>SHA-256 Checksum</th>
                                <th>Released</th>
                                <th style="text-align: right;">Action</th>
                            </tr>
                        </thead>
                        <tbody id="version-list-body">
                            <tr><td colspan="6" class="empty-state">Loading versions...</td></tr>
                        </tbody>
                    </table>
                </div>

                <!-- Create / Upload Version Form -->
                <div style="background: var(--bg-base); border: 1px solid var(--border-prominent); border-radius: 8px; padding: 1.25rem;">
                    <h4 style="font-size: 0.95rem; margin-bottom: 0.85rem; color: var(--accent-cyan); display: flex; align-items: center; gap: 0.5rem;">
                        <span>📦</span> Upload New Release Package
                    </h4>

                    <!-- Drag & Drop Package Zone -->
                    <div class="dropzone" id="pkg-dropzone" onclick="document.getElementById('pkg-file-input').click()">
                        <div class="dropzone-icon">📥</div>
                        <div class="dropzone-text" id="dropzone-label">Click or drag & drop <strong>.botpkg</strong> or <strong>.zip</strong> archive here</div>
                        <div class="dropzone-subtext">Automatic byte size and Web Crypto SHA-256 calculation</div>
                        <input type="file" id="pkg-file-input" accept=".botpkg,.zip" style="display: none;" onchange="handlePackageFileSelect(this)">
                    </div>

                    <div style="margin-top: 1rem;">
                        <div class="form-row">
                            <div class="form-group">
                                <label class="form-label">Version String *</label>
                                <input type="text" id="ver-input-version" class="form-control mono" placeholder="e.g. 1.0.0 or 1.1.0">
                            </div>
                            <div class="form-group">
                                <label class="form-label">Package Size (Bytes)</label>
                                <input type="number" id="ver-input-size" class="form-control mono" placeholder="Auto-calculated" readonly>
                            </div>
                        </div>

                        <div class="form-group">
                            <label class="form-label">Package Key (R2 Storage Key)</label>
                            <input type="text" id="ver-input-key" class="form-control mono" placeholder="Auto-filled upon upload" readonly>
                        </div>

                        <div class="form-group">
                            <label class="form-label">Package SHA-256 Checksum</label>
                            <input type="text" id="ver-input-sha256" class="form-control mono" placeholder="Auto-calculated Web Crypto hash" readonly>
                        </div>

                        <div class="form-row">
                            <div class="form-group">
                                <label class="form-label">Min App Version</label>
                                <input type="number" id="ver-input-min-app" class="form-control mono" value="1">
                            </div>
                            <div class="form-group">
                                <label class="form-label">Min Runtime Version</label>
                                <input type="text" id="ver-input-min-runtime" class="form-control mono" value="1.0.0">
                            </div>
                        </div>

                        <div class="form-group">
                            <label class="form-label">Release Notes</label>
                            <textarea id="ver-input-notes" class="form-control" placeholder="What's new in this version?"></textarea>
                        </div>

                        <div class="form-group">
                            <label class="form-label">Release Status</label>
                            <select id="ver-input-status" class="form-control mono">
                                <option value="published">Published (Make current version)</option>
                                <option value="unpublished">Unpublished (Hold release)</option>
                            </select>
                        </div>

                        <button class="btn btn-primary" style="width: 100%;" id="btn-submit-version" onclick="submitNewVersion()" disabled>
                            Upload & Register Version
                        </button>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal('version-modal')">Close</button>
            </div>
        </div>
    </div>

    <!-- 4. Credential Management Modal -->
    <div id="credential-modal" class="modal-overlay">
        <div class="modal-box modal-box-lg">
            <div class="modal-header">
                <div class="modal-title" id="credential-modal-title">Configure Required Credentials</div>
                <button class="modal-close" onclick="closeModal('credential-modal')">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="cred-bot-id">
                <p style="font-size: 0.84rem; color: var(--text-muted); margin-bottom: 1rem;">
                    Specify tokens, API secrets, or configuration fields required by this bot before launch on the Android client.
                </p>

                <div class="table-responsive" style="margin-bottom: 1.5rem; border: 1px solid var(--border-subtle); border-radius: 8px;">
                    <table>
                        <thead>
                            <tr>
                                <th>Key</th>
                                <th>Display Name</th>
                                <th>Description</th>
                                <th>Required</th>
                                <th>Secret</th>
                                <th>Input Type</th>
                                <th style="text-align: right;">Action</th>
                            </tr>
                        </thead>
                        <tbody id="credential-list-body">
                            <tr><td colspan="7" class="empty-state">No credentials configured.</td></tr>
                        </tbody>
                    </table>
                </div>

                <!-- Add Credential Form -->
                <div style="background: var(--bg-base); border: 1px solid var(--border-prominent); border-radius: 8px; padding: 1rem;">
                    <h5 style="font-size: 0.88rem; color: var(--text-main); margin-bottom: 0.75rem;">➕ Add New Credential Field</h5>
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label">Key Name *</label>
                            <input type="text" id="new-cred-key" class="form-control mono" placeholder="e.g. bot_token">
                        </div>
                        <div class="form-group">
                            <label class="form-label">Display Name *</label>
                            <input type="text" id="new-cred-name" class="form-control" placeholder="e.g. Telegram Bot Token">
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Help Text / Description</label>
                        <input type="text" id="new-cred-desc" class="form-control" placeholder="e.g. Obtained from @BotFather on Telegram">
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label">Input UI Type</label>
                            <select id="new-cred-type" class="form-control">
                                <option value="password">Password / Secret Mask</option>
                                <option value="text">Single Line Text</option>
                                <option value="textarea">Multi-line Text</option>
                            </select>
                        </div>
                        <div class="form-group" style="display: flex; align-items: center; gap: 1.5rem; margin-top: 1.5rem;">
                            <label style="font-size: 0.85rem; display: flex; align-items: center; gap: 0.4rem; cursor: pointer;">
                                <input type="checkbox" id="new-cred-required" checked> Required
                            </label>
                            <label style="font-size: 0.85rem; display: flex; align-items: center; gap: 0.4rem; cursor: pointer;">
                                <input type="checkbox" id="new-cred-secret" checked> Secret Value
                            </label>
                        </div>
                    </div>
                    <button class="btn btn-primary btn-sm" onclick="addCredentialSubmit()">
                        Save Credential Specification
                    </button>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal('credential-modal')">Close</button>
            </div>
        </div>
    </div>

    <!-- 5. Audit Trail Drawer -->
    <div id="audit-drawer" class="drawer">
        <div class="drawer-header">
            <div style="font-weight: 700; font-size: 1.05rem;">📜 Store Audit Trail</div>
            <button class="modal-close" onclick="toggleAuditDrawer()">&times;</button>
        </div>
        <div class="drawer-body" id="audit-logs-list">
            <div class="empty-state">Loading audit trail...</div>
        </div>
    </div>

    <!-- Toast Notification Container -->
    <div id="toast-container" class="toast-container"></div>

    <!-- ================================================================= -->
    <!-- APPLICATION JAVASCRIPT                                            -->
    <!-- ================================================================= -->
    <script>
        const AUTH_STORAGE_KEY = 'tapbot_admin_key';
        let allBots = [];
        let currentFilter = 'all';

        // -----------------------------------------------------------------
        // Authentication & Session Management
        // -----------------------------------------------------------------
        function getAdminKey() {
            return sessionStorage.getItem(AUTH_STORAGE_KEY) || '';
        }

        function setAdminKey(key) {
            sessionStorage.setItem(AUTH_STORAGE_KEY, key);
        }

        function clearAdminKey() {
            sessionStorage.removeItem(AUTH_STORAGE_KEY);
        }

        function checkAuthOrPrompt() {
            const key = getAdminKey();
            if (!key) {
                document.getElementById('auth-modal').classList.add('active');
                return false;
            }
            return true;
        }

        async function handleAuthSubmit() {
            const input = document.getElementById('auth-key-input');
            const key = input.value.trim();
            const errorDiv = document.getElementById('auth-error');

            if (!key) {
                errorDiv.innerText = 'Please enter an admin API key';
                errorDiv.style.display = 'block';
                return;
            }

            try {
                // Test key against stats endpoint
                const res = await fetch('/api/v1/admin/stats', {
                    headers: { 'Authorization': 'Bearer ' + key }
                });

                if (res.status === 401) {
                    errorDiv.innerText = 'Unauthorized: Invalid Admin API Key';
                    errorDiv.style.display = 'block';
                    return;
                }

                if (!res.ok) {
                    throw new Error('Verification failed with status ' + res.status);
                }

                setAdminKey(key);
                document.getElementById('auth-modal').classList.remove('active');
                errorDiv.style.display = 'none';
                showToast('Authentication successful', 'success');
                loadDashboardData();
            } catch (err) {
                errorDiv.innerText = 'Error verifying key: ' + err.message;
                errorDiv.style.display = 'block';
            }
        }

        function handleLogout() {
            clearAdminKey();
            showToast('Logged out of admin session', 'info');
            document.getElementById('auth-modal').classList.add('active');
        }

        // -----------------------------------------------------------------
        // API Fetch Wrapper
        // -----------------------------------------------------------------
        async function apiCall(endpoint, options = {}) {
            const key = getAdminKey();
            if (!key) {
                checkAuthOrPrompt();
                throw new Error('Not authenticated');
            }

            options.headers = options.headers || {};
            if (!(options.body instanceof FormData)) {
                options.headers['Content-Type'] = options.headers['Content-Type'] || 'application/json';
            }
            options.headers['Authorization'] = 'Bearer ' + key;

            const res = await fetch(endpoint, options);

            if (res.status === 401) {
                clearAdminKey();
                checkAuthOrPrompt();
                throw new Error('Session expired or unauthorized');
            }

            const json = await res.json();
            if (!json.success) {
                throw new Error(json.error?.message || 'API request failed');
            }

            return json.data;
        }

        // -----------------------------------------------------------------
        // UI Helpers & Toasts
        // -----------------------------------------------------------------
        function showToast(message, type = 'info') {
            const container = document.getElementById('toast-container');
            const toast = document.createElement('div');
            toast.className = 'toast toast-' + type;
            const icon = type === 'success' ? '✓' : (type === 'error' ? '✕' : 'ℹ');
            toast.innerHTML = '<span>' + icon + '</span><span>' + escapeHtml(message) + '</span>';
            container.appendChild(toast);
            setTimeout(() => {
                toast.style.opacity = '0';
                setTimeout(() => toast.remove(), 200);
            }, 3500);
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;');
        }

        function formatBytes(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }

        function formatDate(dateStr) {
            if (!dateStr) return '-';
            try {
                const d = new Date(dateStr);
                return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            } catch {
                return dateStr;
            }
        }

        function closeModal(modalId) {
            document.getElementById(modalId).classList.remove('active');
        }

        // -----------------------------------------------------------------
        // Dashboard Data Loading
        // -----------------------------------------------------------------
        async function loadDashboardData() {
            if (!checkAuthOrPrompt()) return;

            try {
                // 1. Fetch Stats
                const stats = await apiCall('/api/v1/admin/stats');
                document.getElementById('stat-total-bots').innerText = stats.totalBots;
                document.getElementById('stat-published-bots').innerText = stats.publishedBots;
                document.getElementById('stat-draft-bots').innerText = (stats.draftBots + stats.reviewBots);
                document.getElementById('stat-total-versions').innerText = stats.totalVersions;
                // Strictly display "Not available" without fake analytics
                document.getElementById('stat-downloads').innerText = stats.downloads;

                // 2. Fetch Bots
                allBots = await apiCall('/api/v1/admin/bots');
                renderBotsTable();
            } catch (err) {
                console.error('Failed to load dashboard data:', err);
                showToast(err.message, 'error');
            }
        }

        // -----------------------------------------------------------------
        // Render Bots Table
        // -----------------------------------------------------------------
        function renderBotsTable() {
            const tbody = document.getElementById('bots-table-body');
            const search = (document.getElementById('search-input').value || '').trim().toLowerCase();

            const filtered = allBots.filter(bot => {
                const matchesFilter = (currentFilter === 'all') || (bot.status.toLowerCase() === currentFilter);
                const matchesSearch = !search ||
                    bot.name.toLowerCase().includes(search) ||
                    bot.slug.toLowerCase().includes(search) ||
                    bot.category.toLowerCase().includes(search);
                return matchesFilter && matchesSearch;
            });

            if (filtered.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="empty-state"><div class="empty-icon">📂</div><div>No bots found matching criteria.</div></td></tr>';
                return;
            }

            tbody.innerHTML = filtered.map(bot => {
                const status = (bot.status || 'draft').toLowerCase();
                const iconHtml = bot.iconUrl
                    ? '<img class="bot-icon" src="' + escapeHtml(bot.iconUrl) + '" alt="' + escapeHtml(bot.name) + '">'
                    : '<div class="bot-icon">🤖</div>';

                const verText = bot.currentVersion
                    ? '<span class="mono font-semibold" style="color: var(--accent-cyan);">v' + escapeHtml(bot.currentVersion) + '</span>'
                    : '<span style="color: var(--text-dim);">None</span>';

                const credCount = bot.credentials ? bot.credentials.length : 0;

                return '<tr>' +
                    '<td>' +
                        '<div class="bot-cell">' +
                            iconHtml +
                            '<div>' +
                                '<div class="bot-name">' + escapeHtml(bot.name) + '</div>' +
                                '<div class="bot-slug mono">' + escapeHtml(bot.slug) + '</div>' +
                            '</div>' +
                        '</div>' +
                    '</td>' +
                    '<td><span class="badge-tag">' + escapeHtml(bot.category) + '</span></td>' +
                    '<td><span class="badge-tag mono">' + escapeHtml(bot.runtime || 'native_art') + '</span></td>' +
                    '<td>' + verText + '</td>' +
                    '<td>' +
                        '<select class="lifecycle-select" onchange="changeLifecycle(\\'' + bot.id + '\\', this.value)">' +
                            '<option value="DRAFT"' + (status === 'draft' ? ' selected' : '') + '>DRAFT</option>' +
                            '<option value="REVIEW"' + (status === 'review' ? ' selected' : '') + '>REVIEW</option>' +
                            '<option value="PUBLISHED"' + (status === 'published' ? ' selected' : '') + '>PUBLISHED</option>' +
                            '<option value="UNPUBLISHED"' + (status === 'unpublished' ? ' selected' : '') + '>UNPUBLISHED</option>' +
                            '<option value="ARCHIVED"' + (status === 'archived' ? ' selected' : '') + '>ARCHIVED</option>' +
                        '</select>' +
                    '</td>' +
                    '<td>' +
                        '<button class="btn btn-secondary btn-sm" onclick="openCredentialModal(\\'' + bot.id + '\\')">' +
                            '🔑 ' + credCount + ' Fields' +
                        '</button>' +
                    '</td>' +
                    '<td style="text-align: right;">' +
                        '<div class="actions-cell" style="justify-content: flex-end;">' +
                            '<button class="btn btn-secondary btn-sm" onclick="openVersionModal(\\'' + bot.id + '\\')" title="Manage releases and packages">' +
                                '📦 Releases' +
                            '</button>' +
                            '<button class="btn btn-secondary btn-sm" onclick="openEditBotModal(\\'' + bot.id + '\\')" title="Edit bot metadata">' +
                                '✏️' +
                            '</button>' +
                            '<button class="btn btn-danger btn-sm" onclick="confirmDeleteBot(\\'' + bot.id + '\\', \\'' + escapeHtml(bot.name) + '\\')" title="Delete bot">' +
                                '🗑️' +
                            '</button>' +
                        '</div>' +
                    '</td>' +
                '</tr>';
            }).join('');
        }

        function setFilter(filter, el) {
            currentFilter = filter;
            document.querySelectorAll('.filter-tab').forEach(tab => tab.classList.remove('active'));
            el.classList.add('active');
            renderBotsTable();
        }

        function handleSearch() {
            renderBotsTable();
        }

        // -----------------------------------------------------------------
        // Bot CRUD
        // -----------------------------------------------------------------
        function openCreateBotModal() {
            document.getElementById('bot-modal-title').innerText = 'Create New Bot';
            document.getElementById('bot-form-id').value = '';
            document.getElementById('bot-form-name').value = '';
            document.getElementById('bot-form-slug').value = '';
            document.getElementById('bot-form-slug').readOnly = false;
            document.getElementById('bot-form-description').value = '';
            document.getElementById('bot-form-long-description').value = '';
            document.getElementById('bot-form-icon-url').value = '';
            document.getElementById('bot-form-category').value = 'utilities';
            document.getElementById('bot-form-runtime').value = 'native_art';
            document.getElementById('bot-form-status').value = 'draft';
            document.getElementById('icon-preview-wrap').style.display = 'none';

            document.getElementById('bot-modal').classList.add('active');
        }

        function handleNameSlugAutoFill() {
            const idField = document.getElementById('bot-form-id').value;
            if (!idField) {
                const name = document.getElementById('bot-form-name').value;
                const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
                document.getElementById('bot-form-slug').value = slug;
            }
        }

        function openEditBotModal(botId) {
            const bot = allBots.find(b => b.id === botId);
            if (!bot) return;

            document.getElementById('bot-modal-title').innerText = 'Edit Bot: ' + bot.name;
            document.getElementById('bot-form-id').value = bot.id;
            document.getElementById('bot-form-name').value = bot.name;
            document.getElementById('bot-form-slug').value = bot.slug;
            document.getElementById('bot-form-slug').readOnly = true;
            document.getElementById('bot-form-description').value = bot.description || '';
            document.getElementById('bot-form-long-description').value = bot.longDescription || '';
            document.getElementById('bot-form-icon-url').value = bot.iconUrl || '';
            document.getElementById('bot-form-category').value = bot.category || 'utilities';
            document.getElementById('bot-form-runtime').value = bot.runtime || 'native_art';
            document.getElementById('bot-form-status').value = bot.status || 'draft';

            if (bot.iconUrl) {
                document.getElementById('icon-preview-img').src = bot.iconUrl;
                document.getElementById('icon-preview-wrap').style.display = 'flex';
            } else {
                document.getElementById('icon-preview-wrap').style.display = 'none';
            }

            document.getElementById('bot-modal').classList.add('active');
        }

        async function saveBotForm() {
            const id = document.getElementById('bot-form-id').value;
            const name = document.getElementById('bot-form-name').value.trim();
            const slug = document.getElementById('bot-form-slug').value.trim();
            const category = document.getElementById('bot-form-category').value;
            const runtime = document.getElementById('bot-form-runtime').value;
            const description = document.getElementById('bot-form-description').value.trim();
            const longDescription = document.getElementById('bot-form-long-description').value.trim();
            const iconUrl = document.getElementById('bot-form-icon-url').value.trim();
            const status = document.getElementById('bot-form-status').value;

            if (!name || !slug || !description) {
                showToast('Please fill in required fields: Name, Slug, Description', 'error');
                return;
            }

            try {
                if (!id) {
                    // Create
                    await apiCall('/api/v1/admin/bots', {
                        method: 'POST',
                        body: JSON.stringify({ name, slug, category, runtime, description, longDescription, iconUrl, status })
                    });
                    showToast('Bot created successfully', 'success');
                } else {
                    // Update
                    await apiCall('/api/v1/admin/bots/' + encodeURIComponent(id), {
                        method: 'PUT',
                        body: JSON.stringify({ name, category, runtime, description, longDescription, iconUrl, status })
                    });
                    showToast('Bot updated successfully', 'success');
                }

                closeModal('bot-modal');
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        async function handleIconFileUpload(input) {
            const file = input.files[0];
            if (!file) return;

            const formData = new FormData();
            formData.append('file', file);

            try {
                showToast('Uploading icon to storage...', 'info');
                const data = await apiCall('/api/v1/admin/upload/icon', {
                    method: 'POST',
                    body: formData
                });

                document.getElementById('bot-form-icon-url').value = data.iconUrl;
                document.getElementById('icon-preview-img').src = data.iconUrl;
                document.getElementById('icon-preview-wrap').style.display = 'flex';
                showToast('Icon uploaded successfully', 'success');
            } catch (err) {
                showToast('Failed to upload icon: ' + err.message, 'error');
            }
        }

        async function changeLifecycle(botId, newStatus) {
            try {
                await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId) + '/lifecycle', {
                    method: 'POST',
                    body: JSON.stringify({ status: newStatus })
                });
                showToast('Lifecycle transitioned to ' + newStatus, 'success');
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
                loadDashboardData();
            }
        }

        async function confirmDeleteBot(botId, botName) {
            if (!confirm('Are you sure you want to delete "' + botName + '"? This will permanently delete all versions, credentials, and packages.')) {
                return;
            }

            try {
                await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId), {
                    method: 'DELETE'
                });
                showToast('Bot deleted successfully', 'success');
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        // -----------------------------------------------------------------
        // Version Management
        // -----------------------------------------------------------------
        async function openVersionModal(botId) {
            const bot = allBots.find(b => b.id === botId);
            if (!bot) return;

            document.getElementById('ver-bot-id').value = bot.id;
            document.getElementById('version-modal-title').innerText = 'Releases & Packages: ' + bot.name;

            // Reset form
            document.getElementById('ver-input-version').value = '';
            document.getElementById('ver-input-size').value = '';
            document.getElementById('ver-input-key').value = '';
            document.getElementById('ver-input-sha256').value = '';
            document.getElementById('ver-input-notes').value = '';
            document.getElementById('ver-input-min-app').value = '1';
            document.getElementById('ver-input-min-runtime').value = '1.0.0';
            document.getElementById('ver-input-status').value = 'published';
            document.getElementById('dropzone-label').innerHTML = 'Click or drag & drop <strong>.botpkg</strong> or <strong>.zip</strong> archive here';
            document.getElementById('btn-submit-version').disabled = true;

            document.getElementById('version-modal').classList.add('active');

            await loadVersionsForBot(bot.id);
        }

        async function loadVersionsForBot(botId) {
            const tbody = document.getElementById('version-list-body');
            tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Loading versions...</td></tr>';

            try {
                const botDetail = await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId));
                const versions = botDetail.versions || [];

                if (versions.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No releases found for this bot. Upload a package below.</td></tr>';
                    return;
                }

                tbody.innerHTML = versions.map(v => {
                    const isPub = v.status === 'published';
                    const statusBadge = isPub
                        ? '<span class="badge badge-published">Published</span>'
                        : '<span class="badge badge-unpublished">Unpublished</span>';

                    const actionBtn = isPub
                        ? '<button class="btn btn-secondary btn-sm" onclick="toggleVersionPublish(\\'' + botId + '\\', \\'' + v.version + '\\', false)">Unpublish</button>'
                        : '<button class="btn btn-success btn-sm" onclick="toggleVersionPublish(\\'' + botId + '\\', \\'' + v.version + '\\', true)">Publish</button>';

                    return '<tr>' +
                        '<td class="mono font-semibold">v' + escapeHtml(v.version) + '</td>' +
                        '<td>' + statusBadge + '</td>' +
                        '<td class="mono">' + formatBytes(v.packageSize) + '</td>' +
                        '<td class="mono" style="font-size: 0.72rem; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="' + escapeHtml(v.sha256) + '">' +
                            escapeHtml(v.sha256) +
                        '</td>' +
                        '<td>' + formatDate(v.publishedAt) + '</td>' +
                        '<td style="text-align: right;">' + actionBtn + '</td>' +
                    '</tr>';
                }).join('');
            } catch (err) {
                tbody.innerHTML = '<tr><td colspan="6" class="empty-state" style="color: var(--status-archived);">' + escapeHtml(err.message) + '</td></tr>';
            }
        }

        async function handlePackageFileSelect(input) {
            const file = input.files[0];
            if (!file) return;

            const dropzoneLabel = document.getElementById('dropzone-label');
            dropzoneLabel.innerHTML = '⏳ Uploading <strong>' + escapeHtml(file.name) + '</strong> (' + formatBytes(file.size) + ')...';

            const formData = new FormData();
            formData.append('file', file);

            try {
                const result = await apiCall('/api/v1/admin/upload/package', {
                    method: 'POST',
                    body: formData
                });

                document.getElementById('ver-input-key').value = result.packageKey;
                document.getElementById('ver-input-size').value = result.packageSize;
                document.getElementById('ver-input-sha256').value = result.sha256;

                // Auto extract version if formatted as name_1.0.0.botpkg
                const verMatch = file.name.match(/_(\d+\.\d+\.\d+)/);
                if (verMatch && !document.getElementById('ver-input-version').value) {
                    document.getElementById('ver-input-version').value = verMatch[1];
                }

                dropzoneLabel.innerHTML = '✓ <strong>' + escapeHtml(file.name) + '</strong> uploaded (' + formatBytes(result.packageSize) + ')<br><span class="mono" style="font-size: 0.72rem;">SHA-256: ' + result.sha256.substring(0, 16) + '...</span>';
                document.getElementById('btn-submit-version').disabled = false;
                showToast('Package uploaded and hashed successfully', 'success');
            } catch (err) {
                dropzoneLabel.innerHTML = '✕ Upload failed: ' + escapeHtml(err.message);
                showToast('Package upload error: ' + err.message, 'error');
            }
        }

        async function submitNewVersion() {
            const botId = document.getElementById('ver-bot-id').value;
            const version = document.getElementById('ver-input-version').value.trim();
            const packageKey = document.getElementById('ver-input-key').value.trim();
            const packageSize = parseInt(document.getElementById('ver-input-size').value, 10);
            const sha256 = document.getElementById('ver-input-sha256').value.trim();
            const minimumAppVersion = parseInt(document.getElementById('ver-input-min-app').value, 10) || 1;
            const minimumRuntimeVersion = document.getElementById('ver-input-min-runtime').value.trim() || '1.0.0';
            const releaseNotes = document.getElementById('ver-input-notes').value.trim();
            const status = document.getElementById('ver-input-status').value;

            if (!version || !packageKey || isNaN(packageSize) || !sha256) {
                showToast('Please specify a version and upload a valid package file', 'error');
                return;
            }

            try {
                await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId) + '/versions', {
                    method: 'POST',
                    body: JSON.stringify({
                        version,
                        packageKey,
                        packageSize,
                        sha256,
                        minimumAppVersion,
                        minimumRuntimeVersion,
                        releaseNotes,
                        status
                    })
                });

                showToast('Version v' + version + ' registered successfully', 'success');
                await loadVersionsForBot(botId);
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        async function toggleVersionPublish(botId, version, shouldPublish) {
            const action = shouldPublish ? 'publish' : 'unpublish';
            try {
                await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId) + '/versions/' + encodeURIComponent(version) + '/' + action, {
                    method: 'POST'
                });
                showToast('Version v' + version + ' ' + (shouldPublish ? 'published' : 'unpublished'), 'success');
                await loadVersionsForBot(botId);
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        // -----------------------------------------------------------------
        // Credential Management
        // -----------------------------------------------------------------
        async function openCredentialModal(botId) {
            const bot = allBots.find(b => b.id === botId);
            if (!bot) return;

            document.getElementById('cred-bot-id').value = bot.id;
            document.getElementById('credential-modal-title').innerText = 'Required Credentials: ' + bot.name;

            // Reset add form
            document.getElementById('new-cred-key').value = '';
            document.getElementById('new-cred-name').value = '';
            document.getElementById('new-cred-desc').value = '';
            document.getElementById('new-cred-type').value = 'password';
            document.getElementById('new-cred-required').checked = true;
            document.getElementById('new-cred-secret').checked = true;

            document.getElementById('credential-modal').classList.add('active');
            await loadCredentialsForBot(bot.id);
        }

        async function loadCredentialsForBot(botId) {
            const tbody = document.getElementById('credential-list-body');
            tbody.innerHTML = '<tr><td colspan="7" class="empty-state">Loading credentials...</td></tr>';

            try {
                const botDetail = await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId));
                const creds = botDetail.credentials || [];

                if (creds.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="7" class="empty-state">No credentials configured for this bot. Add one below.</td></tr>';
                    return;
                }

                tbody.innerHTML = creds.map(c => {
                    return '<tr>' +
                        '<td class="mono font-semibold">' + escapeHtml(c.key) + '</td>' +
                        '<td>' + escapeHtml(c.displayName) + '</td>' +
                        '<td style="color: var(--text-dim);">' + escapeHtml(c.description || '-') + '</td>' +
                        '<td>' + (c.required ? '✓ Yes' : 'No') + '</td>' +
                        '<td>' + (c.secret ? '🔒 Secret' : 'Public') + '</td>' +
                        '<td><span class="badge-tag">' + escapeHtml(c.inputType) + '</span></td>' +
                        '<td style="text-align: right;">' +
                            '<button class="btn btn-danger btn-sm" onclick="deleteCredential(\\'' + botId + '\\', \\'' + escapeHtml(c.key) + '\\')">Delete</button>' +
                        '</td>' +
                    '</tr>';
                }).join('');
            } catch (err) {
                tbody.innerHTML = '<tr><td colspan="7" class="empty-state" style="color: var(--status-archived);">' + escapeHtml(err.message) + '</td></tr>';
            }
        }

        async function addCredentialSubmit() {
            const botId = document.getElementById('cred-bot-id').value;
            const key = document.getElementById('new-cred-key').value.trim();
            const displayName = document.getElementById('new-cred-name').value.trim();
            const description = document.getElementById('new-cred-desc').value.trim();
            const inputType = document.getElementById('new-cred-type').value;
            const required = document.getElementById('new-cred-required').checked;
            const secret = document.getElementById('new-cred-secret').checked;

            if (!key || !displayName) {
                showToast('Please provide a key name and display name', 'error');
                return;
            }

            try {
                await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId) + '/credentials', {
                    method: 'POST',
                    body: JSON.stringify({ key, displayName, description, inputType, required, secret })
                });

                showToast('Credential ' + key + ' saved', 'success');
                document.getElementById('new-cred-key').value = '';
                document.getElementById('new-cred-name').value = '';
                document.getElementById('new-cred-desc').value = '';
                await loadCredentialsForBot(botId);
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        async function deleteCredential(botId, key) {
            if (!confirm('Remove credential requirement "' + key + '"?')) return;

            try {
                await apiCall('/api/v1/admin/bots/' + encodeURIComponent(botId) + '/credentials/' + encodeURIComponent(key), {
                    method: 'DELETE'
                });
                showToast('Credential removed', 'success');
                await loadCredentialsForBot(botId);
                loadDashboardData();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        // -----------------------------------------------------------------
        // Audit Drawer
        // -----------------------------------------------------------------
        function toggleAuditDrawer() {
            const drawer = document.getElementById('audit-drawer');
            const isActive = drawer.classList.contains('active');
            if (!isActive) {
                drawer.classList.add('active');
                loadAuditLogs();
            } else {
                drawer.classList.remove('active');
            }
        }

        async function loadAuditLogs() {
            const list = document.getElementById('audit-logs-list');
            list.innerHTML = '<div class="empty-state">Loading audit logs...</div>';

            try {
                const logs = await apiCall('/api/v1/admin/audit?limit=50');
                if (logs.length === 0) {
                    list.innerHTML = '<div class="empty-state">No audit logs recorded yet.</div>';
                    return;
                }

                list.innerHTML = logs.map(l => {
                    const detailsStr = l.details ? JSON.stringify(l.details, null, 2) : '';
                    return '<div class="audit-item">' +
                        '<div class="audit-meta">' +
                            '<span class="audit-action">' + escapeHtml(l.action) + '</span>' +
                            '<span class="audit-time">' + formatDate(l.createdAt) + '</span>' +
                        '</div>' +
                        '<div style="color: var(--text-muted); font-size: 0.74rem;">' +
                            'Target: <span class="mono">' + escapeHtml(l.entityType) + ' (' + escapeHtml(l.entityId) + ')</span> by <strong>' + escapeHtml(l.actor) + '</strong>' +
                        '</div>' +
                        (detailsStr ? '<pre class="audit-details mono">' + escapeHtml(detailsStr) + '</pre>' : '') +
                    '</div>';
                }).join('');
            } catch (err) {
                list.innerHTML = '<div class="empty-state" style="color: var(--status-archived);">' + escapeHtml(err.message) + '</div>';
            }
        }

        // -----------------------------------------------------------------
        // Initialization
        // -----------------------------------------------------------------
        window.addEventListener('DOMContentLoaded', () => {
            if (checkAuthOrPrompt()) {
                loadDashboardData();
            }
        });
    </script>
</body>
</html>`;
}
