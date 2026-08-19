// Cliente SSE via fetch-streaming: mantem o Bearer no header (sem token na URL),
// reconecta com backoff e entrega os eventos ao chamador. Fallback natural: se o
// stream cair, o polling do sino continua funcionando.
const API_BASE = '/api';

export function connectRealtime(onEvent: (event: string, data: unknown) => void): () => void {
  let stopped = false;
  let controller: AbortController | null = null;
  let attempt = 0;

  async function loop() {
    while (!stopped) {
      const token = localStorage.getItem('torqmind.token');
      if (!token) {
        await sleep(3000);
        continue;
      }
      controller = new AbortController();
      try {
        const res = await fetch(`${API_BASE}/events/stream`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          signal: controller.signal
        });
        if (res.status === 401) {
          stopped = true;
          break;
        }
        if (!res.ok || !res.body) {
          throw new Error(`stream ${res.status}`);
        }
        attempt = 0;
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (!stopped) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let idx: number;
          while ((idx = buffer.indexOf('\n\n')) >= 0) {
            const frame = buffer.slice(0, idx);
            buffer = buffer.slice(idx + 2);
            handleFrame(frame, onEvent);
          }
        }
      } catch {
        // erro de rede ou abort: cai no backoff abaixo
      } finally {
        controller = null;
      }
      if (stopped) break;
      attempt = Math.min(attempt + 1, 6);
      await sleep(Math.min(1000 * 2 ** attempt, 30000));
    }
  }

  loop();

  return () => {
    stopped = true;
    if (controller) controller.abort();
  };
}

function handleFrame(frame: string, onEvent: (event: string, data: unknown) => void) {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith(':')) continue; // comentario/heartbeat
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  }
  if (dataLines.length === 0) return;
  const raw = dataLines.join('\n');
  let data: unknown = raw;
  try {
    data = JSON.parse(raw);
  } catch {
    // mantem string
  }
  onEvent(event, data);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
