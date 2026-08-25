// Support for being embedded as a trading-desk tab (?embed=1): the desk would supply the outer
// chrome, so this hides the app's own topbar/status bar (see app.css .embedded). The desk carries
// no Risk tab today — this site is standalone, so the class is never added — and the support stays
// because the roadmap wants that tab back once it shows the desk's positions rather than a sample
// book, which is why it was removed.
if (new URLSearchParams(location.search).has("embed")) {
  document.body.classList.add("embedded");
}

const form = document.getElementById("book");
const reportEl = document.getElementById("report");
const errorEl = document.getElementById("error");
const ageEl = document.getElementById("age");
const reqsEl = document.getElementById("reqs");
const stValuationEl = document.getElementById("st-valuation");
const stDeltaEl = document.getElementById("st-delta");
const stVarKEl = document.getElementById("st-var-k");
const stVarEl = document.getElementById("st-var");
const stPnlEl = document.getElementById("st-pnl");

// Fields the form displays as a percentage (e.g. "10" for 10%) but the API takes as a decimal
// fraction (0.10). submitForm divides by 100 on the way out, so the conversion happens once.
const PERCENT_FIELDS = ["riskFreeRate", "dividendYield", "confidence"];

let requestCount = 0;
let lastRecomputeAt = 0;

const num = (n, dp = 2) =>
  n.toLocaleString(undefined, {
    minimumFractionDigits: dp,
    maximumFractionDigits: dp,
  });

const signClass = (n) => (n < 0 ? "neg" : "pos");
const signed = (n) => `<span class="${signClass(n)}">${num(n)}</span>`;

function block(title, sub, body) {
  const subHtml = sub ? `<span class="sub">${sub}</span>` : "";
  return `<div class="report-block"><h3>${title}${subHtml}</h3>${body}</div>`;
}

function rows(pairs) {
  const body = pairs
    .map(([k, v]) => `<tr><td>${k}</td><td>${v}</td></tr>`)
    .join("");
  return `<table class="risk"><tbody>${body}</tbody></table>`;
}

function renderStats(r, pct) {
  stValuationEl.textContent = num(r.valuation);
  stValuationEl.className = `v ${signClass(r.valuation)}`;

  const delta = r.greeks.delta;
  stDeltaEl.textContent = num(delta);
  stDeltaEl.className = `v ${signClass(delta)} ${delta >= 0 ? "up" : "down"}`;

  const worstVar = Math.max(
    r.var.parametric.valueAtRisk,
    r.var.historical.valueAtRisk,
  );
  stVarKEl.textContent = `VaR ${pct}%`;
  stVarEl.textContent = num(worstVar);
  stVarEl.className = "v neg";

  if (r.pnl) {
    const pnl = r.pnl.actual;
    stPnlEl.textContent = num(pnl);
    stPnlEl.className = `v ${signClass(pnl)} ${pnl >= 0 ? "up" : "down"}`;
  } else {
    stPnlEl.textContent = "—";
    stPnlEl.className = "v";
  }
}

function render(r) {
  const g = r.greeks;
  const greeks = block(
    "Greeks",
    null,
    rows([
      ["delta", signed(g.delta)],
      ["gamma", signed(g.gamma)],
      ["vega", signed(g.vega)],
      ["theta", signed(g.theta)],
      ["rho", signed(g.rho)],
    ]),
  );

  const pct = Math.round(r.confidence * 100);
  const v = r.var;
  const varBlock = block(
    "Value at Risk / ES",
    `${pct}%`,
    `<table class="risk">
       <thead><tr><th>method</th><th>VaR</th><th>ES</th></tr></thead>
       <tbody>
         <tr><td>parametric</td><td>${num(v.parametric.valueAtRisk)}</td><td>${num(v.parametric.expectedShortfall)}</td></tr>
         <tr><td>historical</td><td>${num(v.historical.valueAtRisk)}</td><td>${num(v.historical.expectedShortfall)}</td></tr>
       </tbody>
     </table>`,
  );

  const blocks = [greeks, varBlock];
  if (r.pnl) {
    const p = r.pnl;
    blocks.push(
      block(
        "PnL Explain",
        null,
        rows([
          ["actual", signed(p.actual)],
          ["delta", signed(p.delta)],
          ["gamma", signed(p.gamma)],
          ["vega", signed(p.vega)],
          ["theta", signed(p.theta)],
          ["rho", signed(p.rho)],
          ["residual", signed(p.residual)],
        ]),
      ),
    );
  }
  reportEl.innerHTML = blocks.join("");
  renderStats(r, pct);
}

function touchStatus() {
  requestCount += 1;
  lastRecomputeAt = Date.now();
  reqsEl.textContent = requestCount;
  ageEl.textContent = "just now";
}

setInterval(() => {
  if (!lastRecomputeAt) return;
  const age = (Date.now() - lastRecomputeAt) / 1000;
  ageEl.textContent = age < 1.5 ? "just now" : `${Math.round(age)}s ago`;
}, 1000);

async function load(method, body) {
  errorEl.hidden = true;
  try {
    const res = await fetch(
      "api/report",
      method === "POST" ? { method, body } : {},
    );
    const json = await res.json();
    if (!res.ok) throw new Error(json.error || `HTTP ${res.status}`);
    render(json);
    touchStatus();
  } catch (e) {
    errorEl.textContent = e.message;
    errorEl.hidden = false;
  }
}

function submitForm() {
  const data = new FormData(form);
  for (const field of PERCENT_FIELDS) {
    const raw = data.get(field);
    if (raw !== null && raw !== "") data.set(field, String(Number(raw) / 100));
  }
  load("POST", new URLSearchParams(data).toString());
}

form.addEventListener("submit", (e) => {
  e.preventDefault();
  submitForm();
});

// Recompute as soon as a field is committed (blur, Enter, or a select change) -- a trader
// shouldn't have to reach for a button after every edit. The button stays for an explicit
// re-run and for input methods where "change" doesn't fire predictably.
form.addEventListener("change", submitForm);

load("GET");
