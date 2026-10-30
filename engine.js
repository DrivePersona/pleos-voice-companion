// 보이스 컴패니언 반응 엔진: 차량 이벤트 in → 음성·아바타 out.
// DOM도 Pleos SDK도 모른다. 바깥 세계는 platform 객체로 받는다
// (시뮬레이터는 index.html, 실차는 Pleos 어댑터가 같은 모양으로 채운다).
(function (root) {
  // 같은 순간에 여러 이벤트가 오면 숫자가 작은 쪽 하나만 말한다. 기획서 §6: 안전 > 상태 보고 > 연출.
  const RANK = { safety: 0, status: 1, flourish: 2 };

  // 기획서 v0.2 §5 예시 대사 그대로. neutral = 연출 없이 중립 음성(§6 "안전 이벤트는 연출하지 않습니다").
  // ponytail: cooldownMs는 기획서에서 N 미정 — 임시값. 에뮬레이터 실측 뒤 조정.
  const EVENTS = {
    brake:   { label: '브레이크 밟기',        rank: 'flourish', cooldownMs: 10000, face: '😱', line: '으앗!' },
    turn:    { label: '방향지시등 켜기',      rank: 'status',   cooldownMs: 8000,  face: '😉', line: '깜빡이 켰어~' },
    mode:    { label: '드라이브 모드 바꾸기', rank: 'flourish', cooldownMs: 3000,  face: '😎', line: '모드 체인지!' },
    tire:    { label: '타이어 공기압 낮음',   rank: 'status',   cooldownMs: 60000, face: '🥺', line: '발이 좀 아파…' },
    warning: { label: '차량 경고',            rank: 'safety',   cooldownMs: 10000, face: '😐', line: '차량 경고가 있어요.', neutral: true },
  };

  function createEngine(platform, opts = {}) {
    const defaults = opts.events || EVENTS; // 손대지 않은 원본. 대사를 비웠을 때 돌아갈 곳
    const events = structuredClone(defaults); // 기본값을 엔진끼리 공유하지 않는다
    const now = opts.now || Date.now;
    const windowMs = opts.windowMs ?? 300; // 이 시간 안에 모인 이벤트끼리 경쟁한다
    const maxVolume = opts.maxVolume ?? 0.8; // 음량 상한(§6)
    // 상태를 아직 못 읽은 기동 직후는 주행으로 본다. 닫힌 쪽이 안전(§6 정차 게이트).
    const s = { driving: true, voiceWhileDriving: true, last: {}, pending: [], timer: null };
    const log = (e) => platform.log && platform.log(e);
    const drop = (name, why, extra) => log({ name, result: 'drop', why, ...extra });

    function emit(name) {
      if (!events[name]) return drop(name, 'unknown-event'); // 조용히 삼키지 않는다
      s.pending.push(name);
      if (!s.timer) s.timer = setTimeout(flush, windowMs);
    }

    function flush() {
      clearTimeout(s.timer);
      s.timer = null;
      const batch = [...new Set(s.pending)];
      s.pending = [];
      const t = now();
      // 쿨다운을 먼저 거른다. 쿨다운 중인 이벤트가 우선순위 경쟁에서 다른 이벤트를 막으면 안 된다.
      const ready = batch.filter((n) => {
        const wait = (s.last[n] ?? -Infinity) + events[n].cooldownMs - t;
        if (wait > 0) drop(n, 'cooldown', { waitMs: wait });
        return wait <= 0;
      });
      ready.sort((a, b) => RANK[events[a].rank] - RANK[events[b].rank]); // 동률이면 먼저 온 쪽
      const [win, ...lost] = ready;
      // 밀려난 이벤트는 버린다. 늦게 말하면 의미가 없다(§6 반응 큐).
      lost.forEach((n) => drop(n, 'priority', { by: win }));
      if (win) react(win, t);
    }

    function react(name, t) {
      const ev = events[name];
      if (s.driving && !s.voiceWhileDriving) return drop(name, 'driving-silent');
      if (!platform.requestFocus()) return drop(name, 'focus');
      s.last[name] = t; // 실제로 말할 때만 쿨다운을 쓴다
      // 여기서 아는 건 '요청함'까지. 실제로 재생됐는지는 플랫폼이 따로 알려 준다.
      log({ name, result: 'speak', text: ev.line });
      platform.speak(ev.line, { name, voice: ev.neutral ? 'neutral' : 'character', volume: maxVolume });
      if (!s.driving && !ev.neutral) platform.showAvatar(ev.face, ev.line);
    }

    return {
      events,
      emit,
      flush,
      setDriving(v) {
        s.driving = v;
        if (!v) return;
        platform.hideAvatar(); // 주행이 시작되면 연출을 멈추고 UI를 숨긴다(§3)
        if (!s.voiceWhileDriving) platform.stop();
      },
      setVoiceWhileDriving(v) {
        s.voiceWhileDriving = v;
        if (s.driving && !v) platform.stop();
      },
      focusLost: () => platform.stop(), // 내비·통화·긴급 안내에 바로 양보(§6 오디오 포커스)
      setLine(name, text) {
        // 비우면 기본 대사로 돌아간다. 말없는 이벤트가 생기지 않게.
        if (events[name]) events[name].line = String(text).trim() || defaults[name].line;
      },
    };
  }

  const api = { createEngine, EVENTS };
  if (typeof module !== 'undefined') module.exports = api;
  else root.Engine = api;
})(typeof window !== 'undefined' ? window : globalThis);
