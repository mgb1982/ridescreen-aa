// SPDX-License-Identifier: AGPL-3.0-or-later
// RideScreen iOS — App Store Server Notifications (version 2) → one Telegram line per purchase or refund.
//
//   POST /v1/apple    body {"signedPayload": "<JWS>"}; App Store Connect › App › App Information ›
//                     App Store Server Notifications › Production (and Sandbox) Server URL.
//
// Apple signs every notification (ES256, certificate chain in the JWS header, root = "Apple Root CA - G3").
// The signature is checked here, but a failed or unsupported check never drops the alert: the message is then
// tagged "firma sin verificar", because the worst a forged request can do is send the owner a wrong Telegram
// line. Nothing is stored, and no personal data is in these notifications (no Apple ID, no name, no device).

export const ASN_BUNDLE_ID = "com.mgb1982.RideScreenIOS";
// SHA-256 of the DER of "Apple Root CA - G3" (https://www.apple.com/certificateauthority/).
export const APPLE_ROOT_G3_SHA256 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179";

const MAX_BODY = 64 * 1024;

const TYPES = {
  ONE_TIME_CHARGE: "💶 <b>Compra</b>",
  REFUND: "↩️ <b>Reembolso</b>",
  REFUND_REVERSED: "✅ <b>Reembolso revertido</b>",
  REFUND_DECLINED: "🚫 <b>Reembolso rechazado</b>",
  REVOKE: "⛔ <b>Compra revocada</b> (Compartir en familia)",
  CONSUMPTION_REQUEST: "❓ <b>Apple pide datos de un reembolso</b>",
  TEST: "🔔 <b>Aviso de prueba de Apple</b>",
};

const STOREFRONTS = {
  THA: "Tailandia", ESP: "España", ARG: "Argentina", MYS: "Malasia", MAR: "Marruecos", MKD: "Macedonia del Norte",
  TUR: "Turquía", USA: "EE. UU.", ITA: "Italia", GRC: "Grecia", FRA: "Francia", DEU: "Alemania", PRT: "Portugal",
  GBR: "Reino Unido", SGP: "Singapur", MEX: "México", COL: "Colombia", CHL: "Chile", BRA: "Brasil", IND: "India",
};

/**
 * deps.notify(html): sends the Telegram line (it applies the alert budget).
 * deps.firstTime(uuid): true the first time this notification id is seen (Apple retries until it gets a 200).
 */
export async function handleApple(request, env, deps) {
  const len = Number(request.headers.get("content-length") || 0);
  if (len > MAX_BODY) return new Response("too large", { status: 413 });
  const raw = await request.text();
  if (raw.length > MAX_BODY) return new Response("too large", { status: 413 });
  let signed;
  try {
    signed = JSON.parse(raw).signedPayload;
  } catch {
    return new Response("bad json", { status: 400 });
  }
  const outer = await openJws(signed);
  if (!outer) return new Response("bad payload", { status: 400 });
  const n = outer.payload;
  const data = n.data || {};
  if (data.bundleId && data.bundleId !== ASN_BUNDLE_ID) return new Response("ok"); // another app of the account
  if (n.notificationUUID && !(await deps.firstTime(String(n.notificationUUID)))) return new Response("ok");

  let tx = null;
  let txVerified = true;
  if (data.signedTransactionInfo) {
    const inner = await openJws(data.signedTransactionInfo);
    if (inner) { tx = inner.payload; txVerified = inner.verified; }
  }
  await deps.notify(describe(n, tx, outer.verified && txVerified));
  return new Response("ok");
}

/** Pure: the Telegram text for a decoded notification and transaction (exported for the tests). */
export function describe(n, tx, verified) {
  const head = TYPES[n.notificationType] || `🔔 <b>${esc(n.notificationType || "Aviso de Apple")}</b>`;
  const lines = [head + (n.subtype ? ` · ${esc(n.subtype)}` : "")];
  if (tx) {
    const price = Number(tx.price);
    if (Number.isFinite(price) && tx.currency) lines.push(`${(price / 1000).toFixed(2)} ${esc(tx.currency)}`);
    if (tx.productId) lines.push(`<code>${esc(tx.productId)}</code>`);
    if (tx.storefront) lines.push(esc(STOREFRONTS[tx.storefront] || tx.storefront));
  }
  const env = (n.data && n.data.environment) || (tx && tx.environment);
  if (env && env !== "Production") lines.push(`Entorno: ${esc(env)} (no es una compra real)`);
  lines.push(verified ? "firma de Apple verificada ✅" : "⚠️ firma sin verificar");
  return lines.join("\n");
}

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// ---------------------------------------------------------------- JWS

/** Decodes a compact JWS and checks it. Null when it is not even a JWS with a JSON payload. */
export async function openJws(jws) {
  if (typeof jws !== "string") return null;
  const parts = jws.split(".");
  if (parts.length !== 3) return null;
  let header, payload;
  try {
    header = JSON.parse(utf8(b64urlToBytes(parts[0])));
    payload = JSON.parse(utf8(b64urlToBytes(parts[1])));
  } catch {
    return null;
  }
  let verified = false;
  try {
    verified = await verifyJws(header, parts);
  } catch (e) {
    console.log("apple jws check", e && e.message);
  }
  return { header, payload, verified };
}

async function verifyJws(header, parts) {
  if (!header || header.alg !== "ES256" || !Array.isArray(header.x5c) || header.x5c.length < 2) return false;
  const chain = header.x5c.map((c) => b64ToBytes(c));
  // The chain must end in Apple's own root.
  const root = hex(await crypto.subtle.digest("SHA-256", chain[chain.length - 1]));
  if (root !== APPLE_ROOT_G3_SHA256) return false;
  // Each certificate must be signed by the next one.
  const certs = chain.map(parseCert);
  for (let i = 0; i < certs.length - 1; i++) {
    if (!(await certSignedBy(certs[i], certs[i + 1]))) return false;
  }
  // And the JWS itself by the leaf (JWS ES256 signatures are already raw r||s).
  const key = await importSpki(certs[0].spki);
  const data = new TextEncoder().encode(parts[0] + "." + parts[1]);
  return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, b64urlToBytes(parts[2]), data);
}

// ---------------------------------------------------------------- X.509 (just what the chain check needs)

const OID_SHA256_ECDSA = "1.2.840.10045.4.3.2";
const OID_SHA384_ECDSA = "1.2.840.10045.4.3.3";
const OID_P256 = "1.2.840.10045.3.1.7";
const OID_P384 = "1.3.132.0.34";

function tlv(buf, off) {
  const tag = buf[off];
  let len = buf[off + 1];
  let p = off + 2;
  if (len & 0x80) {
    const nBytes = len & 0x7f;
    len = 0;
    for (let i = 0; i < nBytes; i++) len = len * 256 + buf[p + i];
    p += nBytes;
  }
  return { tag, start: off, body: p, end: p + len };
}

function children(buf, node) {
  const out = [];
  let off = node.body;
  while (off < node.end) {
    const c = tlv(buf, off);
    out.push(c);
    off = c.end;
  }
  return out;
}

function oidString(buf, node) {
  const b = buf.subarray(node.body, node.end);
  const parts = [Math.floor(b[0] / 40), b[0] % 40];
  let v = 0;
  for (let i = 1; i < b.length; i++) {
    v = v * 128 + (b[i] & 0x7f);
    if (!(b[i] & 0x80)) { parts.push(v); v = 0; }
  }
  return parts.join(".");
}

function parseCert(der) {
  const cert = tlv(der, 0);
  const [tbs, sigAlg, sigValue] = children(der, cert);
  const tbsFields = children(der, tbs);
  let i = tbsFields[0].tag === 0xa0 ? 1 : 0; // optional [0] version
  // serial, signature algorithm, issuer, validity, subject, subjectPublicKeyInfo
  const spkiNode = tbsFields[i + 5];
  const spki = der.slice(spkiNode.start, spkiNode.end);
  const spkiKids = children(der, spkiNode);
  const curveNode = children(der, spkiKids[0])[1];
  const curve = curveNode ? oidString(der, curveNode) : "";
  const algOid = oidString(der, children(der, sigAlg)[0]);
  // BIT STRING: first content byte = unused bits (0), then the DER ECDSA-Sig-Value
  const sig = der.slice(sigValue.body + 1, sigValue.end);
  return { tbs: der.slice(tbs.start, tbs.end), spki, curve, algOid, sig };
}

async function certSignedBy(cert, issuer) {
  const hash = cert.algOid === OID_SHA384_ECDSA ? "SHA-384" : cert.algOid === OID_SHA256_ECDSA ? "SHA-256" : null;
  if (!hash) return false;
  const key = await importSpki(issuer.spki);
  const size = issuer.curve === OID_P384 ? 48 : 32;
  return await crypto.subtle.verify({ name: "ECDSA", hash }, key, derSigToRaw(cert.sig, size), cert.tbs);
}

async function importSpki(spki) {
  // The curve is inside the SPKI; try the two Apple uses.
  for (const namedCurve of ["P-256", "P-384"]) {
    try {
      return await crypto.subtle.importKey("spki", spki, { name: "ECDSA", namedCurve }, false, ["verify"]);
    } catch { /* wrong curve */ }
  }
  throw new Error("unsupported key");
}

function derSigToRaw(der, size) {
  const seq = tlv(der, 0);
  const [r, s] = children(der, seq);
  const raw = new Uint8Array(size * 2);
  for (const [node, at] of [[r, 0], [s, size]]) {
    let v = der.subarray(node.body, node.end);
    while (v.length > size && v[0] === 0) v = v.subarray(1);
    raw.set(v, at + size - v.length);
  }
  return raw;
}

// ---------------------------------------------------------------- bytes

function b64ToBytes(s) {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToBytes(s) {
  let t = s.replace(/-/g, "+").replace(/_/g, "/");
  while (t.length % 4) t += "=";
  return b64ToBytes(t);
}

function utf8(bytes) {
  return new TextDecoder().decode(bytes);
}

function hex(buf) {
  return [...new Uint8Array(buf)].map((x) => x.toString(16).padStart(2, "0")).join("");
}
