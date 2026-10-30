#!/usr/bin/env node
'use strict';
// 음성 팩 제작 도구(의존성 없음). 최종 판정은 앱 검증기다(CLAUDE.md '음성 팩 설계').
//   node tools/pack-build.js tones <폴더>   기술 검증용 톤 팩을 만든다(음성이 아니라 코드로 만든 사인 효과음)
//   node tools/pack-build.js build <폴더>   manifest.json의 files(크기·SHA-256)를 채우고 WAV를 점검해 경고한다
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const RATE = 24000;

/** 모노 PCM16 24kHz WAV. 샘플은 -1..1. */
function wav(samples) {
  const data = Buffer.alloc(samples.length * 2);
  samples.forEach((s, i) => data.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(s * 32767))), i * 2));
  const h = Buffer.alloc(44);
  h.write('RIFF', 0);
  h.writeUInt32LE(36 + data.length, 4);
  h.write('WAVE', 8);
  h.write('fmt ', 12);
  h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20);
  h.writeUInt16LE(1, 22);
  h.writeUInt32LE(RATE, 24);
  h.writeUInt32LE(RATE * 2, 28);
  h.writeUInt16LE(2, 32);
  h.writeUInt16LE(16, 34);
  h.write('data', 36);
  h.writeUInt32LE(data.length, 40);
  return Buffer.concat([h, data]);
}

/** 진폭 0.7(약 -3 dBFS), 앞뒤 20ms 페이드. freq(t)는 t초 시점의 주파수, gate(t)는 0~1 곱. */
function tone(seconds, freq, gate = () => 1) {
  const n = Math.round(seconds * RATE);
  const fade = Math.round(0.02 * RATE);
  const out = new Array(n);
  let phase = 0;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    phase += (2 * Math.PI * freq(t)) / RATE;
    out[i] = 0.7 * Math.min(1, i / fade, (n - 1 - i) / fade) * gate(t) * Math.sin(phase);
  }
  return out;
}

function listFiles(dir, base = dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    return e.isDirectory() ? listFiles(p, base) : [path.relative(base, p).split(path.sep).join('/')];
  });
}

const sha = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

/** WAV 헤더·길이·피크를 보고 경고 목록을 돌려준다. */
function checkWav(name, b, m) {
  if (b.length < 44 || b.toString('ascii', 0, 4) !== 'RIFF' || b.toString('ascii', 8, 12) !== 'WAVE') return [`${name}: WAV가 아니에요`];
  let pos = 12;
  let fmt = null;
  let data = null;
  while (pos + 8 <= b.length) {
    const id = b.toString('ascii', pos, pos + 4);
    const size = b.readUInt32LE(pos + 4);
    const body = pos + 8;
    if (id === 'fmt ') fmt = { format: b.readUInt16LE(body), ch: b.readUInt16LE(body + 2), rate: b.readUInt32LE(body + 4), bits: b.readUInt16LE(body + 14) };
    else if (id === 'data') { data = b.subarray(body, body + size); break; }
    pos = body + size + (size & 1);
  }
  if (!fmt || !data) return [`${name}: fmt/data 덩어리를 못 찾았어요`];
  const w = [];
  if (fmt.format !== 1 || fmt.bits !== 16) w.push(`${name}: PCM 16비트가 아니에요`);
  if (m.audio && (fmt.ch !== m.audio.channels || fmt.rate !== m.audio.sampleRate)) w.push(`${name}: WAV 헤더(${fmt.rate}Hz ${fmt.ch}채널)가 manifest의 audio와 달라요`);
  const seconds = data.length / (2 * fmt.ch) / fmt.rate;
  if (seconds > 4) w.push(`${name}: 4초를 넘어요(${seconds.toFixed(1)}초)`);
  let peak = 0;
  for (let i = 0; i + 1 < data.length; i += 2) peak = Math.max(peak, Math.abs(data.readInt16LE(i)));
  const db = 20 * Math.log10(peak / 32768 || 1e-9);
  if (db > -1) w.push(`${name}: 피크 ${db.toFixed(1)} dBFS — 클리핑 위험(-3 dBFS 권장)`);
  if (db < -30) w.push(`${name}: 피크 ${db.toFixed(1)} dBFS — 너무 작아요`);
  return w;
}

function build(dir) {
  const mf = path.join(dir, 'manifest.json');
  const m = JSON.parse(fs.readFileSync(mf, 'utf8'));
  const files = listFiles(dir).filter((f) => f !== 'manifest.json').sort();
  m.files = files.map((f) => {
    const b = fs.readFileSync(path.join(dir, f));
    return { path: f, size: b.length, sha256: sha(b) };
  });
  fs.writeFileSync(mf, JSON.stringify(m, null, 2) + '\n');
  console.log(`${m.id}: files ${files.length}개를 채웠어요.`);
  files.filter((f) => f.endsWith('.wav')).flatMap((f) => checkWav(f, fs.readFileSync(path.join(dir, f)), m)).forEach((x) => console.warn('경고: ' + x));
}

/** 기술 검증용 톤 팩. MODE를 일부러 뺐다(기본 음성 대체를 시험하려고). TIRE는 2.5초라 재생 중 멈춤을 시험하기 좋다. */
function tones(dir) {
  fs.mkdirSync(path.join(dir, 'clips'), { recursive: true });
  const clips = {
    BRAKE: { file: 'clips/brake.wav', text: '(톤) 브레이크', samples: tone(0.35, (t) => 660 - (330 * t) / 0.35) },
    TURN: { file: 'clips/turn.wav', text: '(톤) 방향지시등', samples: tone(0.3, () => 880, (t) => (t < 0.12 || (t >= 0.18 && t < 0.3) ? 1 : 0)) },
    TIRE: { file: 'clips/tire.wav', text: '(톤) 타이어', samples: tone(2.5, (t) => 440 - (220 * t) / 2.5) },
  };
  for (const c of Object.values(clips)) fs.writeFileSync(path.join(dir, c.file), wav(c.samples));
  const manifest = {
    schema: 1,
    id: 'dev.tones',
    name: '개발용 톤',
    version: '1.0.0',
    kind: 'clip',
    language: 'ko-KR',
    audio: { format: 'wav-pcm16', sampleRate: RATE, channels: 1 },
    clips: Object.fromEntries(Object.entries(clips).map(([k, c]) => [k, { file: c.file, text: c.text }])),
    files: [],
    provenance: { character: 'original', voiceSource: 'synthetic', consent: true, license: 'CC0-1.0', commercialUse: true, attribution: '기술 검증용으로 코드가 만든 사인파 효과음(음성 아님)' },
  };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  build(dir);
}

const [cmd, dir] = process.argv.slice(2);
if (cmd === 'tones' && dir) tones(dir);
else if (cmd === 'build' && dir) build(dir);
else {
  console.error('사용법:\n  node tools/pack-build.js tones <폴더>   기술 검증용 톤 팩을 만든다(음성이 아니라 사인 효과음)\n  node tools/pack-build.js build <폴더>   manifest.json의 files(크기·SHA-256)를 채우고 WAV를 점검한다');
  process.exit(2);
}
