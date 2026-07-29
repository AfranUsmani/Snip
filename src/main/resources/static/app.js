/**
 * Dashboard for the URL Shortener API.
 *
 * The API intentionally has no "list all links" endpoint (a short link is only
 * meaningful to whoever created it), so this dashboard remembers the links you
 * create in this browser via localStorage and refreshes their click counts and
 * analytics from the API. All calls are same-origin against the hosting app.
 */
(() => {
    "use strict";

    const STORAGE_KEY = "snip.links.v2";
    const API = "/api/v1/urls";

    // ---- DOM refs -------------------------------------------------------------
    const $ = (id) => document.getElementById(id);
    const form = $("create-form");
    const urlInput = $("url-input");
    const aliasInput = $("alias-input");
    const aliasPrefix = $("alias-prefix");
    const expirySelect = $("expiry-select");
    const advToggle = $("adv-toggle");
    const advOptions = $("adv-options");
    const createBtn = $("create-btn");
    const btnLabel = createBtn.querySelector(".btn-label");
    const spinner = createBtn.querySelector(".spinner");
    const formError = $("form-error");
    const coldNote = $("cold-note");

    const result = $("result");
    const resultShort = $("result-short");
    const resultOriginal = $("result-original");
    const resultExpiry = $("result-expiry");
    const resultQrImg = $("result-qr-img");
    const resultQrDl = $("result-qr-dl");
    const copyBtn = $("copy-btn");
    const openBtn = $("open-btn");

    const statLinks = $("stat-links");
    const statClicks = $("stat-clicks");
    const statTop = $("stat-top");

    const emptyState = $("empty-state");
    const tableWrap = $("table-wrap");
    const linksBody = $("links-body");
    const refreshBtn = $("refresh-btn");
    const clearBtn = $("clear-btn");
    const bulkBtn = $("bulk-btn");

    const healthPill = $("health-pill");
    const healthText = $("health-text");
    const toast = $("toast");
    const modal = $("modal");
    const modalContent = $("modal-content");

    // ---- storage --------------------------------------------------------------
    function loadLinks() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch {
            return [];
        }
    }
    function saveLinks(links) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(links));
    }
    let links = loadLinks();

    // ---- helpers --------------------------------------------------------------
    function escapeHtml(str) {
        return String(str).replace(/[&<>"']/g, (c) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
        }[c]));
    }

    function relativeTime(iso) {
        const then = new Date(iso).getTime();
        if (Number.isNaN(then)) return "";
        const secs = Math.round((Date.now() - then) / 1000);
        if (secs < 45) return "just now";
        const mins = Math.round(secs / 60);
        if (mins < 60) return `${mins}m ago`;
        const hrs = Math.round(mins / 60);
        if (hrs < 24) return `${hrs}h ago`;
        const days = Math.round(hrs / 24);
        if (days < 30) return `${days}d ago`;
        return new Date(iso).toLocaleDateString();
    }

    /** Future-facing relative time, e.g. "in 3h", "in 5d". */
    function untilTime(iso) {
        const secs = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
        if (secs <= 0) return "expired";
        if (secs < 3600) return `in ${Math.max(1, Math.round(secs / 60))}m`;
        if (secs < 86400) return `in ${Math.round(secs / 3600)}h`;
        return `in ${Math.round(secs / 86400)}d`;
    }

    const isExpired = (l) => l.expiresAt && new Date(l.expiresAt).getTime() <= Date.now();
    const qrPath = (code, size) => `${API}/${encodeURIComponent(code)}/qr${size ? `?size=${size}` : ""}`;

    function showToast(msg) {
        toast.textContent = msg;
        toast.hidden = false;
        void toast.offsetWidth;
        toast.classList.add("show");
        clearTimeout(showToast._t);
        showToast._t = setTimeout(() => {
            toast.classList.remove("show");
            setTimeout(() => { toast.hidden = true; }, 250);
        }, 1900);
    }

    async function copyText(text) {
        try {
            await navigator.clipboard.writeText(text);
            showToast("Copied to clipboard");
        } catch {
            const ta = document.createElement("textarea");
            ta.value = text; ta.style.position = "fixed"; ta.style.opacity = "0";
            document.body.appendChild(ta); ta.select();
            try { document.execCommand("copy"); showToast("Copied to clipboard"); }
            catch { showToast("Copy failed — select and copy manually"); }
            document.body.removeChild(ta);
        }
    }

    // ---- rendering ------------------------------------------------------------
    function renderStats() {
        const totalClicks = links.reduce((sum, l) => sum + (l.hitCount || 0), 0);
        statLinks.textContent = links.length;
        statClicks.textContent = totalClicks;
        const top = links.reduce(
            (best, l) => (l.hitCount || 0) > (best?.hitCount || 0) ? l : best, null);
        statTop.textContent = top && top.hitCount > 0 ? `/${top.shortCode}` : "—";
    }

    function expiryTag(l) {
        if (!l.expiresAt) return "";
        return isExpired(l)
            ? `<span class="expiry-tag dead">expired</span>`
            : `<span class="expiry-tag live" title="${escapeHtml(new Date(l.expiresAt).toLocaleString())}">${untilTime(l.expiresAt)}</span>`;
    }

    function renderLinks() {
        if (links.length === 0) {
            emptyState.hidden = false;
            tableWrap.hidden = true;
            renderStats();
            return;
        }
        emptyState.hidden = true;
        tableWrap.hidden = false;

        const ordered = [...links].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        linksBody.innerHTML = ordered.map((l) => {
            const short = escapeHtml(l.shortUrl);
            const code = escapeHtml(l.shortCode);
            const original = escapeHtml(l.originalUrl);
            const dead = isExpired(l) ? " is-expired" : "";
            return `
                <tr data-code="${code}">
                    <td><a class="cell-short${dead}" href="${short}" target="_blank" rel="noopener">/${code}</a>${expiryTag(l)}</td>
                    <td class="cell-dest"><a href="${original}" target="_blank" rel="noopener" title="${original}">${original}</a></td>
                    <td class="col-clicks"><span class="clicks-badge">${l.hitCount || 0}</span></td>
                    <td class="col-created muted">${relativeTime(l.createdAt)}</td>
                    <td class="col-actions">
                        <div class="row-actions">
                            <button class="icon-btn" data-act="copy" title="Copy short link">⧉</button>
                            <button class="icon-btn" data-act="preview" title="Preview destination (safe)">👁</button>
                            <button class="icon-btn" data-act="qr" title="Show QR code">▦</button>
                            <button class="icon-btn" data-act="analytics" title="Click analytics">📊</button>
                            <button class="icon-btn" data-act="edit" title="Edit destination">✎</button>
                            <button class="icon-btn danger" data-act="remove" title="Remove from this list">✕</button>
                        </div>
                    </td>
                </tr>`;
        }).join("");
        renderStats();
    }

    function upsertLink(link) {
        const idx = links.findIndex((l) => l.shortCode === link.shortCode);
        if (idx >= 0) links[idx] = { ...links[idx], ...link };
        else links.unshift(link);
        saveLinks(links);
        renderLinks();
    }

    // ---- create ---------------------------------------------------------------
    function setLoading(on) {
        createBtn.disabled = on;
        spinner.hidden = !on;
        btnLabel.textContent = on ? "Shortening…" : "Shorten";
    }

    function readOptions() {
        const opts = {};
        const alias = aliasInput.value.trim();
        if (alias) opts.customAlias = alias;
        const ttl = parseInt(expirySelect.value, 10);
        if (ttl > 0) opts.expiresAt = new Date(Date.now() + ttl * 1000).toISOString();
        return opts;
    }

    async function createLink(url) {
        formError.hidden = true;
        setLoading(true);
        const coldTimer = setTimeout(() => { coldNote.hidden = false; }, 2500);
        try {
            const res = await fetch(API, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ url, ...readOptions() }),
            });
            if (!res.ok) {
                let msg = `Request failed (${res.status})`;
                try {
                    const err = await res.json();
                    if (err && Array.isArray(err.messages) && err.messages.length) msg = err.messages.join("; ");
                    else if (err && err.error) msg = err.error;
                } catch { /* non-JSON error body */ }
                if (res.status === 409) msg = "That alias is already taken — try another.";
                throw new Error(msg);
            }
            const data = await res.json();
            upsertLink(data);
            showResult(data);
            urlInput.value = "";
            aliasInput.value = "";
            expirySelect.value = "0";
            showToast("Short link created");
        } catch (e) {
            formError.textContent = e.message || "Something went wrong.";
            formError.hidden = false;
        } finally {
            clearTimeout(coldTimer);
            coldNote.hidden = true;
            setLoading(false);
        }
    }

    function showResult(data) {
        resultShort.textContent = data.shortUrl;
        resultShort.href = data.shortUrl;
        resultOriginal.textContent = data.originalUrl;
        result.dataset.short = data.shortUrl;
        resultQrImg.src = qrPath(data.shortCode, 240);
        resultQrDl.href = qrPath(data.shortCode, 600);
        resultQrDl.setAttribute("download", `${data.shortCode}-qr.png`);
        if (data.expiresAt) {
            resultExpiry.textContent = `⏳ Expires ${untilTime(data.expiresAt)} · ${new Date(data.expiresAt).toLocaleString()}`;
            resultExpiry.hidden = false;
        } else {
            resultExpiry.hidden = true;
        }
        result.hidden = false;
    }

    // ---- stats refresh --------------------------------------------------------
    async function refreshOne(code) {
        const res = await fetch(`${API}/${encodeURIComponent(code)}`);
        if (!res.ok) throw new Error(`stats ${res.status}`);
        return res.json();
    }

    async function refreshAll() {
        if (links.length === 0) return;
        refreshBtn.disabled = true;
        const original = refreshBtn.textContent;
        refreshBtn.textContent = "↻ Refreshing…";
        let gone = 0;
        const results = await Promise.allSettled(links.map((l) => refreshOne(l.shortCode)));
        const fresh = [];
        results.forEach((r, i) => {
            if (r.status === "fulfilled") fresh.push({ ...links[i], ...r.value });
            else if (/\b404\b/.test(r.reason?.message || "")) gone += 1;
            else fresh.push(links[i]);
        });
        links = fresh;
        saveLinks(links);
        renderLinks();
        flashBadges();
        refreshBtn.disabled = false;
        refreshBtn.textContent = original;
        showToast(gone > 0 ? `Clicks updated · ${gone} stale link(s) removed` : "Clicks updated");
    }

    function flashBadges() {
        linksBody.querySelectorAll(".clicks-badge").forEach((b) => {
            b.classList.remove("bump"); void b.offsetWidth; b.classList.add("bump");
        });
    }

    // ---- modal (QR + analytics) ----------------------------------------------
    function openModal(html) {
        modalContent.innerHTML = html;
        modal.hidden = false;
    }
    function closeModal() {
        modal.hidden = true;
        modalContent.innerHTML = "";
    }
    modal.addEventListener("click", (e) => { if (e.target.hasAttribute("data-close")) closeModal(); });
    document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !modal.hidden) closeModal(); });

    function showQrModal(link) {
        openModal(`
            <div class="qr-modal">
                <p class="modal-title">/${escapeHtml(link.shortCode)}</p>
                <p class="modal-sub">${escapeHtml(link.shortUrl)}</p>
                <img src="${qrPath(link.shortCode, 300)}" width="240" height="240" alt="QR code" />
                <div><a class="btn-ghost" href="${qrPath(link.shortCode, 600)}" download="${escapeHtml(link.shortCode)}-qr.png">Download PNG</a></div>
            </div>`);
    }

    function bars(title, map) {
        const entries = Object.entries(map || {});
        if (entries.length === 0) return `<div class="an-section"><h4>${title}</h4><div class="an-empty small">No data yet</div></div>`;
        const max = Math.max(...entries.map(([, v]) => v), 1);
        const rows = entries.slice(0, 6).map(([name, val]) => `
            <div class="an-bar-row">
                <span class="name" title="${escapeHtml(name)}">${escapeHtml(name)}</span>
                <span class="an-bar-track"><span class="an-bar-fill" style="width:${(val / max) * 100}%"></span></span>
                <span class="val">${val}</span>
            </div>`).join("");
        return `<div class="an-section"><h4>${title}</h4>${rows}</div>`;
    }

    function timeline(days) {
        if (!days || days.length === 0) return "";
        const max = Math.max(...days.map((d) => d.count), 1);
        const cols = days.map((d) => {
            const pct = (d.count / max) * 100;
            const zero = d.count === 0 ? " zero" : "";
            return `<div class="an-day" title="${d.date}: ${d.count}"><span class="col${zero}" style="height:${Math.max(pct, 2)}%"></span></div>`;
        }).join("");
        const first = days[0].date.slice(5), last = days[days.length - 1].date.slice(5);
        return `<div class="an-section"><h4>Clicks · last ${days.length} days</h4>
            <div class="an-timeline">${cols}</div>
            <div class="an-axis"><span>${first}</span><span>${last}</span></div></div>`;
    }

    async function showAnalyticsModal(link) {
        openModal(`<div class="qr-modal"><p class="modal-title">/${escapeHtml(link.shortCode)}</p>
            <p class="modal-sub">Loading analytics…</p></div>`);
        try {
            const res = await fetch(`${API}/${encodeURIComponent(link.shortCode)}/analytics`);
            if (!res.ok) throw new Error(String(res.status));
            const a = await res.json();
            openModal(`
                <p class="modal-title">/${escapeHtml(a.shortCode)}</p>
                <p class="modal-sub">${escapeHtml(link.originalUrl)}</p>
                <div class="an-total"><span class="num">${a.totalClicks}</span><span class="lbl">Total clicks</span></div>
                ${timeline(a.clicksByDay)}
                ${bars("Devices", a.byDevice)}
                ${bars("Browsers", a.byBrowser)}
                ${bars("Sources", a.byReferrer)}
                ${a.totalClicks === 0 ? '<div class="an-empty small">No clicks yet — open the short link to generate some.</div>' : ""}
            `);
        } catch {
            openModal(`<div class="qr-modal"><p class="modal-title">/${escapeHtml(link.shortCode)}</p>
                <p class="an-empty">Couldn't load analytics.</p></div>`);
        }
    }

    // ---- edit (change destination) -------------------------------------------
    /** Maps the edit modal's expiry choice to an ISO instant (or null to clear). */
    function readEditExpiry(link, value) {
        if (value === "keep") return (link.expiresAt && !isExpired(link)) ? link.expiresAt : null;
        const ttl = parseInt(value, 10);
        return ttl > 0 ? new Date(Date.now() + ttl * 1000).toISOString() : null;
    }

    function showEditModal(link) {
        openModal(`
            <p class="modal-title">Edit /${escapeHtml(link.shortCode)}</p>
            <p class="modal-sub">Change where this link points — the code and QR stay the same.</p>
            <form id="edit-form" class="modal-form">
                <label for="edit-url">Destination URL</label>
                <input id="edit-url" type="url" required value="${escapeHtml(link.originalUrl)}" />
                <label for="edit-expiry">Expiry</label>
                <select id="edit-expiry">
                    <option value="keep">Keep current</option>
                    <option value="0">Never (clear)</option>
                    <option value="3600">In 1 hour</option>
                    <option value="86400">In 1 day</option>
                    <option value="604800">In 7 days</option>
                    <option value="2592000">In 30 days</option>
                </select>
                <button type="submit" class="primary">Save changes</button>
                <p id="edit-error" class="form-error" role="alert" hidden></p>
            </form>`);

        const editForm = $("edit-form");
        editForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const url = $("edit-url").value.trim();
            const btn = editForm.querySelector(".primary");
            const err = $("edit-error");
            err.hidden = true;
            btn.disabled = true; btn.textContent = "Saving…";
            try {
                const body = { url, expiresAt: readEditExpiry(link, $("edit-expiry").value) };
                const res = await fetch(`${API}/${encodeURIComponent(link.shortCode)}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(body),
                });
                if (!res.ok) {
                    let msg = `Update failed (${res.status})`;
                    try { const j = await res.json(); if (j.messages?.length) msg = j.messages.join("; "); } catch { /* */ }
                    throw new Error(msg);
                }
                const data = await res.json();
                upsertLink(data);
                closeModal();
                showToast("Destination updated");
            } catch (ex) {
                err.textContent = ex.message; err.hidden = false;
                btn.disabled = false; btn.textContent = "Save changes";
            }
        });
    }

    // ---- bulk shorten ---------------------------------------------------------
    function showBulkModal() {
        openModal(`
            <p class="modal-title">Bulk shorten</p>
            <p class="modal-sub">Paste up to 50 URLs, one per line.</p>
            <form id="bulk-form" class="modal-form">
                <textarea id="bulk-input" placeholder="https://example.com/one&#10;https://example.com/two"></textarea>
                <button type="submit" class="primary">Shorten all</button>
            </form>
            <div id="bulk-result" class="bulk-result"></div>`);

        $("bulk-form").addEventListener("submit", async (e) => {
            e.preventDefault();
            const urls = $("bulk-input").value.split("\n").map((s) => s.trim()).filter(Boolean);
            const out = $("bulk-result");
            if (urls.length === 0) { out.innerHTML = `<span class="err">Add at least one URL.</span>`; return; }
            if (urls.length > 50) { out.innerHTML = `<span class="err">Max 50 URLs per batch.</span>`; return; }

            const btn = $("bulk-form").querySelector(".primary");
            btn.disabled = true; btn.textContent = "Shortening…";
            try {
                const res = await fetch(`${API}/bulk`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ urls }),
                });
                if (!res.ok) throw new Error(`Request failed (${res.status})`);
                const data = await res.json();
                const now = new Date().toISOString();
                data.results.filter((r) => r.success).forEach((r) => {
                    upsertLink({
                        shortCode: r.shortCode, shortUrl: r.shortUrl, originalUrl: r.url,
                        hitCount: 0, createdAt: now, expiresAt: null,
                    });
                });
                const fails = data.results.filter((r) => !r.success);
                out.innerHTML = `<span class="ok">✓ Created ${data.created}</span>`
                    + (data.failed ? ` · <span class="err">${data.failed} failed</span>` : "")
                    + (fails.length ? `<ul>${fails.map((f) => `<li>${escapeHtml(f.url)} — ${escapeHtml(f.error)}</li>`).join("")}</ul>` : "");
                showToast(`Created ${data.created} link(s)`);
            } catch (ex) {
                out.innerHTML = `<span class="err">${escapeHtml(ex.message)}</span>`;
            } finally {
                btn.disabled = false; btn.textContent = "Shorten all";
            }
        });
    }

    // ---- health ---------------------------------------------------------------
    async function pollHealth() {
        try {
            const res = await fetch("/actuator/health", { cache: "no-store" });
            const data = await res.json();
            const up = res.ok && data.status === "UP";
            healthPill.className = `pill ${up ? "pill-up" : "pill-down"}`;
            healthText.textContent = up ? "API healthy" : (data.status || "degraded");
        } catch {
            healthPill.className = "pill pill-down";
            healthText.textContent = "unreachable";
        }
    }

    // ---- events ---------------------------------------------------------------
    form.addEventListener("submit", (e) => {
        e.preventDefault();
        const url = urlInput.value.trim();
        if (url) createLink(url);
    });

    advToggle.addEventListener("click", () => {
        const open = advOptions.hidden;
        advOptions.hidden = !open;
        advToggle.setAttribute("aria-expanded", String(open));
    });

    copyBtn.addEventListener("click", () => { if (result.dataset.short) copyText(result.dataset.short); });
    openBtn.addEventListener("click", () => { if (result.dataset.short) window.open(result.dataset.short, "_blank", "noopener"); });

    linksBody.addEventListener("click", async (e) => {
        const btn = e.target.closest(".icon-btn");
        if (!btn) return;
        const code = btn.closest("tr")?.dataset.code;
        const link = links.find((l) => l.shortCode === code);
        if (!link) return;
        const act = btn.dataset.act;

        if (act === "copy") {
            copyText(link.shortUrl);
        } else if (act === "preview") {
            window.open(`/preview/${encodeURIComponent(code)}`, "_blank", "noopener");
        } else if (act === "qr") {
            showQrModal(link);
        } else if (act === "analytics") {
            showAnalyticsModal(link);
        } else if (act === "edit") {
            showEditModal(link);
        } else if (act === "remove") {
            try {
                // 204 = deleted server-side; 404 = already gone. Either way, drop it locally.
                await fetch(`${API}/${encodeURIComponent(code)}`, { method: "DELETE" });
            } catch (_) {
                // Network/offline — still remove from this browser's list.
            }
            links = links.filter((l) => l.shortCode !== code);
            saveLinks(links);
            renderLinks();
            showToast("Deleted");
        }
    });

    refreshBtn.addEventListener("click", refreshAll);
    bulkBtn.addEventListener("click", showBulkModal);
    clearBtn.addEventListener("click", () => {
        if (links.length === 0) return;
        if (!confirm("Remove all links from this browser? (Server data is untouched.)")) return;
        links = [];
        saveLinks(links);
        result.hidden = true;
        renderLinks();
        showToast("Cleared");
    });

    // ---- Theme toggle ---------------------------------------------------------
    // The inline head script already set data-theme before first paint; here we
    // just sync the button + meta to it, and wire the toggle.
    const themeToggle = $("theme-toggle");
    const metaThemeColor = $("meta-theme-color");
    const THEME_KEY = "snip.theme";
    const THEME_BG = { dark: "#0f1115", light: "#f6f7fb" };

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-theme", theme);
        if (themeToggle) themeToggle.setAttribute("aria-checked", String(theme === "dark"));
        if (metaThemeColor) metaThemeColor.setAttribute("content", THEME_BG[theme] || THEME_BG.dark);
    }

    applyTheme(document.documentElement.getAttribute("data-theme") || "dark");

    if (themeToggle) {
        themeToggle.addEventListener("click", () => {
            const next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
            applyTheme(next);
            try { localStorage.setItem(THEME_KEY, next); } catch (_) {}
        });
    }

    // Follow OS theme changes only until the user makes an explicit choice.
    window.matchMedia("(prefers-color-scheme: light)").addEventListener("change", (e) => {
        try { if (localStorage.getItem(THEME_KEY)) return; } catch (_) {}
        applyTheme(e.matches ? "light" : "dark");
    });

    // ---- init -----------------------------------------------------------------
    aliasPrefix.textContent = location.host + "/";
    renderLinks();
    pollHealth();
    setInterval(pollHealth, 20000);
    if (links.length > 0) refreshAll();

    // Register the service worker (offline app shell + snappier cold starts).
    if ("serviceWorker" in navigator) {
        window.addEventListener("load", () => {
            navigator.serviceWorker.register("/sw.js").catch(() => {});
        });
    }
})();
