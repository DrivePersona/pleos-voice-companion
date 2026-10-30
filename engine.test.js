// 반응 엔진 규칙 테스트. 명세는 기획서 v0.2 §5(이벤트 매핑)·§6(음성 UI·안전 원칙). 실행: node --test
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createEngine } = require('./engine.js');

// 가짜 플랫폼: 호출을 전부 기록하고, 시계와 오디오 포커스를 손으로 돌린다.
function setup(opts = {}) {
  const calls = [];
  let t = 1_000_000;
  let focus = true;
  const platform = {
    speak: (text, o) => calls.push({ type: 'speak', text, voice: o.voice, volume: o.volume }),
    stop: () => calls.push({ type: 'stop' }),
    requestFocus: () => focus,
    showAvatar: (face, text) => calls.push({ type: 'avatar', face, text }),
    hideAvatar: () => calls.push({ type: 'hide' }),
    log: (e) => calls.push({ type: 'log', ...e }),
  };
  const engine = createEngine(platform, { now: () => t, ...opts });
  return {
    engine,
    calls,
    tick: (ms) => { t += ms; },
    setFocus: (v) => { focus = v; },
    fire: (...names) => { names.forEach((n) => engine.emit(n)); engine.flush(); },
  };
}
const of = (calls, type) => calls.filter((c) => c.type === type);
const spoken = (calls) => of(calls, 'speak').map((c) => c.text);
const drops = (calls) => of(calls, 'log').filter((c) => c.result === 'drop').map((c) => `${c.name}:${c.why}`);

test('정차 중 브레이크: 대사를 말하고 아바타를 보여 준다 (음량은 상한 이하)', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(false);
  fire('brake');
  const s = of(calls, 'speak')[0];
  assert.equal(s.text, '으앗!');
  assert.equal(s.voice, 'character');
  assert.ok(s.volume > 0 && s.volume <= 0.8);
  assert.deepEqual(of(calls, 'avatar').map((c) => c.text), ['으앗!']);
});

test('같은 이벤트는 쿨다운 안에서 한 번만, 지나면 다시 말한다', () => {
  const { engine, calls, fire, tick } = setup();
  engine.setDriving(false);
  fire('brake');
  tick(5000);
  fire('brake');
  assert.deepEqual(spoken(calls), ['으앗!']);
  assert.deepEqual(drops(calls), ['brake:cooldown']);
  tick(5001);
  fire('brake');
  assert.deepEqual(spoken(calls), ['으앗!', '으앗!']);
});

test('같은 순간 여러 이벤트: 안전 > 상태 > 연출 순으로 하나만 말하고 나머지는 버린다', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(false);
  fire('brake', 'turn', 'warning');
  assert.deepEqual(spoken(calls), ['차량 경고가 있어요.']);
  assert.deepEqual(drops(calls).sort(), ['brake:priority', 'turn:priority']);
});

test('상태 보고(방향지시등)가 연출(브레이크)보다 먼저다', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(false);
  fire('brake', 'turn');
  assert.deepEqual(spoken(calls), ['깜빡이 켰어~']);
});

test('쿨다운 중인 이벤트는 우선순위 경쟁에서 빠진다 (쿨다운이 먼저)', () => {
  const { engine, calls, fire, tick } = setup();
  engine.setDriving(false);
  fire('warning');
  tick(1000);
  fire('warning', 'brake');
  assert.deepEqual(spoken(calls), ['차량 경고가 있어요.', '으앗!']);
});

test('안전 이벤트는 연출 없이 중립 음성으로 말하고 아바타도 반응하지 않는다', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(false);
  fire('warning');
  assert.equal(of(calls, 'speak')[0].voice, 'neutral');
  assert.equal(of(calls, 'avatar').length, 0);
});

test('오디오 포커스가 없으면 말하지 않고, 쿨다운도 쓰지 않는다', () => {
  const { engine, calls, fire, setFocus } = setup();
  engine.setDriving(false);
  setFocus(false);
  fire('brake');
  assert.deepEqual(spoken(calls), []);
  assert.deepEqual(drops(calls), ['brake:focus']);
  setFocus(true);
  fire('brake');
  assert.deepEqual(spoken(calls), ['으앗!']);
});

test('포커스를 잃으면 재생을 바로 멈춘다', () => {
  const { engine, calls } = setup();
  engine.focusLost();
  assert.equal(of(calls, 'stop').length, 1);
});

test('주행 중에는 아바타 없이 목소리만 낸다', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(true);
  fire('brake');
  assert.deepEqual(spoken(calls), ['으앗!']);
  assert.equal(of(calls, 'avatar').length, 0);
});

test('주행 중 음성이 불허되면 정차 중에만 반응한다', () => {
  const { engine, calls, fire } = setup();
  engine.setVoiceWhileDriving(false);
  engine.setDriving(true);
  fire('brake');
  assert.deepEqual(spoken(calls), []);
  assert.deepEqual(drops(calls), ['brake:driving-silent']);
  engine.setDriving(false);
  fire('brake');
  assert.deepEqual(spoken(calls), ['으앗!']);
});

test('음성 불허 상태에서 주행이 시작되면 말하던 소리도 멈춘다', () => {
  const { engine, calls } = setup();
  engine.setDriving(false);
  engine.setVoiceWhileDriving(false);
  assert.equal(of(calls, 'stop').length, 0);
  engine.setDriving(true);
  assert.equal(of(calls, 'stop').length, 1);
});

test('상태를 아직 모르는 기동 직후는 주행으로 보고 아바타를 닫아 둔다', () => {
  const { calls, fire } = setup(); // setDriving 호출 없음
  fire('brake');
  assert.equal(of(calls, 'avatar').length, 0);
});

test('주행이 시작되면 아바타를 바로 숨긴다', () => {
  const { engine, calls } = setup();
  engine.setDriving(false);
  engine.setDriving(true);
  assert.equal(of(calls, 'hide').length, 1);
});

test('대사를 바꾸면 다음 반응부터 바뀐 대사로 말하고, 다른 엔진에는 새지 않는다', () => {
  const a = setup();
  const b = setup();
  a.engine.setDriving(false);
  b.engine.setDriving(false);
  a.engine.setLine('brake', '앗 차가워!');
  a.fire('brake');
  b.fire('brake');
  assert.deepEqual(spoken(a.calls), ['앗 차가워!']);
  assert.deepEqual(spoken(b.calls), ['으앗!']);
});

test('대사를 비우면 기본 대사로 돌아간다 (직전에 바꾼 대사로 남지 않는다)', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(false);
  engine.setLine('brake', '앗 차가워!');
  engine.setLine('brake', '  ');
  fire('brake');
  assert.deepEqual(spoken(calls), ['으앗!']);
});

test('주행 중에 음성이 불허로 바뀌면 말하던 소리를 바로 멈춘다', () => {
  const { engine, calls } = setup();
  engine.setDriving(true);
  engine.setVoiceWhileDriving(false);
  assert.equal(of(calls, 'stop').length, 1);
});

test('같은 이벤트가 한 창 안에서 여러 번 와도 한 번만 처리한다 (자기 자신에게 밀려 버려지지 않는다)', () => {
  const { engine, calls, fire } = setup();
  engine.setDriving(false);
  fire('brake', 'brake');
  assert.deepEqual(spoken(calls), ['으앗!']);
  assert.deepEqual(drops(calls), []);
});

test('알 수 없는 이벤트는 삼키지 않고 기록한다', () => {
  const { engine, calls } = setup();
  engine.emit('bogus');
  engine.flush();
  assert.deepEqual(drops(calls), ['bogus:unknown-event']);
});

test('emit만 호출해도 타이머가 창 안의 이벤트를 모아 한 번에 처리한다', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { engine, calls } = setup();
  engine.setDriving(false);
  ['brake', 'turn', 'warning'].forEach((n) => engine.emit(n));
  assert.deepEqual(spoken(calls), []); // 창이 닫히기 전엔 말하지 않는다
  t.mock.timers.tick(300);
  assert.deepEqual(spoken(calls), ['차량 경고가 있어요.']);
});
