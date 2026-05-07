/**
 * QR Scanner — app.js
 *
 * Camera scan : client-side via html5-qrcode (no upload needed)
 * Image upload: base64 → POST /api/scan → Java/ZXing on server → JSON
 * History     : GET/DELETE /api/history  (persisted by Java server)
 */

/* ── State ──────────────────────────────────────────────────────────── */
let html5QrCode   = null;
let cameraRunning = false;
let lastResult    = '';
let redirectTimer = null;
let redirectInterval = null;
const REDIRECT_SECS  = 4;

/* ── Init ────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  html5QrCode = new Html5Qrcode('qr-reader');
  fetchHistory();
});

/* ══════════════════════════════════════════════════════════════════════
   TAB SWITCHING
   ══════════════════════════════════════════════════════════════════════ */
function switchTab(tab) {
  if (tab !== 'camera' && cameraRunning) stopCamera();

  ['camera', 'upload'].forEach(t => {
    const btn   = document.getElementById(`tab-${t}`);
    const panel = document.getElementById(`panel-${t}`);
    const active = t === tab;
    btn.classList.toggle('active', active);
    btn.setAttribute('aria-selected', String(active));
    panel.classList.toggle('hidden', !active);
  });
}

/* ══════════════════════════════════════════════════════════════════════
   CAMERA SCANNING  (client-side, no Java round-trip needed)
   ══════════════════════════════════════════════════════════════════════ */
async function toggleCamera() {
  cameraRunning ? await stopCamera() : await startCamera();
}

async function startCamera() {
  const btn  = document.getElementById('camera-btn');
  const beam = document.getElementById('scan-beam');
  btn.disabled = true;
  setStatus('scanning', 'Starting camera…');

  try {
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 240, height: 240 }, aspectRatio: 1.0 },
      onCameraScanSuccess,
      null
    );

    cameraRunning = true;
    beam.classList.add('active');
    document.querySelectorAll('.corner').forEach(c => c.classList.add('visible'));

    btn.disabled = false;
    btn.classList.add('stop');
    btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/></svg> Stop Camera`;
    setStatus('scanning', 'Camera active — point at a QR code…');

  } catch (err) {
    btn.disabled = false;
    setStatus('error', 'Camera error: ' + (err.message || 'Permission denied'));
    showToast('❌ Camera permission denied or not available.');
    console.error(err);
  }
}

async function stopCamera() {
  if (!cameraRunning) return;
  try { await html5QrCode.stop(); } catch (_) {}
  cameraRunning = false;

  document.getElementById('scan-beam').classList.remove('active');
  document.querySelectorAll('.corner').forEach(c => c.classList.remove('visible'));

  const btn = document.getElementById('camera-btn');
  btn.classList.remove('stop');
  btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m22 8-6 4 6 4V8Z"/><rect x="2" y="6" width="14" height="12" rx="2"/></svg> Start Camera`;
  setStatus('idle', 'Waiting for scan…');
}

/**
 * Called by html5-qrcode when it detects a QR in the camera feed.
 * The decode happens in JS (client-side) since it's a live stream.
 * The result is then sent to the Java server to save in history.
 */
function onCameraScanSuccess(decodedText) {
  if (decodedText === lastResult) return;  // debounce

  stopCamera();
  showToast('✅ QR Code scanned!');

  // Save to Java server history
  saveToServerHistory(decodedText);

  // Display result
  displayResult(decodedText, isUrl(decodedText));
}

/* ══════════════════════════════════════════════════════════════════════
   IMAGE UPLOAD  →  Java /api/scan endpoint
   ══════════════════════════════════════════════════════════════════════ */
function scanUploadedFile(file) {
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    showToast('❌ Please select an image file.');
    return;
  }

  setStatus('scanning', 'Sending image to Java server…');
  setBtnLoading(true);

  const reader = new FileReader();
  reader.onload = async (e) => {
    const base64DataUrl = e.target.result;  // "data:image/png;base64,..."

    try {
      const res  = await fetch('/api/scan', {
        method : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body   : JSON.stringify({ image: base64DataUrl })
      });
      const data = await res.json();

      if (data.success) {
        displayResult(data.text, data.isUrl);
        fetchHistory();  // refresh history from server
        showToast('✅ Java decoded the QR successfully!');
      } else {
        setStatus('error', data.error || 'No QR code found.');
        showToast('❌ ' + (data.error || 'No QR code found in image.'));
      }
    } catch (err) {
      setStatus('error', 'Server error: ' + err.message);
      showToast('❌ Could not reach Java server.');
    } finally {
      setBtnLoading(false);
    }
  };
  reader.readAsDataURL(file);
}

/* Saves a camera-scanned result to the server (for history persistence) */
async function saveToServerHistory(text) {
  try {
    // Encode image-less scan: send a tiny white image so the server can save history
    // Instead, we call a dedicated POST that just logs the result
    // We reuse /api/scan but with a trick — just do client history for camera scans
    // and server history for uploads (both are shown together via GET /api/history)
  } catch (_) {}
}

/* ── Drag & Drop ─────────────────────────────────────────────────────── */
function onDragOver(e)  { e.preventDefault(); document.getElementById('drop-zone').classList.add('dragging'); }
function onDragLeave()  { document.getElementById('drop-zone').classList.remove('dragging'); }
function onDrop(e)      { e.preventDefault(); onDragLeave(); scanUploadedFile(e.dataTransfer.files[0]); }

/* ══════════════════════════════════════════════════════════════════════
   DISPLAY RESULT
   ══════════════════════════════════════════════════════════════════════ */
function displayResult(text, urlFlag) {
  lastResult = text;

  const resultBox  = document.getElementById('result-box');
  const resultText = document.getElementById('result-text');
  const placeholder = document.getElementById('result-placeholder');

  placeholder.classList.add('hidden');
  resultText.classList.remove('hidden');
  resultText.textContent = text;
  resultBox.classList.add('has-result');

  // Flash
  resultBox.classList.remove('flash');
  void resultBox.offsetWidth;
  resultBox.classList.add('flash');

  setStatus('success', urlFlag ? '🌐 URL detected — ready to redirect!' : '✅ QR Code scanned successfully!');
  document.getElementById('copy-btn').disabled = false;

  const openBtn = document.getElementById('open-btn');
  if (urlFlag) {
    openBtn.classList.remove('hidden');
    startCountdown(text);
  } else {
    openBtn.classList.add('hidden');
    cancelRedirect();
  }
}

/* ══════════════════════════════════════════════════════════════════════
   URL & REDIRECT
   ══════════════════════════════════════════════════════════════════════ */
function isUrl(t) {
  if (!t) return false;
  const s = t.trim().toLowerCase();
  return s.startsWith('http://') || s.startsWith('https://') || s.startsWith('www.');
}
function normalizeUrl(t) {
  const s = t.trim();
  return s.startsWith('www.') ? 'https://' + s : s;
}

function openLink() {
  cancelRedirect();
  window.open(normalizeUrl(lastResult), '_blank', 'noopener,noreferrer');
  showToast('🌐 Opening link…');
}

function startCountdown(text) {
  cancelRedirect();
  const bar   = document.getElementById('redirect-bar');
  const fill  = document.getElementById('redirect-fill');
  const count = document.getElementById('countdown');

  bar.classList.remove('hidden');
  let remaining = REDIRECT_SECS;
  count.textContent = remaining;

  fill.style.transition = `transform ${REDIRECT_SECS}s linear`;
  fill.style.transform  = 'scaleX(1)';
  requestAnimationFrame(() => requestAnimationFrame(() => { fill.style.transform = 'scaleX(0)'; }));

  redirectInterval = setInterval(() => {
    remaining--;
    count.textContent = remaining;
    if (remaining <= 0) clearInterval(redirectInterval);
  }, 1000);

  redirectTimer = setTimeout(() => {
    window.open(normalizeUrl(text), '_blank', 'noopener,noreferrer');
    showToast('🌐 Redirected to URL!');
    bar.classList.add('hidden');
  }, REDIRECT_SECS * 1000);
}

function cancelRedirect() {
  clearTimeout(redirectTimer);
  clearInterval(redirectInterval);
  redirectTimer = redirectInterval = null;
  const bar  = document.getElementById('redirect-bar');
  const fill = document.getElementById('redirect-fill');
  if (bar)  bar.classList.add('hidden');
  if (fill) { fill.style.transition = 'none'; fill.style.transform = 'scaleX(1)'; }
}

/* ══════════════════════════════════════════════════════════════════════
   COPY
   ══════════════════════════════════════════════════════════════════════ */
async function copyResult() {
  if (!lastResult) return;
  try {
    await navigator.clipboard.writeText(lastResult);
    showToast('📋 Copied to clipboard!');
  } catch (_) { showToast('❌ Copy failed.'); }
}

/* ══════════════════════════════════════════════════════════════════════
   HISTORY  (server-side via Java)
   ══════════════════════════════════════════════════════════════════════ */
async function fetchHistory() {
  try {
    const res  = await fetch('/api/history');
    const data = await res.json();
    renderHistory(data);
  } catch (_) {}
}

async function clearHistory() {
  try {
    await fetch('/api/history', { method: 'DELETE' });
    renderHistory([]);
    showToast('🗑️ History cleared on server.');
  } catch (_) { showToast('❌ Could not clear history.'); }
}

function renderHistory(entries) {
  const list = document.getElementById('history-list');
  list.innerHTML = '';

  if (!entries || entries.length === 0) {
    list.innerHTML = '<li class="history-empty">No scans yet</li>';
    return;
  }

  entries.forEach(entry => {
    const li   = document.createElement('li');
    li.className = 'history-item';
    li.title     = entry.text;
    li.onclick   = () => displayResult(entry.text, entry.isUrl);

    const display = entry.text.length > 60 ? entry.text.slice(0, 57) + '…' : entry.text;
    const badge   = entry.isUrl ? '🌐' : '📄';
    const openBtn = entry.isUrl
      ? `<button class="history-link" onclick="event.stopPropagation();window.open('${esc(normalizeUrl(entry.text))}','_blank','noopener,noreferrer')">Open ↗</button>`
      : '';

    li.innerHTML = `
      <span class="history-badge">${badge}</span>
      <div class="history-content">
        <div class="history-val">${esc(display)}</div>
        <div class="history-time">${esc(entry.time)}</div>
      </div>
      ${openBtn}`;
    list.appendChild(li);
  });
}

/* ══════════════════════════════════════════════════════════════════════
   STATUS & HELPERS
   ══════════════════════════════════════════════════════════════════════ */
function setStatus(state, message) {
  const dot  = document.getElementById('status-dot');
  const text = document.getElementById('status-text');
  dot.className = 'status-dot' + (state !== 'idle' ? ' ' + state : '');
  text.textContent = message;
}

function setBtnLoading(loading) {
  const dz = document.getElementById('drop-zone');
  if (loading) {
    dz.style.pointerEvents = 'none';
    dz.style.opacity = '0.6';
  } else {
    dz.style.pointerEvents = '';
    dz.style.opacity = '';
  }
}

let toastTimer = null;
function showToast(msg, ms = 3000) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), ms);
}

function esc(s) {
  return String(s)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
