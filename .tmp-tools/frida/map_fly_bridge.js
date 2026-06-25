/**
 * Last Asylum map fly bridge (com.phs.global v1.0.81).
 * SquadRelay broadcast -> MapFlyReceiver -> trigger file -> this script.
 */
'use strict';

// Frida 17 removed the global `Java` bridge from gadget scripts; import it explicitly.
// This file is bundled with frida-compile before being embedded in the APK.
import Java from 'frida-java-bridge';

const LIB = 'libil2cpp.so';
const TRIGGER_FILE = '/data/data/com.phs.global/files/squadrelay_map_fly.json';
const TRIGGER_SDCARD = '/sdcard/Download/squadrelay_map_fly.json';
const PROBE_FILE = '/data/data/com.phs.global/files/squadrelay_probe.json';
const LOG = '/data/data/com.phs.global/files/la_map_fly_bridge.log';
const LOG_SDCARD = '/sdcard/Download/la_map_fly_bridge.log';

// "В рейд" share bridge: Lua hook writes target payload to a game-private file on
// share-panel open/close; this script polls it and broadcasts to the SquadRelay app.
const SHARE_FILE = '/data/data/com.phs.global/files/squadrelay_share.json';
const SHARE_OK_FILE = '/data/data/com.phs.global/files/squadrelay_share_hook.ok';
const SHARE_ACTION = 'com.lastasylum.alliance.action.SHARE_TARGET';
const SHARE_APP_PKG = 'com.lastasylum.alliance';

// Auto-help: SquadRelay writes a persistent config file; this script periodically
// calls the alliance "help all" network action (same as tapping the in-game Help
// button) while there is something to help with.
const AUTOHELP_FILE = '/data/data/com.phs.global/files/squadrelay_autohelp.json';
const AUTOHELP_SDCARD = '/sdcard/Download/squadrelay_autohelp.json';
const AUTOHELP_MIN_INTERVAL_MS = 5000;
const AUTOHELP_MAX_INTERVAL_MS = 600000;
// Gate on the locally-cached help list (no extra network round-trip), then send
// UnionHelpAllC2S only when the game says there is help available.
const AUTOHELP_LUA = [
  'pcall(function()',
  "  local D = rawget(_G, 'Data')",
  '  if not D then return end',
  '  local ad = D.AllianceData',
  '  if not ad or not ad.help then return end',
  '  local h = ad.help',
  '  local ok, can = pcall(function() return h:IsHaveCanHelpData() end)',
  '  if not ok or not can then return end',
  "  local sm = package.loaded['Logic.Proto.Send.union_help']",
  '  if sm and sm.UnionHelpAllC2S then sm.UnionHelpAllC2S() end',
  'end)',
].join('\n');

const RVA = {
  LuaManager_FormatKXY: 0x2518350,
  LuaManager_SimpleInstrSend: 0x25148d8,
  LuaManager_RequireLua: 0x251410c,
  AppFrame_SimpleInstrSend: 0x316ba08,
  Application_InvokeDeepLinkActivated: 0x4e094f4,
  UnitySynchronizationContext_ExecuteTasks: 0x4e69c6c,
  LuaFunction_Action: 0x2673f8c,
  LuaEnv_DoString: 0x2672f80,
  NGUITools_set_clipboard: 0x2587830,
  set_flyWorldFun: 0x289f524,
  set_flyWorldLua: 0x289f770,
};

let lastTriggerText = '';
let libReadyLogged = false;
let flyHooksReady = false;
let pendingHijack = null;
let activeHijack = null;
let probeObserveUntil = 0;
let lastProbeText = '';
let actionSeen = {};
let actionCatchUntil = 0;
let actionBaselineReady = false;
let liveLuaEnv = ptr(0);
let doStringLogUntil = 0;
let cachedAppFrame = ptr(0);
let mainThreadFlyQueue = [];
let unityMainTid = -1;
let autoHelpEnabled = false;
let autoHelpIntervalMs = 30000;
let autoHelpLastRun = 0;
let lastAutoHelpCfg = '';

function readFileUtf8(path, maxLen) {
  const limit = maxLen || 4096;
  const fopen = new NativeFunction(Module.getGlobalExportByName('fopen'), 'pointer', ['pointer', 'pointer']);
  const fseek = new NativeFunction(Module.getGlobalExportByName('fseek'), 'int', ['pointer', 'long', 'int']);
  const ftell = new NativeFunction(Module.getGlobalExportByName('ftell'), 'long', ['pointer']);
  const fread = new NativeFunction(Module.getGlobalExportByName('fread'), 'ulong', ['pointer', 'ulong', 'ulong', 'pointer']);
  const fclose = new NativeFunction(Module.getGlobalExportByName('fclose'), 'int', ['pointer']);
  const fp = fopen(Memory.allocUtf8String(path), Memory.allocUtf8String('rb'));
  if (fp.isNull()) return '';
  fseek(fp, 0, 2);
  const len = Number(ftell(fp));
  fseek(fp, 0, 0);
  if (len <= 0 || len > limit) {
    fclose(fp);
    return '';
  }
  const buf = Memory.alloc(len + 1);
  fread(buf, 1, len, fp);
  fclose(fp);
  return buf.readUtf8String(len);
}

function writeFileEmpty(path) {
  const fopen = new NativeFunction(Module.getGlobalExportByName('fopen'), 'pointer', ['pointer', 'pointer']);
  const fclose = new NativeFunction(Module.getGlobalExportByName('fclose'), 'int', ['pointer']);
  const fp = fopen(Memory.allocUtf8String(path), Memory.allocUtf8String('wb'));
  if (!fp.isNull()) fclose(fp);
}

function log(line) {
  console.log('[map_fly_bridge] ' + line);
  for (const path of [LOG, LOG_SDCARD]) {
    try {
      const f = new File(path, 'a');
      f.write(line + '\n');
      f.flush();
      f.close();
    } catch (e) {}
  }
}

function il2CppPathFromMaps() {
  const maps = readFileUtf8('/proc/self/maps', 512 * 1024);
  if (!maps) return null;
  for (const line of maps.split('\n')) {
    if (line.indexOf('libil2cpp.so') < 0) continue;
    const pathStart = line.indexOf('/');
    if (pathStart < 0) continue;
    return line.substring(pathStart).trim();
  }
  return null;
}

function libBaseFromMaps() {
  const maps = readFileUtf8('/proc/self/maps', 512 * 1024);
  if (!maps) return null;
  for (const line of maps.split('\n')) {
    if (line.indexOf('libil2cpp.so') < 0) continue;
    const dash = line.indexOf('-');
    if (dash <= 0) continue;
    const start = parseInt(line.substring(0, dash), 16);
    if (!isNaN(start) && start > 0) return ptr(start);
  }
  return null;
}

function libBase() {
  const mod = Process.findModuleByName(LIB);
  if (mod) return mod.base;
  for (let i = 0; i < Process.enumerateModules().length; i++) {
    const m = Process.enumerateModules()[i];
    if (m.name.indexOf('il2cpp') >= 0) return m.base;
  }
  return libBaseFromMaps();
}

function mapsDiagOnce() {
  if (mapsDiagOnce.done) return;
  mapsDiagOnce.done = true;
  const mods = Process.enumerateModules();
  const il2 = mods.filter(function (m) { return m.name.indexOf('il2cpp') >= 0; });
  log('mods=' + mods.length + ' il2cppMods=' + il2.length);
}
mapsDiagOnce.done = false;

function findExport(name) {
  const mod = Process.findModuleByName(LIB);
  if (mod) {
    try {
      const addr = mod.findExportByName(name);
      if (addr) return addr;
    } catch (e) {}
    try {
      const addr = mod.getExportByName(name);
      if (addr) return addr;
    } catch (e) {}
  }
  try {
    return Module.getExportByName(LIB, name);
  } catch (e) {}
  try {
    return Module.getGlobalExportByName(name);
  } catch (e) {}
  return null;
}

function nativeFn(name, ret, args) {
  const api = findExport(name);
  if (!api) return null;
  return new NativeFunction(api, ret, args);
}

function attachIl2CppThread() {
  const domainGet = nativeFn('il2cpp_domain_get', 'pointer', []);
  const attach = nativeFn('il2cpp_thread_attach', 'pointer', ['pointer']);
  if (domainGet && attach) {
    attach(domainGet());
  }
}

function allocIl2CppString(text) {
  const fn = nativeFn('il2cpp_string_new', 'pointer', ['pointer']);
  if (fn) return fn(Memory.allocUtf8String(text));
  const len = text.length;
  const p = Memory.alloc(0x14 + len * 2);
  p.add(0x10).writeS32(len);
  p.add(0x14).writeUtf16String(text);
  return p;
}

function findImageByName(namePart) {
  attachIl2CppThread();
  const domainGet = nativeFn('il2cpp_domain_get', 'pointer', []);
  const getAssemblies = nativeFn('il2cpp_domain_get_assemblies', 'pointer', ['pointer', 'pointer']);
  const assemblyGetImage = nativeFn('il2cpp_assembly_get_image', 'pointer', ['pointer']);
  const imageGetName = nativeFn('il2cpp_image_get_name', 'pointer', ['pointer']);
  const domainAssemblyOpen = nativeFn('il2cpp_domain_assembly_open', 'pointer', ['pointer', 'pointer']);
  if (!domainGet || !assemblyGetImage) return ptr(0);
  const domain = domainGet();
  if (getAssemblies && imageGetName) {
    const countPtr = Memory.alloc(8);
    countPtr.writeU64(0);
    const assemblies = getAssemblies(domain, countPtr);
    const count = Number(countPtr.readU64());
    for (let i = 0; i < count; i++) {
      const asm = assemblies.add(i * Process.pointerSize).readPointer();
      if (asm.isNull()) continue;
      const image = assemblyGetImage(asm);
      if (image.isNull()) continue;
      const name = imageGetName(image).readCString();
      if (name && name.indexOf(namePart) >= 0) return image;
    }
  }
  if (domainAssemblyOpen) {
    const asm = domainAssemblyOpen(domain, Memory.allocUtf8String(namePart));
    if (!asm.isNull()) return assemblyGetImage(asm);
  }
  return ptr(0);
}

function getIl2CppByteClass() {
  const classFromName = nativeFn('il2cpp_class_from_name', 'pointer', ['pointer', 'pointer', 'pointer']);
  if (!classFromName) return ptr(0);
  const images = ['mscorlib.dll', 'System.Private.CoreLib.dll', 'Assembly-CSharp'];
  for (let i = 0; i < images.length; i++) {
    const image = findImageByName(images[i]);
    if (image.isNull()) continue;
    const byteClass = classFromName(
      image,
      Memory.allocUtf8String('System'),
      Memory.allocUtf8String('Byte'),
    );
    if (!byteClass.isNull()) return byteClass;
  }
  return ptr(0);
}

function readIl2CppString(p) {
  if (!p || p.isNull()) return null;
  try {
    const len = p.add(0x10).readS32();
    if (len <= 0 || len > 65536) return null;
    return p.add(0x14).readUtf16String(len);
  } catch (e) {
    return null;
  }
}

function findManagedClass(namespace, className) {
  attachIl2CppThread();
  const classFromName = nativeFn('il2cpp_class_from_name', 'pointer', ['pointer', 'pointer', 'pointer']);
  const domainGet = nativeFn('il2cpp_domain_get', 'pointer', []);
  const getAssemblies = nativeFn('il2cpp_domain_get_assemblies', 'pointer', ['pointer', 'pointer']);
  const assemblyGetImage = nativeFn('il2cpp_assembly_get_image', 'pointer', ['pointer']);
  if (!classFromName || !domainGet || !getAssemblies || !assemblyGetImage) return ptr(0);
  const domain = domainGet();
  const countPtr = Memory.alloc(8);
  countPtr.writeU64(0);
  const assemblies = getAssemblies(domain, countPtr);
  const count = Number(countPtr.readU64());
  const ns = namespace ? Memory.allocUtf8String(namespace) : Memory.allocUtf8String('');
  const cn = Memory.allocUtf8String(className);
  for (let i = 0; i < count; i++) {
    const asm = assemblies.add(i * Process.pointerSize).readPointer();
    if (asm.isNull()) continue;
    const image = assemblyGetImage(asm);
    if (image.isNull()) continue;
    const cls = classFromName(image, ns, cn);
    if (!cls.isNull()) return cls;
  }
  return ptr(0);
}

function readStaticByte(cls, offset) {
  if (cls.isNull()) return -1;
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  if (!getStaticFieldData) return -1;
  const staticData = getStaticFieldData(cls);
  if (staticData.isNull()) return -1;
  return staticData.add(offset).readU8();
}

function readStaticObject(namespace, className, fieldName) {
  const cls = findManagedClass(namespace, className);
  if (cls.isNull()) return ptr(-1);
  const classInit = nativeFn('il2cpp_runtime_class_init', 'void', ['pointer']);
  if (classInit) {
    try { classInit(cls); } catch (e) {}
  }
  const getField = nativeFn('il2cpp_class_get_field_from_name', 'pointer', ['pointer', 'pointer']);
  const staticGet = nativeFn('il2cpp_field_static_get_value', 'void', ['pointer', 'pointer']);
  if (!getField || !staticGet) return ptr(-1);
  const field = getField(cls, Memory.allocUtf8String(fieldName));
  if (field.isNull()) return ptr(-1);
  const out = Memory.alloc(8);
  out.writePointer(ptr(0));
  staticGet(field, out);
  return out.readPointer();
}

function logFlyFields(tag) {
  const fun = readStaticObject('', 'GameCommandBase', 'flyWorldFun');
  const lua = readStaticObject('', 'GameCommandBase', 'flyWorldLua');
  log(tag + ' flyWorldFun=' + fun + ' flyWorldLua=' + lua);
  return fun;
}

function installActionCatchHook() {
  if (installActionCatchHook.done) return;
  const base = libBase();
  if (!base) return;
  installActionCatchHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaFunction_Action), {
      onEnter(args) {
        const key = args[0].toString();
        if (actionSeen[key]) return;
        actionSeen[key] = true;
        if (actionBaselineReady && actionCatchUntil > Date.now()) {
          log('>>> Action NEW this=' + key);
        }
      },
    });
    log('LuaFunction.Action catch hook installed');
    setTimeout(function () {
      actionBaselineReady = true;
      log('action baseline ready (seen=' + Object.keys(actionSeen).length + ')');
    }, 6000);
  } catch (e) {
    log('Action catch hook failed: ' + e);
  }
}
installActionCatchHook.done = false;

function installDoStringHook() {
  if (installDoStringHook.done) return;
  const base = libBase();
  if (!base) return;
  installDoStringHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaEnv_DoString), {
      onEnter(args) {
        liveLuaEnv = args[0];
        if (doStringLogUntil > Date.now()) {
          const chunk = readIl2CppString(args[1]);
          if (chunk) log('DoString: ' + chunk.substring(0, 240).replace(/\s+/g, ' '));
        }
      },
    });
    log('LuaEnv.DoString hook installed');
  } catch (e) {
    log('DoString hook failed: ' + e);
  }
}
installDoStringHook.done = false;

function doStringNow(code) {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return false;
  const base = libBase();
  if (!base) return false;
  try {
    attachIl2CppThread();
    const doStr = new NativeFunction(
      base.add(RVA.LuaEnv_DoString),
      'pointer',
      ['pointer', 'pointer', 'pointer', 'pointer']
    );
    doStr(liveLuaEnv, allocIl2CppString(code), allocIl2CppString('squadrelay'), ptr(0));
    return true;
  } catch (e) {
    log('doStringNow failed: ' + e);
    return false;
  }
}

function runLua(code) {
  if (!liveLuaEnv || liveLuaEnv.isNull()) {
    log('runLua: no live LuaEnv captured yet (open the game / world map first)');
    return;
  }
  mainThreadFlyQueue.push(function () {
    if (doStringNow(code)) {
      log('runLua ok: ' + code.substring(0, 120));
    }
  });
}

// Verified in-game world-map camera fly for PHS Global v1.0.81:
// GlobalMapCtrlManager:GetWorldManager():JumpToCellPosWithServerId(X, Y, serverId)
// Handles same-server and cross-server jumps (calls EnterOtherMap_InSameMap internally).
function flyViaLuaJump(x, y, server) {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return false;
  const code =
    'pcall(function() GlobalMapCtrlManager:GetWorldManager():JumpToCellPosWithServerId(' +
    x +
    ',' +
    y +
    ',' +
    server +
    ') end)';
  if (doStringNow(code)) {
    log('fly via lua JumpToCellPosWithServerId(' + x + ',' + y + ',' + server + ')');
    return true;
  }
  return false;
}

function installFlyWorldSetterHooks() {
  if (installFlyWorldSetterHooks.done) return;
  const base = libBase();
  if (!base) return;
  installFlyWorldSetterHooks.done = true;
  try {
    Interceptor.attach(base.add(RVA.set_flyWorldFun), {
      onLeave() {
        log('>>> flyWorldFun SET by game');
        logFlyFields('after set_flyWorldFun');
      },
    });
    Interceptor.attach(base.add(RVA.set_flyWorldLua), {
      onLeave() {
        log('>>> flyWorldLua SET by game');
        logFlyFields('after set_flyWorldLua');
      },
    });
    log('flyWorld setter hooks installed');
  } catch (e) {
    log('flyWorld setter hooks failed: ' + e);
  }
}
installFlyWorldSetterHooks.done = false;

function readStaticPointer(cls, offset) {
  if (cls.isNull()) return ptr(0);
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  if (!getStaticFieldData) return ptr(0);
  const staticData = getStaticFieldData(cls);
  if (staticData.isNull()) return ptr(0);
  return staticData.add(offset).readPointer();
}

function ensureFlyHooks() {
  if (flyHooksReady) return;
  if (!libBase()) return;
  flyHooksReady = true;
  installAppFrameCacheHook();
  installWorldMapHijack();
  installMainThreadFlyDrain();
  log('fly hooks installed (deferred)');
}

function installWorldMapHijack() {
  if (installWorldMapHijack.done) return;
  const base = libBase();
  if (!base) return;
  installWorldMapHijack.done = true;
  const patch = function (cmdIndex, dataIndex, args) {
    if (probeObserveUntil > Date.now()) {
      const pc = readIl2CppString(args[cmdIndex]);
      if (pc === 'WorldMapViewC2S') {
        log('probe WorldMapView ' + hexBytes(readByteArray(args[dataIndex]) || []));
      } else if (pc && pc !== 'Pong') {
        log('probe cmd=' + pc);
      }
    }
    if (!activeHijack || activeHijack.framesLeft <= 0) return;
    const cmd = readIl2CppString(args[cmdIndex]);
    if (!cmd) return;
    if (cmd !== 'WorldMapViewC2S') {
      log('hijack see cmd=' + cmd);
      return;
    }
    const h = activeHijack;
    const view = buildWorldMapView(h.x, h.y, h.server, h.cross);
    args[dataIndex] = allocByteArray(view);
    h.patched++;
    log('hijack WorldMapViewC2S -> ' + hexBytes(view) + ' (patched=' + h.patched + ')');
  };
  try {
    Interceptor.attach(base.add(RVA.LuaManager_SimpleInstrSend), {
      onEnter(args) {
        patch(0, 1, args);
      },
    });
    Interceptor.attach(base.add(RVA.AppFrame_SimpleInstrSend), {
      onEnter(args) {
        patch(1, 2, args);
      },
    });
    log('WorldMapView hijack installed');
  } catch (e) {
    log('WorldMapView hijack failed: ' + e);
  }
}
installWorldMapHijack.done = false;

function logFlyPreconditions() {
  try {
    const luaCls = findManagedClass('GameFrameWork', 'LuaManager');
    const cmdCls = findManagedClass('', 'GameCommandBase');
    log(
      'pre fly isWorldNetwork=' + readStaticByte(luaCls, 0x0a) +
        ' isInitLua=' + readStaticByte(luaCls, 0x18) +
        ' flyWorldFun=' + readStaticPointer(cmdCls, 0x20),
    );
  } catch (e) {
    log('pre fly skipped: ' + e);
  }
}

function findAppFrameClass() {
  return findManagedClass('GameFrameWork', 'AppFrame');
}

function getAppFrameInstance() {
  if (!cachedAppFrame.isNull()) return cachedAppFrame;
  const cls = findAppFrameClass();
  if (cls.isNull()) return ptr(0);
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  if (getStaticFieldData) {
    const staticData = getStaticFieldData(cls);
    if (!staticData.isNull()) {
      const inst = staticData.readPointer();
      if (!inst.isNull()) {
        cachedAppFrame = inst;
        log('AppFrame.instance via static field data -> ' + inst);
        return inst;
      }
    }
  }
  const getField = nativeFn('il2cpp_class_get_field_from_name', 'pointer', ['pointer', 'pointer']);
  const staticGet = nativeFn('il2cpp_field_static_get_value', 'void', ['pointer', 'pointer']);
  if (getField && staticGet) {
    const field = getField(cls, Memory.allocUtf8String('instance'));
    if (!field.isNull()) {
      const out = Memory.alloc(Process.pointerSize);
      staticGet(field, out);
      const inst = out.readPointer();
      if (!inst.isNull()) {
        cachedAppFrame = inst;
        log('AppFrame.instance via static_get_value -> ' + inst);
        return inst;
      }
    }
  }
  return ptr(0);
}

function installAppFrameCacheHook() {
  if (installAppFrameCacheHook.done) return;
  const base = libBase();
  if (!base) return;
  installAppFrameCacheHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.AppFrame_SimpleInstrSend), {
      onEnter(args) {
        const self = args[0];
        if (!self.isNull()) cachedAppFrame = self;
      },
    });
    log('AppFrame cache hook installed');
  } catch (e) {
    log('AppFrame cache hook failed: ' + e);
  }
}
installAppFrameCacheHook.done = false;

function writeRawBytes(dest, buf) {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  dest.writeByteArray(bytes);
}

function readByteArray(p) {
  if (!p || p.isNull()) return null;
  try {
    const len = p.add(0x18).readS32();
    if (len <= 0 || len > 4096) return null;
    const bytes = p.add(0x20).readByteArray(len);
    return new Uint8Array(bytes);
  } catch (e) {
    return null;
  }
}

function allocByteArray(bytes) {
  const buf = new Uint8Array(bytes);
  const arrayNewFn = nativeFn('il2cpp_array_new', 'pointer', ['pointer', 'ulong']);
  const byteClass = getIl2CppByteClass();
  if (!arrayNewFn || byteClass.isNull()) {
    throw new Error('il2cpp byte[] API unavailable');
  }
  const arr = arrayNewFn(byteClass, buf.length);
  if (arr.isNull()) {
    throw new Error('il2cpp_array_new returned null');
  }
  writeRawBytes(arr.add(0x20), buf);
  return arr;
}

function encodeVarint(n) {
  const out = [];
  let v = n >>> 0;
  while (v > 0x7f) {
    out.push((v & 0x7f) | 0x80);
    v >>>= 7;
  }
  out.push(v);
  return out;
}

function tagField(fieldNum, wireType) {
  return encodeVarint((fieldNum << 3) | wireType);
}

function buildEnterWorldMap(server) {
  return Uint8Array.from([...tagField(1, 0), ...encodeVarint(server)]);
}

function buildWorldMapView(x, y, server, crossServer) {
  // Wire order from in-game chat tap (ingame-coord-device-20260622-012332.log):
  // outer: field1=1, field3=0x2b, field4=embedded, [field5=1 if cross], field2=0x13
  // inner: field2=Y, field1=server, field3=X
  const inner = [
    ...tagField(2, 0),
    ...encodeVarint(y),
    ...tagField(1, 0),
    ...encodeVarint(server),
    ...tagField(3, 0),
    ...encodeVarint(x),
  ];
  const outer = [
    ...tagField(1, 0),
    ...encodeVarint(1),
    ...tagField(3, 0),
    ...encodeVarint(0x2b),
    ...tagField(4, 2),
    ...encodeVarint(inner.length),
    ...inner,
  ];
  if (crossServer) {
    outer.push(...tagField(5, 0), ...encodeVarint(1));
  }
  outer.push(...tagField(2, 0), ...encodeVarint(0x13));
  return Uint8Array.from(outer);
}

function hexBytes(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join(' ');
}

function invokeDeepLink(url) {
  const base = libBase();
  if (!base) return false;
  attachIl2CppThread();
  const fn = new NativeFunction(
    base.add(RVA.Application_InvokeDeepLinkActivated),
    'void',
    ['pointer'],
  );
  fn(allocIl2CppString(url));
  log('deepLink ' + url);
  return true;
}

function allocCmdData(payloadBytes) {
  return payloadBytes ? allocByteArray(payloadBytes) : ptr(0);
}

function simpleInstrSendLua(cmd, payloadBytes) {
  const base = libBase();
  if (!base) throw new Error('libil2cpp.so not loaded');
  attachIl2CppThread();
  const cmdPtr = allocIl2CppString(cmd);
  const luaSend = new NativeFunction(
    base.add(RVA.LuaManager_SimpleInstrSend),
    'int',
    ['pointer', 'pointer', 'pointer', 'uint8', 'int32'],
  );
  const rc = luaSend(cmdPtr, allocCmdData(payloadBytes), ptr(0), 0, 0);
  log('LuaManager.SimpleInstrSend(' + cmd + ') rc=' + rc);
  return rc;
}

function simpleInstrSendApp(cmd, payloadBytes) {
  const base = libBase();
  if (!base) throw new Error('libil2cpp.so not loaded');
  attachIl2CppThread();
  const appFrame = getAppFrameInstance();
  if (appFrame.isNull()) {
    log('AppFrame.instance null for ' + cmd);
    return -1;
  }
  const cmdPtr = allocIl2CppString(cmd);
  const appSend = new NativeFunction(
    base.add(RVA.AppFrame_SimpleInstrSend),
    'int',
    ['pointer', 'pointer', 'pointer', 'pointer', 'uint8', 'int32'],
  );
  const rc = appSend(appFrame, cmdPtr, allocCmdData(payloadBytes), ptr(0), 0, 0);
  log('AppFrame.SimpleInstrSend(' + cmd + ') rc=' + rc);
  return rc;
}

function simpleInstrSend(cmd, payloadBytes) {
  const rcLua = simpleInstrSendLua(cmd, payloadBytes);
  const rcApp = simpleInstrSendApp(cmd, payloadBytes);
  return rcApp >= 0 ? rcApp : rcLua;
}

function requireWorldMapHud() {
  const base = libBase();
  if (!base) return false;
  attachIl2CppThread();
  const fn = new NativeFunction(base.add(RVA.LuaManager_RequireLua), 'pointer', ['pointer']);
  const table = fn(allocIl2CppString('UIs.WorldMapUI.Hud.WorldMapHudPanel_Collection'));
  log('RequireLua WorldMapHud -> ' + table);
  return !table.isNull();
}

function invokeFlyWorldFun() {
  const base = libBase();
  if (!base) return false;
  const fun = readStaticPointer(findManagedClass('', 'GameCommandBase'), 0x20);
  log('flyWorldFun=' + fun);
  if (fun.isNull()) return false;
  attachIl2CppThread();
  const action = new NativeFunction(base.add(RVA.LuaFunction_Action), 'void', ['pointer']);
  action(fun);
  log('flyWorldFun.Action() done');
  return true;
}

function setMapClipboard(x, y, server) {
  const xy = 'X:' + x + ' Y:' + y;
  const bracket = '[#:' + server + ' X:' + x + ' Y:' + y + ']';
  const base = libBase();
  if (base) {
    try {
      attachIl2CppThread();
      const fn = new NativeFunction(base.add(RVA.NGUITools_set_clipboard), 'void', ['pointer']);
      fn(allocIl2CppString(xy));
      log('NGUITools.set_clipboard ' + xy);
      fn(allocIl2CppString(bracket));
      log('NGUITools.set_clipboard ' + bracket);
    } catch (e) {
      log('NGUITools.set_clipboard failed: ' + e);
    }
  }
}

function queueAfterFrames(frames, fn) {
  if (frames <= 0) {
    fn();
    return;
  }
  mainThreadFlyQueue.push(function () {
    queueAfterFrames(frames - 1, fn);
  });
}

function invokeFlyWorldLua() {
  const base = libBase();
  if (!base) return false;
  const lua = readStaticPointer(findManagedClass('', 'GameCommandBase'), 0x28);
  log('flyWorldLua=' + lua);
  if (lua.isNull()) return false;
  attachIl2CppThread();
  const call = new NativeFunction(base.add(0x26741b0), 'pointer', ['pointer', 'pointer']);
  call(lua, ptr(0));
  log('flyWorldLua.Call() done');
  return true;
}

function tryClientFly(x, y, server) {
  try {
    requireWorldMapHud();
  } catch (e) {
    log('RequireLua skipped: ' + e);
  }
  setMapClipboard(x, y, server);
  formatKXY(x, y, server);
  if (invokeFlyWorldFun()) return true;
  if (invokeFlyWorldLua()) return true;
  return false;
}

function sendWorldMapView(x, y, server, crossServer) {
  const view = buildWorldMapView(x, y, server, crossServer);
  log('WorldMapViewC2S ' + hexBytes(view));
  simpleInstrSendLua('WorldMapViewC2S', view);
  simpleInstrSendApp('WorldMapViewC2S', view);
}

function armHijack(x, y, server, cross) {
  activeHijack = { x: x, y: y, server: server, cross: cross, framesLeft: 120, patched: 0 };
  pendingHijack = activeHijack;
}

function scheduleDeepLinkBurst(urls, frameGap) {
  let at = 0;
  for (let i = 0; i < urls.length; i++) {
    const url = urls[i];
    queueAfterFrames(at, function () {
      invokeDeepLink(url);
      log('burst deepLink ' + url);
    });
    at += frameGap;
  }
}

function flyViaClipboardBurst(x, y, server, cross) {
  const srv = server > 0 ? server : 109;
  armHijack(x, y, srv, cross);
  setMapClipboard(x, y, srv);
  formatKXY(x, y, srv);
  scheduleDeepLinkBurst(
    [
      'globalphslink://world/' + srv,
      'globalphslink://map?x=' + x,
      'globalphslink://map?y=' + y,
      'globalphslink://map/' + x + '/' + y + '/' + srv,
      'globalphslink://coordinate/' + x + '/' + y + '/' + srv,
      'globalphslink://map',
    ],
    12,
  );
  log('clipboard burst armed for ' + x + '/' + y + '/' + srv);
  waitHijackOrFallback(x, y, srv, cross, 150);
}

function finishHijackSuccess(patched) {
  activeHijack = null;
  pendingHijack = null;
  log('hijack success patched=' + patched);
  try {
    if (invokeFlyWorldFun() || invokeFlyWorldLua()) {
      log('post-hijack client fly');
    }
  } catch (e) {
    log('post-hijack client fly failed: ' + e);
  }
}

function waitHijackOrFallback(x, y, server, cross, framesLeft) {
  if (activeHijack && activeHijack.framesLeft > 0) {
    activeHijack.framesLeft--;
  }
  if (activeHijack && activeHijack.patched >= 2) {
    finishHijackSuccess(activeHijack.patched);
    return;
  }
  if (framesLeft <= 0) {
    const patched = activeHijack ? activeHijack.patched : 0;
    activeHijack = null;
    pendingHijack = null;
    if (patched > 0) {
      finishHijackSuccess(patched);
      return;
    }
    log('hijack timeout — WorldMapView fallback');
    try {
      tryClientFly(x, y, server);
    } catch (e) {
      log('client fly retry failed: ' + e);
    }
    sendWorldMapView(x, y, server, cross);
    return;
  }
  mainThreadFlyQueue.push(function () {
    waitHijackOrFallback(x, y, server, cross, framesLeft - 1);
  });
}

function flyViaDeepLinks(x, y, server) {
  const srv = server > 0 ? server : 109;
  invokeDeepLink('globalphslink://world/' + srv);
  invokeDeepLink('globalphslink://map');
  invokeDeepLink('globalphslink://coordinate/' + y + '/' + x + '/' + srv);
}

function formatKXY(x, y, server) {
  const base = libBase();
  if (!base) throw new Error('libil2cpp.so not loaded');
  attachIl2CppThread();
  const bracket = '[#:' + server + ' X:' + x + ' Y:' + y + ']';
  const fn = new NativeFunction(base.add(RVA.LuaManager_FormatKXY), 'pointer', ['pointer', 'int32', 'int32']);
  const table = fn(allocIl2CppString(bracket), 9999, 9999);
  log('FormatKXY("' + bracket + '") -> ' + table);
  return table;
}

function waitForLib(retries, onReady) {
  if (libBase()) {
    onReady();
    return;
  }
  if (retries <= 0) {
    log('waiting for libil2cpp.so...');
    setTimeout(function () {
      waitForLib(120, onReady);
    }, 500);
    return;
  }
  setTimeout(function () {
    waitForLib(retries - 1, onReady);
  }, 500);
}

let pendingFlies = [];
let drainScheduled = false;

function clearTriggerFiles() {
  writeFileEmpty(TRIGGER_FILE);
  writeFileEmpty(TRIGGER_SDCARD);
  lastTriggerText = '';
}

function drainPendingFliesNative() {
  if (!pendingFlies.length) return;
  if (!libBase()) return;
  const batch = pendingFlies.slice();
  pendingFlies = [];
  log('drain pending flies count=' + batch.length);
  for (let i = 0; i < batch.length; i++) {
    const p = batch[i];
    if (!flyToMapNow(p.x, p.y, p.server, p.cross)) {
      pendingFlies.push(p);
    }
  }
}

function scheduleDrainPendingFlies() {
  if (drainScheduled || !pendingFlies.length) return;
  if (!libBase()) return;
  drainScheduled = true;
  drainScheduled = false;
  drainPendingFliesNative();
}

function currentThreadLabel() {
  try {
    const gettid = new NativeFunction(Module.getGlobalExportByName('gettid'), 'int', []);
    return 'tid=' + gettid();
  } catch (e) {
    return 'tid=?';
  }
}

function installMainThreadFlyDrain() {
  if (installMainThreadFlyDrain.done) return;
  const base = libBase();
  if (!base) return;
  installMainThreadFlyDrain.done = true;
  try {
    Interceptor.attach(base.add(RVA.UnitySynchronizationContext_ExecuteTasks), {
      onEnter() {
        if (unityMainTid < 0) {
          try {
            const gettid = new NativeFunction(Module.getGlobalExportByName('gettid'), 'int', []);
            unityMainTid = gettid();
            log('unity main tid=' + unityMainTid);
          } catch (e) {}
        }
        if (!mainThreadFlyQueue.length) return;
        const batch = mainThreadFlyQueue.slice();
        mainThreadFlyQueue = [];
        for (let i = 0; i < batch.length; i++) {
          try {
            batch[i]();
          } catch (e) {
            log('main-thread fly failed: ' + e);
          }
        }
      },
    });
    log('Unity main-thread fly drain installed');
  } catch (e) {
    log('Unity main-thread fly drain failed: ' + e);
  }
}
installMainThreadFlyDrain.done = false;

function runOnGameThread(fn) {
  ensureFlyHooks();
  mainThreadFlyQueue.push(function () {
    const tid = currentThreadLabel();
    const onMain = unityMainTid >= 0 && tid.indexOf('tid=' + unityMainTid) >= 0;
    log('fly worker ' + tid + (onMain ? ' (unity main)' : ' (NOT unity main)'));
    fn();
  });
  log('queued fly for unity main thread (q=' + mainThreadFlyQueue.length + ')');
}

function flyToMapNow(x, y, server, crossServer) {
  const base = libBase();
  if (!base) return false;
  const srv = server > 0 ? server : 109;
  runOnGameThread(function () {
    try {
      log('flyToMap x=' + x + ' y=' + y + ' server=' + srv + ' cross=' + crossServer + ' base=' + base);
      if (flyViaLuaJump(x, y, srv)) {
        log('fly path: lua JumpToCellPosWithServerId');
        return;
      }
      log('lua jump unavailable (liveLuaEnv null) - falling back');
      logFlyPreconditions();
      if (crossServer) {
        log('cross-server fly');
        simpleInstrSendApp('EnterWorldMapC2S', buildEnterWorldMap(srv));
        mainThreadFlyQueue.push(function () {
          flyViaClipboardBurst(x, y, srv, true);
        });
        return;
      }
      try {
        if (tryClientFly(x, y, srv)) {
          mainThreadFlyQueue.push(function () {
            sendWorldMapView(x, y, srv, false);
          });
          log('fly path: client flyWorld');
          return;
        }
      } catch (nativeErr) {
        log('client fly failed: ' + nativeErr);
      }
      flyViaClipboardBurst(x, y, srv, false);
    } catch (e) {
      log('flyToMap error: ' + e + (e.stack ? '\n' + e.stack : ''));
      flyViaDeepLinks(x, y, srv);
    }
  });
  return true;
}

function flyToMap(x, y, server, crossServer) {
  if (x < 0 || y < 0) {
    log('flyToMap: invalid x/y');
    return;
  }
  ensureFlyHooks();
  pendingFlies.push({ x: x, y: y, server: server, cross: crossServer });
  log('flyToMap pending x=' + x + ' y=' + y + ' server=' + server + ' lib=' + !!libBase());
  if (libBase()) {
    scheduleDrainPendingFlies();
    return;
  }
  waitForLib(240, function () {
    if (!libBase()) {
      log('flyToMap: libil2cpp still missing');
      return;
    }
    scheduleDrainPendingFlies();
  });
}

function parseTriggerJson(text) {
  const m = text.match(/"x"\s*:\s*(\d+)/);
  const my = text.match(/"y"\s*:\s*(\d+)/);
  const ms = text.match(/"server"\s*:\s*(\d+)/);
  const mc = text.match(/"crossServer"\s*:\s*(true|false)/);
  if (!m || !my) return null;
  return {
    x: parseInt(m[1], 10),
    y: parseInt(my[1], 10),
    server: ms ? parseInt(ms[1], 10) : -1,
    cross: mc ? mc[1] === 'true' : false,
  };
}

function pollTriggerFile() {
  const paths = [TRIGGER_FILE, TRIGGER_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    const path = paths[i];
    try {
      const text = readFileUtf8(path);
      if (!text || !text.trim() || text === lastTriggerText) continue;
      lastTriggerText = text;
      const payload = parseTriggerJson(text);
      if (!payload) {
        log('trigger parse failed (' + path + '): ' + text);
        continue;
      }
      log('trigger file (' + path + '): ' + text.trim());
      flyToMap(payload.x, payload.y, payload.server, payload.cross);
      writeFileEmpty(path);
      lastTriggerText = '';
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('poll error (' + path + '): ' + e);
      }
    }
  }
}

function runProbe(clip, url) {
  ensureFlyHooks();
  installFlyWorldSetterHooks();
  installActionCatchHook();
  installDoStringHook();
  if (url === 'armcatch') {
    actionCatchUntil = Date.now() + 12000;
    probeObserveUntil = Date.now() + 12000;
    doStringLogUntil = Date.now() + 12000;
    log('>>> ARMED 12s (Action+DoString) — tap a chat coordinate NOW (baselineReady=' + actionBaselineReady + ', seen=' + Object.keys(actionSeen).length + ', liveLuaEnv=' + liveLuaEnv + ')');
    return;
  }
  if (url && url.indexOf('lua:') === 0) {
    log('lua probe: ' + url.slice(4).substring(0, 160));
    probeObserveUntil = Date.now() + 8000;
    runLua(url.slice(4));
    return;
  }
  if (url === 'readfly') {
    mainThreadFlyQueue.push(function () {
      logFlyFields('readfly');
      try {
        const luaCls = findManagedClass('GameFrameWork', 'LuaManager');
        log('readfly isInitLua=' + readStaticByte(luaCls, 0x18) + ' isWorldNetwork=' + readStaticByte(luaCls, 0x0a));
      } catch (e) {}
    });
    return;
  }
  if (url === 'callfly') {
    probeObserveUntil = Date.now() + 8000;
    mainThreadFlyQueue.push(function () {
      const fun = logFlyFields('callfly-before');
      if (!fun || fun.isNull() || fun.toInt32() === -1) {
        log('callfly: flyWorldFun null, abort');
        return;
      }
      try {
        if (clip) {
          const m = clip.match(/X:(\d+)\s+Y:(\d+)/);
          if (m) formatKXY(parseInt(m[1], 10), parseInt(m[2], 10), 109);
        }
        const action = new NativeFunction(libBase().add(RVA.LuaFunction_Action), 'void', ['pointer']);
        action(fun);
        log('callfly: flyWorldFun.Action() done');
      } catch (e) {
        log('callfly failed: ' + e);
      }
    });
    return;
  }
  probeObserveUntil = Date.now() + 8000;
  mainThreadFlyQueue.push(function () {
    try {
      if (clip) {
        const base = libBase();
        if (base) {
          attachIl2CppThread();
          const fn = new NativeFunction(base.add(RVA.NGUITools_set_clipboard), 'void', ['pointer']);
          fn(allocIl2CppString(clip));
          log('probe clipboard=' + clip);
        }
      }
      invokeDeepLink(url);
      log('probe deepLink=' + url + ' (observing 8s)');
    } catch (e) {
      log('probe failed: ' + e);
    }
  });
}

function pollProbeFile() {
  try {
    const text = readFileUtf8(PROBE_FILE);
    if (!text || !text.trim() || text === lastProbeText) return;
    lastProbeText = text;
    const mu = text.match(/"url"\s*:\s*"([^"]+)"/);
    const mc = text.match(/"clip"\s*:\s*"([^"]*)"/);
    if (!mu) {
      log('probe parse failed: ' + text);
      return;
    }
    log('probe trigger: ' + text.trim());
    runProbe(mc ? mc[1] : null, mu[1]);
    writeFileEmpty(PROBE_FILE);
    lastProbeText = '';
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('probe poll error: ' + e);
    }
  }
}

function pollAutoHelpConfig() {
  const paths = [AUTOHELP_FILE, AUTOHELP_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim()) continue;
      if (text === lastAutoHelpCfg) return;
      lastAutoHelpCfg = text;
      const me = text.match(/"enabled"\s*:\s*(true|false)/);
      const mi = text.match(/"intervalSec"\s*:\s*(\d+)/);
      if (me) autoHelpEnabled = me[1] === 'true';
      if (mi) {
        let ms = parseInt(mi[1], 10) * 1000;
        if (ms < AUTOHELP_MIN_INTERVAL_MS) ms = AUTOHELP_MIN_INTERVAL_MS;
        if (ms > AUTOHELP_MAX_INTERVAL_MS) ms = AUTOHELP_MAX_INTERVAL_MS;
        autoHelpIntervalMs = ms;
      }
      autoHelpLastRun = 0;
      log('autohelp config: enabled=' + autoHelpEnabled + ' interval=' + autoHelpIntervalMs + 'ms');
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('autohelp config poll error: ' + e);
      }
    }
  }
}

function tickAutoHelp() {
  if (!autoHelpEnabled) return;
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (now - autoHelpLastRun < autoHelpIntervalMs) return;
  autoHelpLastRun = now;
  try {
    runLua(AUTOHELP_LUA);
  } catch (e) {
    log('autohelp tick error: ' + e);
  }
}

function logLibStatusOnce() {
  if (libReadyLogged) return;
  const base = libBase();
  if (base) {
    libReadyLogged = true;
    log('libil2cpp ready @ ' + base);
    installAppFrameCacheHook();
    installWorldMapHijack();
    installFlyWorldSetterHooks();
    installActionCatchHook();
    installDoStringHook();
    return;
  }
  const path = il2CppPathFromMaps();
  if (path) {
    log('libil2cpp in maps but not attached: ' + path);
  }
}

// Lua hook: wraps UIChatSharePanel OnBaseEnter/OnBaseExit to persist the share
// target (paramTable + chest grade/stars from Config.SecretTask) to a game-private file.
const SHARE_HOOK_LUA = [
  "pcall(function()",
  "local pl=package.loaded",
  "local pcls=pl['Eyu.Logic.UI.Panel.Chat.UIChatSharePanel']",
  "if not pcls then return end",
  // Dispatch resolves panel methods via the parent table (idx), not pcls itself.
  "local idx=(getmetatable(pcls) or {}).__index or pcls",
  "local F='/data/data/com.phs.global/files/squadrelay_share.json'",
  "local OK='/data/data/com.phs.global/files/squadrelay_share_hook.ok'",
  "local function esc(s) return (string.gsub(tostring(s),'\"',\"'\")) end",
  "local function wr(t) local f=io.open(F,'w') if f then f:write(t) f:close() end end",
  // OnEnter/OnExit fire for every panel; the shareType+withAll signature gates to the share panel.
  "local function isShare(pt) return type(pt)=='table' and pt.shareType~=nil and pt.withAll~=nil end",
  "if not _G.__sr_share_inst then",
  "_G.__sr_seq=_G.__sr_seq or 0",
  "local oe=idx.OnEnter idx.__sr_oe=oe",
  "idx.OnEnter=function(self,...) local r=oe(self,...) pcall(function()",
  "local pt=self.paramTable",
  "if isShare(pt) then",
  "_G.__sr_seq=_G.__sr_seq+1",
  "local seq=_G.__sr_seq",
  "wr('{\"seq\":'..seq..',\"open\":true,\"x\":'..tostring(pt.x or 0)..',\"y\":'..tostring(pt.y or 0)..',\"sid\":'..tostring(pt.sid or 0)..',\"shareType\":'..tostring(pt.shareType or 0)..'}')",
  "local p={}",
  "p[#p+1]='\"seq\":'..seq",
  "p[#p+1]='\"open\":true'",
  "p[#p+1]='\"x\":'..tostring(pt.x or 0)",
  "p[#p+1]='\"y\":'..tostring(pt.y or 0)",
  "p[#p+1]='\"sid\":'..tostring(pt.sid or 0)",
  "p[#p+1]='\"shareType\":'..tostring(pt.shareType or 0)",
  // Resolve a readable name + category. shareType=1 is a catch-all map object whose kind is
  // encoded in nameKey=#FN#Table@<Table>#<id>#<field>; Config[Table][id][field] holds the
  // already-localized text (monster/resource/rally/etc.). Player cities carry pt.name directly.
  "local dn=nil local cat=nil",
  "if pt.name then dn=pt.name cat='player'",
  "elseif pt.truckName then dn=pt.truckName cat='truck'",
  "elseif pt.nameKey then local nk=tostring(pt.nameKey)",
  "local tb,id,fl=string.match(nk,'Table@(%w+)#(%d+)#(%w+)')",
  "if tb and id then cat=tb local row=_G.Config and _G.Config[tb] and _G.Config[tb][tonumber(id)] if type(row)=='table' then dn=row[fl] or row.name or row.name2 end end",
  "if not dn then dn=nk end end",
  "if dn then p[#p+1]='\"name\":\"'..esc(dn)..'\"' end",
  "if cat then p[#p+1]='\"cat\":\"'..esc(cat)..'\"' end",
  "if pt.lv then p[#p+1]='\"lv\":'..tostring(pt.lv) end",
  "if pt.qualityType then p[#p+1]='\"qualityType\":'..tostring(pt.qualityType) end",
  "if pt.playerName then p[#p+1]='\"playerName\":\"'..esc(pt.playerName)..'\"' end",
  "if pt.secretTaskId then p[#p+1]='\"secretTaskId\":'..tostring(pt.secretTaskId)",
  "local C=_G.Config local st=C and C.SecretTask and C.SecretTask[pt.secretTaskId]",
  "if type(st)=='table' then",
  "if st.quality then p[#p+1]='\"grade\":'..tostring(st.quality) end",
  "if st.secretLevel then p[#p+1]='\"stars\":'..tostring(st.secretLevel) end",
  "end end",
  "wr('{'..table.concat(p,',')..'}')",
  "local gm=_G.GlobalMapCtrlManager local wm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wm then local ok,u=pcall(function() return wm:GetDynamicUnitDataByCell(pt.x,pt.y) end)",
  "if ok and type(u)=='table' then",
  "if u.level and not pt.lv then p[#p+1]='\"lv\":'..tostring(u.level) end",
  "if u.playerPower then p[#p+1]='\"power\":'..tostring(u.playerPower) end",
  "if u.killEnemyCount then p[#p+1]='\"kills\":'..tostring(u.killEnemyCount) end",
  "if u.playerUnionShortName and not pt.playerName then p[#p+1]='\"union\":\"'..esc(u.playerUnionShortName)..'\"' end",
  "_G.__sr_seq=_G.__sr_seq+1",
  "p[1]='\"seq\":'.._G.__sr_seq",
  "wr('{'..table.concat(p,',')..'}')",
  "end end",
  "end end) return r end",
  "local ox=idx.OnExit idx.__sr_ox=ox",
  "idx.OnExit=function(self,...) pcall(function() if isShare(self.paramTable) then _G.__sr_seq=_G.__sr_seq+1 wr('{\"seq\":'.._G.__sr_seq..',\"open\":false}') end end) return ox(self,...) end",
  "_G.__sr_share_inst=true",
  "end",
  "local g=io.open(OK,'w') if g then g:write('ok') g:close() end",
  "end)",
].join(' ');

let shareHookOk = false;
let lastShareInstallAt = 0;
let lastShareText = '';

function maybeInstallShareHook() {
  if (shareHookOk) return;
  const ok = readFileUtf8(SHARE_OK_FILE);
  if (ok && ok.indexOf('ok') >= 0) {
    shareHookOk = true;
    log('share hook installed (confirmed)');
    return;
  }
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (now - lastShareInstallAt < 500) return;
  lastShareInstallAt = now;
  runLua(SHARE_HOOK_LUA);
}

function sendShareBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) {
    log('share broadcast skipped: Java bridge unavailable');
    return;
  }
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) {
        log('share broadcast skipped: no currentApplication');
        return;
      }
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(SHARE_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000); // FLAG_RECEIVER_FOREGROUND
      ctx.sendBroadcast(intent);
      log('share broadcast -> ' + SHARE_APP_PKG);
    });
  } catch (e) {
    log('share broadcast failed: ' + e);
  }
}

function pollShareFile() {
  try {
    const text = readFileUtf8(SHARE_FILE);
    if (!text || !text.trim() || text === lastShareText) return;
    lastShareText = text;
    log('share payload: ' + text.trim());
    sendShareBroadcast(text.trim());
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('share poll error: ' + e);
    }
  }
}

setImmediate(function () {
  let proc = '?';
  try {
    proc = readFileUtf8('/proc/self/cmdline', 256).replace(/\0/g, ' ').trim();
  } catch (e) {}
  log('bridge started pid=' + Process.id + ' proc=' + proc);
  clearTriggerFiles();
  writeFileEmpty(SHARE_FILE);
  writeFileEmpty(SHARE_OK_FILE);
  shareHookOk = false;
  lastShareText = '';
  mapsDiagOnce();
  setInterval(function () {
    logLibStatusOnce();
    pollTriggerFile();
    pollProbeFile();
    pollAutoHelpConfig();
    tickAutoHelp();
    maybeInstallShareHook();
    pollShareFile();
    if (libBase() && pendingFlies.length) scheduleDrainPendingFlies();
  }, 400);
  setInterval(pollShareFile, 100);
});

setInterval(function () {}, 5000);
