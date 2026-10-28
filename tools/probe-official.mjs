import { mkdir, writeFile } from 'node:fs/promises';

// Public, credential-free GET probes only; no barcode or identity API is called.
// Gateway errors establish connectivity only, never feature availability.
const targets = [
  ['taobao_open', 'documentation', 'https://open.taobao.com/'],
  ['taobao_oauth_docs', 'documentation', 'https://developer.alibaba.com/docs/doc.htm?articleId=102635&docType=1&treeId=796'],
  ['cainiao_pickup_service', 'product_description', 'https://tech.cainiao.com/productv2/productIndex/6'],
  ['cainiao_api_docs', 'documentation_portal', 'https://global.link.cainiao.com/'],
  ['pinduoduo_open', 'documentation_portal', 'https://open.pinduoduo.com/'],
  ['pinduoduo_api_docs', 'documentation_portal', 'https://open.pinduoduo.com/application/document/api'],
  ['taobao_gateway', 'gateway_connectivity_only', 'https://eco.taobao.com/router/rest?format=json'],
  ['pinduoduo_gateway', 'gateway_connectivity_only', 'https://gw-api.pinduoduo.com/api/router'],
];
const clean = html => html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
  .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
  .replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();

async function probe([id, kind, url]) {
  const started = Date.now();
  try {
    const response = await fetch(url, {
      signal: AbortSignal.timeout(15000), redirect: 'error',
      headers: { 'User-Agent': 'handyCode-feasibility/0.1', Accept: 'text/html,application/json,application/xml;q=0.9' },
    });
    const reader = response.body?.getReader();
    const chunks = [];
    let bytes = 0;
    let truncated = false;
    try {
      while (reader) {
        const { done, value } = await reader.read();
        if (done) break;
        const remaining = 1024 * 1024 - bytes;
        chunks.push(value.subarray(0, remaining));
        bytes += Math.min(remaining, value.length);
        if (bytes === 1024 * 1024) { truncated = true; break; }
      }
    } finally { if (reader) await reader.cancel().catch(() => {}); }
    const body = Buffer.concat(chunks).toString('utf8');
    const visible = clean(body);
    const excerpts = ['身份码', '取件码', 'app_key', 'access_token', 'client_id'].flatMap(keyword => {
      const index = visible.indexOf(keyword);
      return index < 0 ? [] : [{ keyword, text: visible.slice(Math.max(0, index - 40), index + 120) }];
    });
    let gatewayError;
    if (kind === 'gateway_connectivity_only') {
      try {
        const error = JSON.parse(body).error_response;
        if (error) gatewayError = Object.fromEntries(
          ['code', 'msg', 'sub_code', 'sub_msg', 'error_code', 'error_msg']
            .filter(key => key in error).map(key => [key, String(error[key]).slice(0, 250)]));
      } catch { gatewayError = { parseStatus: 'not_json' }; }
    }
    return { id, kind, url, outcome: 'http_response', status: response.status,
      title: clean(body.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] ?? ''),
      contentType: response.headers.get('content-type'), excerpts, gatewayError, truncated,
      elapsedMs: Date.now() - started };
  } catch (error) {
    return { id, kind, url, outcome: 'request_failed', error: error.name,
      message: error.message, causeCode: error.cause?.code, elapsedMs: Date.now() - started };
  }
}
const observations = await Promise.all(targets.map(probe));
const report = { checkedAt: new Date().toISOString(), credentialsSent: false,
  personalIdentityApiCalled: false, personalIdentityCodeRetrieved: false,
  scope: 'Public documents and credential-free gateway connectivity, not API integration.',
  observations };
await mkdir(new URL('../docs/', import.meta.url), { recursive: true });
await writeFile(new URL('../docs/public-access-check.json', import.meta.url),
  JSON.stringify(report, null, 2) + '\n', 'utf8');
for (const item of observations) {
  console.log(item.id + ': ' + (item.status ? 'HTTP ' + item.status : item.error + ' ' + (item.causeCode ?? item.message)));
  if (item.gatewayError) console.log(JSON.stringify(item.gatewayError));
}
