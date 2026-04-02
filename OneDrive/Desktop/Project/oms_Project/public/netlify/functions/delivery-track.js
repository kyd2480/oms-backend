/**
 * Netlify Serverless Function — 우체국 배송조회 프록시
 *
 * 경로: netlify/functions/delivery-track.js
 * 호출: GET /.netlify/functions/delivery-track?trackingNo=xxx&carrierCode=POST
 *
 * Netlify 환경변수 설정 필요:
 *   POST_OFFICE_API_KEY = fa54da790c9a4f4c291d48ee1f430d2de79b7942bbad6cde726902dc6659a25e
 */

const https = require('https');

const POST_COMBINED_URL =
  'https://openapi.epost.go.kr/trace/retrieveLongitudinalCombinedService' +
  '/retrieveLongitudinalCombinedService/getLongitudinalCombinedList';

/** HTTP GET 요청 */
function httpGet(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { timeout: 10000 }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve(data));
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Connect timed out')); });
  });
}

/** XML 태그 값 추출 */
function tag(xml, name) {
  const m = xml.match(new RegExp(`<${name}[^>]*>([\\s\\S]*?)<\/${name}>`, 'i'));
  return m ? m[1].trim() : '';
}

/** 반복 태그 배열 추출 */
function tags(xml, name) {
  const re = new RegExp(`<${name}[^>]*>([\\s\\S]*?)<\/${name}>`, 'gi');
  const results = [];
  let m;
  while ((m = re.exec(xml)) !== null) results.push(m[1]);
  return results;
}

exports.handler = async (event) => {
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Content-Type': 'application/json; charset=utf-8',
  };

  // CORS preflight
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 200, headers, body: '' };
  }

  const { trackingNo, carrierCode = 'POST' } = event.queryStringParameters || {};

  if (!trackingNo) {
    return {
      statusCode: 400,
      headers,
      body: JSON.stringify({ success: false, message: '송장번호를 입력해주세요.' }),
    };
  }

  if (carrierCode !== 'POST') {
    return {
      statusCode: 200,
      headers,
      body: JSON.stringify({
        success: false,
        message: `${carrierCode} 택배사는 현재 지원되지 않습니다. (우체국만 지원)`,
        trackingNo, carrierCode,
      }),
    };
  }

  const apiKey = process.env.POST_OFFICE_API_KEY;
  if (!apiKey) {
    return {
      statusCode: 500,
      headers,
      body: JSON.stringify({ success: false, message: 'API 키가 설정되지 않았습니다.' }),
    };
  }

  try {
    const url = `${POST_COMBINED_URL}?serviceKey=${encodeURIComponent(apiKey)}&rgist=${encodeURIComponent(trackingNo)}`;
    const xml = await httpGet(url);

    // 에러 코드 확인
    const errCode = tag(xml, 'returnReasonCode');
    if (errCode && errCode !== '00') {
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          success: false,
          message: `우체국 API 오류 (${errCode}): ${tag(xml, 'returnAuthMsg')}`,
          trackingNo, carrierCode: 'POST', carrierName: '우체국택배',
        }),
      };
    }

    // 기본 정보 추출
    const result = {
      success:       true,
      trackingNo,
      carrierCode:   'POST',
      carrierName:   '우체국택배',
      sender:        tag(xml, 'sndr'),
      receiver:      tag(xml, 'rcvr'),
      sentDate:      tag(xml, 'sndrDt'),
      deliveryDate:  tag(xml, 'rcvDt'),
      currentStatus: tag(xml, 'dlvSt'),
      steps:         [],
      message:       '',
    };

    // 종적 목록 파싱
    const items = tags(xml, 'longitudinalDomesticList');
    result.steps = items.map(item => ({
      dateTime: (tag(item, 'chgDt') + ' ' + tag(item, 'chgTm')).trim(),
      location: tag(item, 'nowLc'),
      status:   tag(item, 'crgSt'),
      detail:   tag(item, 'detailDsc'),
    }));

    if (result.steps.length === 0) {
      result.message = '조회된 배송 정보가 없습니다. 송장번호를 확인해주세요.';
    }

    return { statusCode: 200, headers, body: JSON.stringify(result) };

  } catch (e) {
    return {
      statusCode: 200,
      headers,
      body: JSON.stringify({
        success: false,
        message: '배송조회 중 오류가 발생했습니다: ' + e.message,
        trackingNo, carrierCode: 'POST', carrierName: '우체국택배',
      }),
    };
  }
};
