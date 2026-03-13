function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function isRetryableStatus(status) {
    // 재시도 대상: 408/429/5xx
    return status === 408 || status === 429 || (status >= 500 && status <= 599);
}

function parseRetryAfterMs(res) {
    const v = res.headers?.get?.('Retry-After');
    if (!v) return null;

    // Retry-After: seconds 또는 HTTP date
    const seconds = Number(v);
    if (!Number.isNaN(seconds)) return Math.max(0, seconds * 1000);

    const dateMs = Date.parse(v);
    if (!Number.isNaN(dateMs)) return Math.max(0, dateMs - Date.now());

    return null;
}

function jitter(ms) {
    // 0.8 ~ 1.2 배 랜덤
    const r = 0.8 + Math.random() * 0.4;
    return Math.floor(ms * r);
}

export async function fetchWithRetry(url, options = {}) {
    const {
        retries = 3,
        retryDelayMs = 400,     // 기본 base delay
        maxDelayMs = 4000,
        timeoutMs = 8000,
        retryOn = (resOrErr) => {
            // network error면 retry
            if (resOrErr instanceof Error) return true;
            // 응답이면 상태코드로 판단
            return isRetryableStatus(resOrErr.status);
        },
        onRetry, // (info) => void
        ...fetchOptions
    } = options;

    let attempt = 0;
    let lastError = null;

    while (attempt <= retries) {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

        try {
            const res = await fetch(url, {
                ...fetchOptions,
                signal: fetchOptions.signal ?? controller.signal,
            });

            clearTimeout(timeoutId);

            // 정상 응답(2xx~3xx)
            if (res.ok) return res;

            // 재시도 불가면 그대로 반환 (404/400/401 등)
            if (!retryOn(res)) return res;

            // 재시도 가능
            const retryAfter = parseRetryAfterMs(res);
            const backoff = Math.min(maxDelayMs, retryDelayMs * Math.pow(2, attempt));
            const wait = jitter(retryAfter ?? backoff);

            if (attempt === retries) return res;

            onRetry?.({
                attempt: attempt + 1,
                retries,
                waitMs: wait,
                reason: `HTTP ${res.status}`,
                url,
            });

            await sleep(wait);
        } catch (err) {
            clearTimeout(timeoutId);
            lastError = err;

            // abort는 보통 즉시 실패 처리(원하면 retryOn에서 바꿀 수 있음)
            if (err?.name === 'AbortError') {
                if (attempt === retries) throw err;
            }

            if (!retryOn(err)) throw err;
            if (attempt === retries) throw err;

            const backoff = Math.min(maxDelayMs, retryDelayMs * Math.pow(2, attempt));
            const wait = jitter(backoff);

            onRetry?.({
                attempt: attempt + 1,
                retries,
                waitMs: wait,
                reason: err?.message || 'network error',
                url,
            });

            await sleep(wait);
        }

        attempt += 1;
    }

    // 여기 도달하는 경우는 거의 없음
    if (lastError) throw lastError;
    throw new Error('fetchWithRetry failed unexpectedly');
}
