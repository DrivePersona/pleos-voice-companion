/*
 * Design-review character pack generator.
 *
 * This has no runtime dependency: it emits a valid, self-contained GLB made
 * from low-poly primitives so the first design can be viewed in any glTF 2.0
 * viewer before the Android renderer is chosen.
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const outputDir = path.join(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'debug',
  'assets',
  'characterpacks',
  'dev.mori',
);
const outputFile = path.join(outputDir, 'avatar.glb');

const gltf = {
  asset: {
    version: '2.0',
    generator: 'Pleos Voice Companion design-review generator',
    copyright: 'Original work for Pleos Voice Companion; design review only.',
  },
  scene: 0,
  scenes: [{ name: 'Mori presentation', nodes: [0] }],
  nodes: [],
  meshes: [],
  materials: [],
  accessors: [],
  bufferViews: [],
  animations: [],
  buffers: [{ byteLength: 0 }],
  extras: {
    characterId: 'dev.mori',
    presentation: 'stationary-only',
    note: 'Debug character-pack sample loaded by the Android app while stationary.',
  },
};
const binaryParts = [];
let binaryLength = 0;

function pad4() {
  const padding = (4 - (binaryLength % 4)) % 4;
  if (padding) {
    binaryParts.push(Buffer.alloc(padding));
    binaryLength += padding;
  }
}

function addBufferView(buffer, target) {
  pad4();
  const byteOffset = binaryLength;
  binaryParts.push(buffer);
  binaryLength += buffer.length;
  const view = { buffer: 0, byteOffset, byteLength: buffer.length };
  if (target) view.target = target;
  gltf.bufferViews.push(view);
  return gltf.bufferViews.length - 1;
}

function minMax(values, stride) {
  const min = Array(stride).fill(Infinity);
  const max = Array(stride).fill(-Infinity);
  for (let index = 0; index < values.length; index += stride) {
    for (let component = 0; component < stride; component += 1) {
      min[component] = Math.min(min[component], values[index + component]);
      max[component] = Math.max(max[component], values[index + component]);
    }
  }
  return { min, max };
}

function addFloatAccessor(values, type, includeMinMax = false, target = 34962) {
  const stride = { SCALAR: 1, VEC3: 3, VEC4: 4 }[type];
  const typed = new Float32Array(values);
  const accessor = {
    bufferView: addBufferView(Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength), target),
    componentType: 5126,
    count: typed.length / stride,
    type,
  };
  if (includeMinMax) Object.assign(accessor, minMax(values, stride));
  gltf.accessors.push(accessor);
  return gltf.accessors.length - 1;
}

function addIndexAccessor(values) {
  const typed = new Uint16Array(values);
  gltf.accessors.push({
    bufferView: addBufferView(Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength), 34963),
    componentType: 5123,
    count: typed.length,
    type: 'SCALAR',
  });
  return gltf.accessors.length - 1;
}

function addMaterial(name, color, roughness = 0.78) {
  gltf.materials.push({
    name,
    pbrMetallicRoughness: {
      baseColorFactor: [...color, 1],
      metallicFactor: 0,
      roughnessFactor: roughness,
    },
  });
  return gltf.materials.length - 1;
}

function addMesh(name, geometry, material) {
  const position = addFloatAccessor(geometry.positions, 'VEC3', true);
  const normal = addFloatAccessor(geometry.normals, 'VEC3');
  const indices = addIndexAccessor(geometry.indices);
  gltf.meshes.push({
    name,
    primitives: [{ attributes: { POSITION: position, NORMAL: normal }, indices, material }],
  });
  return gltf.meshes.length - 1;
}

function sphere(segments = 16, rings = 10) {
  const positions = [];
  const normals = [];
  const indices = [];
  for (let ring = 0; ring <= rings; ring += 1) {
    const v = ring / rings;
    const theta = v * Math.PI;
    const sinTheta = Math.sin(theta);
    const y = Math.cos(theta);
    for (let segment = 0; segment <= segments; segment += 1) {
      const u = segment / segments;
      const phi = u * Math.PI * 2;
      const x = Math.cos(phi) * sinTheta;
      const z = Math.sin(phi) * sinTheta;
      positions.push(x, y, z);
      normals.push(x, y, z);
    }
  }
  for (let ring = 0; ring < rings; ring += 1) {
    for (let segment = 0; segment < segments; segment += 1) {
      const a = ring * (segments + 1) + segment;
      const b = a + segments + 1;
      indices.push(a, b, a + 1, b, b + 1, a + 1);
    }
  }
  return { positions, normals, indices };
}

function cube() {
  const faces = [
    [[0, 0, 1], [-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]],
    [[0, 0, -1], [1, -1, -1], [-1, -1, -1], [-1, 1, -1], [1, 1, -1]],
    [[1, 0, 0], [1, -1, 1], [1, -1, -1], [1, 1, -1], [1, 1, 1]],
    [[-1, 0, 0], [-1, -1, -1], [-1, -1, 1], [-1, 1, 1], [-1, 1, -1]],
    [[0, 1, 0], [-1, 1, 1], [1, 1, 1], [1, 1, -1], [-1, 1, -1]],
    [[0, -1, 0], [-1, -1, -1], [1, -1, -1], [1, -1, 1], [-1, -1, 1]],
  ];
  const positions = [];
  const normals = [];
  const indices = [];
  for (const [normal, ...vertices] of faces) {
    const start = positions.length / 3;
    for (const vertex of vertices) {
      positions.push(...vertex);
      normals.push(...normal);
    }
    indices.push(start, start + 1, start + 2, start, start + 2, start + 3);
  }
  return { positions, normals, indices };
}

function quatZ(degrees) {
  const radians = (degrees * Math.PI) / 180;
  return [0, 0, Math.sin(radians / 2), Math.cos(radians / 2)];
}

function addNode(name, mesh, options = {}) {
  const node = { name };
  if (mesh !== null && mesh !== undefined) node.mesh = mesh;
  if (options.translation) node.translation = options.translation;
  if (options.rotation) node.rotation = options.rotation;
  if (options.scale) node.scale = options.scale;
  if (options.children) node.children = options.children;
  gltf.nodes.push(node);
  return gltf.nodes.length - 1;
}

function animation(name, channels) {
  const samplers = [];
  const outputChannels = [];
  for (const channel of channels) {
    const input = addFloatAccessor(channel.times, 'SCALAR', true, null);
    const output = addFloatAccessor(channel.values, channel.type, false, null);
    samplers.push({ input, output, interpolation: 'LINEAR' });
    outputChannels.push({ sampler: samplers.length - 1, target: { node: channel.node, path: channel.path } });
  }
  gltf.animations.push({ name, samplers, channels: outputChannels });
}

const materials = {
  lavender: addMaterial('Mori lavender', [0.49, 0.39, 0.83]),
  cream: addMaterial('Warm cream face', [1.0, 0.92, 0.76]),
  ink: addMaterial('Soft ink features', [0.12, 0.10, 0.20], 0.66),
  blush: addMaterial('Coral blush', [0.95, 0.46, 0.50]),
  teal: addMaterial('Signal teal', [0.22, 0.73, 0.70]),
  navy: addMaterial('Night-road navy', [0.10, 0.16, 0.32]),
  white: addMaterial('Eye sparkle', [1.0, 1.0, 1.0], 0.45),
  gold: addMaterial('Warm signal gold', [1.0, 0.72, 0.24]),
};
const meshes = {
  round: addMesh('Low-poly rounded primitive', sphere(16, 10), materials.lavender),
  face: addMesh('Face panel', sphere(16, 10), materials.cream),
  ink: addMesh('Soft ink detail', sphere(12, 8), materials.ink),
  blush: addMesh('Cheek detail', sphere(12, 8), materials.blush),
  teal: addMesh('Signal tab', sphere(12, 8), materials.teal),
  navy: addMesh('Road sash', cube(), materials.navy),
  white: addMesh('Eye sparkle', sphere(10, 7), materials.white),
  gold: addMesh('Signal orb', sphere(12, 8), materials.gold),
};

const root = addNode('Mori.Root', null, { translation: [0, 0, 0] });
const body = addNode('Mori.Body', meshes.round, { translation: [0, 0.02, 0], scale: [0.86, 1.04, 0.63] });
const face = addNode('Mori.Face', meshes.face, { translation: [0, 0.13, 0.56], scale: [0.69, 0.76, 0.14] });
const leftTab = addNode('Mori.SignalTab.Left', meshes.teal, { translation: [-0.74, 0.57, 0], rotation: quatZ(-28), scale: [0.21, 0.43, 0.19] });
const rightTab = addNode('Mori.SignalTab.Right', meshes.teal, { translation: [0.71, 0.38, 0], rotation: quatZ(34), scale: [0.18, 0.34, 0.17] });
const antenna = addNode('Mori.SignalOrb', meshes.gold, { translation: [-0.18, 1.05, 0.06], scale: [0.13, 0.13, 0.13] });
const sash = addNode('Mori.RoadSash', meshes.navy, { translation: [0.13, -0.15, 0.57], rotation: quatZ(-22), scale: [0.46, 0.10, 0.055] });
const sashDot = addNode('Mori.RoadLight', meshes.gold, { translation: [0.13, -0.15, 0.69], scale: [0.055, 0.055, 0.035] });
const leftArm = addNode('Mori.Arm.Left', meshes.round, { translation: [-0.77, -0.20, 0.05], rotation: quatZ(24), scale: [0.19, 0.45, 0.17] });
const rightArm = addNode('Mori.Arm.Right', meshes.round, { translation: [0.77, -0.20, 0.05], rotation: quatZ(-24), scale: [0.19, 0.45, 0.17] });
const leftEye = addNode('Mori.Eye.Left', meshes.ink, { translation: [-0.24, 0.26, 0.72], scale: [0.105, 0.17, 0.055] });
const rightEye = addNode('Mori.Eye.Right', meshes.ink, { translation: [0.24, 0.26, 0.72], scale: [0.105, 0.17, 0.055] });
const leftSparkle = addNode('Mori.EyeSparkle.Left', meshes.white, { translation: [-0.27, 0.32, 0.775], scale: [0.030, 0.048, 0.016] });
const rightSparkle = addNode('Mori.EyeSparkle.Right', meshes.white, { translation: [0.21, 0.32, 0.775], scale: [0.030, 0.048, 0.016] });
const leftBlush = addNode('Mori.Blush.Left', meshes.blush, { translation: [-0.47, -0.02, 0.70], scale: [0.13, 0.065, 0.026] });
const rightBlush = addNode('Mori.Blush.Right', meshes.blush, { translation: [0.47, -0.02, 0.70], scale: [0.13, 0.065, 0.026] });
const mouth = addNode('Mori.Mouth', meshes.ink, { translation: [0, 0.00, 0.73], scale: [0.095, 0.038, 0.020] });
gltf.nodes[root].children = [body, face, leftTab, rightTab, antenna, sash, sashDot, leftArm, rightArm, leftEye, rightEye, leftSparkle, rightSparkle, leftBlush, rightBlush, mouth];

animation('idle', [{
  node: root,
  path: 'translation',
  type: 'VEC3',
  times: [0, 1, 2],
  values: [0, 0, 0, 0, 0.035, 0, 0, 0, 0],
}]);
animation('wave', [{
  node: rightArm,
  path: 'rotation',
  type: 'VEC4',
  times: [0, 0.22, 0.44, 0.66],
  values: [...quatZ(-24), ...quatZ(-72), ...quatZ(-36), ...quatZ(-24)],
}]);
animation('surprise', [
  {
    node: root,
    path: 'scale',
    type: 'VEC3',
    times: [0, 0.16, 0.45],
    values: [1, 1, 1, 1.10, 0.92, 1.08, 1, 1, 1],
  },
  {
    node: leftEye,
    path: 'scale',
    type: 'VEC3',
    times: [0, 0.16, 0.45],
    values: [0.105, 0.17, 0.055, 0.145, 0.225, 0.055, 0.105, 0.17, 0.055],
  },
  {
    node: rightEye,
    path: 'scale',
    type: 'VEC3',
    times: [0, 0.16, 0.45],
    values: [0.105, 0.17, 0.055, 0.145, 0.225, 0.055, 0.105, 0.17, 0.055],
  },
]);
animation('blink', [
  {
    node: leftEye,
    path: 'scale',
    type: 'VEC3',
    times: [0, 0.08, 0.17],
    values: [0.105, 0.17, 0.055, 0.12, 0.025, 0.055, 0.105, 0.17, 0.055],
  },
  {
    node: rightEye,
    path: 'scale',
    type: 'VEC3',
    times: [0, 0.08, 0.17],
    values: [0.105, 0.17, 0.055, 0.12, 0.025, 0.055, 0.105, 0.17, 0.055],
  },
]);

gltf.buffers[0].byteLength = binaryLength;
const json = Buffer.from(JSON.stringify(gltf));
const jsonPadding = (4 - (json.length % 4)) % 4;
const jsonChunk = Buffer.concat([json, Buffer.alloc(jsonPadding, 0x20)]);
const binary = Buffer.concat(binaryParts);
const binaryPadding = (4 - (binary.length % 4)) % 4;
const binaryChunk = Buffer.concat([binary, Buffer.alloc(binaryPadding)]);
const header = Buffer.alloc(12);
header.writeUInt32LE(0x46546c67, 0);
header.writeUInt32LE(2, 4);
header.writeUInt32LE(12 + 8 + jsonChunk.length + 8 + binaryChunk.length, 8);
const jsonHeader = Buffer.alloc(8);
jsonHeader.writeUInt32LE(jsonChunk.length, 0);
jsonHeader.writeUInt32LE(0x4e4f534a, 4);
const binaryHeader = Buffer.alloc(8);
binaryHeader.writeUInt32LE(binaryChunk.length, 0);
binaryHeader.writeUInt32LE(0x004e4942, 4);

fs.mkdirSync(outputDir, { recursive: true });
const output = Buffer.concat([header, jsonHeader, jsonChunk, binaryHeader, binaryChunk]);
fs.writeFileSync(outputFile, output);
fs.writeFileSync(path.join(outputDir, 'manifest.json'), `${JSON.stringify({
  schema: 1,
  id: 'dev.mori',
  name: '모리 — 신호 요정',
  version: '0.1.0',
  format: 'glb',
  model: 'avatar.glb',
  files: [{
    path: 'avatar.glb',
    size: output.length,
    sha256: crypto.createHash('sha256').update(output).digest('hex'),
  }],
  animations: {
    idle: 'idle',
    reactions: { BRAKE: 'surprise', TURN: 'wave', MODE: 'idle', TIRE: 'surprise' },
  },
  provenance: {
    character: 'original',
    license: 'Proprietary',
    commercialUse: true,
    attribution: 'Pleos Voice Companion 디자인 시제품',
  },
}, null, 2)}\n`);
const triangleCount = gltf.meshes.reduce((total, mesh) => total + gltf.accessors[mesh.primitives[0].indices].count / 3, 0);
console.log(`Wrote ${path.relative(process.cwd(), outputFile)} (${fs.statSync(outputFile).size} bytes, ${Math.round(triangleCount)} triangles).`);
console.log(`Animations: ${gltf.animations.map((entry) => entry.name).join(', ')}`);
