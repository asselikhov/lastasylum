/**
 * Last Asylum map fly bridge (com.phs.global v1.0.81).
 * SquadRelay broadcast -> MapFlyReceiver -> trigger file -> this script.
 */
'use strict';

// Frida 17 removed the global `Java` bridge from gadget scripts; import it explicitly.
// This file is bundled with frida-compile before being embedded in the APK.
import Java from 'frida-java-bridge';

// Bump on bridge logic changes; logged at startup to confirm the deployed build.
const BRIDGE_VERSION = '79';
const AUTOASSAULT_SCAN_ERR_FILE = '/data/data/com.phs.global/files/squadrelay_aa_scan_err.txt';
const AUTOASSAULT_SCAN_ERR_FILE_LUA = "'" + AUTOASSAULT_SCAN_ERR_FILE + "'";
const AUTOASSAULT_SCAN_DIAG_FILE = '/data/data/com.phs.global/files/squadrelay_aa_scan_diag.txt';
const AUTOASSAULT_SCAN_DIAG_FILE_LUA = "'" + AUTOASSAULT_SCAN_DIAG_FILE + "'";
const LIB = 'libil2cpp.so';
const TRIGGER_FILE = '/data/data/com.phs.global/files/squadrelay_map_fly.json';
const TRIGGER_SDCARD = '/sdcard/Download/squadrelay_map_fly.json';
const PROBE_FILE = '/data/data/com.phs.global/files/squadrelay_probe.json';
const LOG = '/data/data/com.phs.global/files/la_map_fly_bridge.log';
const LOG_SDCARD = '/sdcard/Download/la_map_fly_bridge.log';

// Открытие личного чата с игроком: приложение пишет триггер {playerId,...}, мост открывает
// в игре приватный чат с этим игроком (RVA/Lua подбирается по результатам реверса чат-модуля).
const OPEN_CHAT_FILE = '/data/data/com.phs.global/files/squadrelay_open_chat.json';
const OPEN_CHAT_SDCARD = '/sdcard/Download/squadrelay_open_chat.json';
const CHAT_RE_DUMP = '/sdcard/Download/squadrelay_chat_re.txt';

// Телепорт территории (города): direct = WorldCityRelocateC2S, alliance = RequestRallyPointRelocateC2S.
const CITY_RELOCATE_FILE = '/data/data/com.phs.global/files/squadrelay_city_relocate.json';
const CITY_RELOCATE_SDCARD = '/sdcard/Download/squadrelay_city_relocate.json';

// Пункт сбора альянса (Data.AllianceData.rallyPoint.point) → приложение для оверлея «Перемещение».
const ALLIANCE_RALLY_FILE = '/data/data/com.phs.global/files/squadrelay_alliance_rally.json';
const ALLIANCE_RALLY_FILE_LUA = "'" + ALLIANCE_RALLY_FILE + "'";
const ALLIANCE_RALLY_ACTION = 'com.lastasylum.alliance.action.ALLIANCE_RALLY';
const ALLIANCE_RALLY_PULSE_FILE = '/data/data/com.phs.global/files/squadrelay_alliance_rally_pulse.json';
const ALLIANCE_RALLY_PULSE_SDCARD = '/sdcard/Download/squadrelay_alliance_rally_pulse.json';

// Остаток предметов перемещения в инвентаре (Data.ItemData.itemsCount).
const RELOCATE_ITEMS_FILE = '/data/data/com.phs.global/files/squadrelay_relocate_items.json';
const RELOCATE_ITEMS_FILE_LUA = "'" + RELOCATE_ITEMS_FILE + "'";
const RELOCATE_ITEMS_ACTION = 'com.lastasylum.alliance.action.RELOCATE_ITEMS';
const RELOCATE_ITEMS_PULSE_FILE = '/data/data/com.phs.global/files/squadrelay_relocate_items_pulse.json';
const RELOCATE_ITEMS_PULSE_SDCARD = '/sdcard/Download/squadrelay_relocate_items_pulse.json';
const RELOCATE_ITEMS_SCAN_INTERVAL_MS = 3000;
const RELOCATE_ITEMS_REBROADCAST_MS = 15000;

// Канал произвольного Lua для отладки/реверса: приложение/adb пишет Lua-код в файл, мост его
// выполняет на main-thread (код сам пишет результат через io.open). Срабатывает только при
// наличии непустого файла; после выполнения файл очищается.
const EVAL_FILE = '/data/data/com.phs.global/files/squadrelay_eval.lua';
const EVAL_SDCARD = '/sdcard/Download/squadrelay_eval.lua';

// "В рейд" share bridge: Lua hook writes target payload to a game-private file on
// share-panel open/close; this script polls it and broadcasts to the SquadRelay app.
const SHARE_FILE = '/data/data/com.phs.global/files/squadrelay_share.json';
const SHARE_OK_FILE = '/data/data/com.phs.global/files/squadrelay_share_hook.ok';
const SHARE_CLOSE_FILE = '/data/data/com.phs.global/files/squadrelay_share_close.json';
const SHARE_ACTION = 'com.lastasylum.alliance.action.SHARE_TARGET';
const SHARE_APP_PKG = 'com.lastasylum.alliance';

// Закладки: хук игрового окна «Добавить тег» (Logic.UI.Panel.Collect.SearchCollectPanel).
// При открытии окна пишем payload цели (та же форма, что у шаринга) в приватный файл игры;
// JS читает его и шлёт broadcast BOOKMARK_TARGET в SquadRelay, который показывает свою
// панель-полоску над игровым окном. Сохранение в закладки делает уже сторона приложения.
const BOOKMARK_FILE = '/data/data/com.phs.global/files/squadrelay_bookmark.json';
const BOOKMARK_OK_FILE = '/data/data/com.phs.global/files/squadrelay_bookmark_hook.ok';
const BOOKMARK_ACTION = 'com.lastasylum.alliance.action.BOOKMARK_TARGET';

// Панель «Перемещение» при тапе по пустой клетке карты (WorldCityRelocationPosWin).
const RELOC_PANEL_FILE = '/data/data/com.phs.global/files/squadrelay_reloc_panel.json';
const RELOC_PANEL_SDCARD = '/sdcard/Download/squadrelay_reloc_panel.json';
const RELOC_PANEL_OK_FILE = '/data/data/com.phs.global/files/squadrelay_reloc_panel_hook.ok';
const RELOC_PANEL_ACTION = 'com.lastasylum.alliance.action.MAP_RELOC_PANEL';
const ROUTE_MAP_CLICK_FILE = '/data/data/com.phs.global/files/squadrelay_route_map_click.json';
const ROUTE_MAP_CLICK_ACTION = 'com.lastasylum.alliance.action.MAP_RELOC_ROUTE_CLICK';
const ROUTE_OFFICER_FILE = '/data/data/com.phs.global/files/squadrelay_route_officer.json';

// Режим прокладки точки маршрута (3×3 без расхода предмета).
const ROUTE_PLACEMENT_FILE = '/data/data/com.phs.global/files/squadrelay_route_placement.json';
const ROUTE_PLACEMENT_SDCARD = '/sdcard/Download/squadrelay_route_placement.json';
const ROUTE_PLACEMENT_RESULT_FILE = '/data/data/com.phs.global/files/squadrelay_route_placement_result.json';
const ROUTE_PLACEMENT_ACTION = 'com.lastasylum.alliance.action.ROUTE_PLACEMENT';

// Метки точек маршрута на карте (JSON от приложения).
const ROUTE_MARKERS_FILE = '/data/data/com.phs.global/files/squadrelay_route_markers.json';
const ROUTE_MARKERS_SDCARD = '/sdcard/Download/squadrelay_route_markers.json';
const ROUTE_MARKERS_PULSE_FILE = '/data/data/com.phs.global/files/squadrelay_route_markers_pulse.json';
const ROUTE_MARKERS_PULSE_SDCARD = '/sdcard/Download/squadrelay_route_markers_pulse.json';

const BOOKMARK_HOOK_LUA = [
  'pcall(function()',
  "pcall(function() require('Logic.UI.Panel.Collect.SearchCollectPanel') end)",
  'local pl=package.loaded',
  "local pcls=pl['Logic.UI.Panel.Collect.SearchCollectPanel']",
  'if not pcls then return end',
  'local idx=(getmetatable(pcls) or {}).__index or pcls',
  "local F='/data/data/com.phs.global/files/squadrelay_bookmark.json'",
  "local OK='/data/data/com.phs.global/files/squadrelay_bookmark_hook.ok'",
  "local function esc(s) return (string.gsub(tostring(s),'\"',\"'\")) end",
  "local function wr(t) local f=io.open(F,'w') if f then f:write(t) f:close() end end",
  // paramTable[2] = { point={x,y,sid}, unitData={...} } — цель, по которой открыли окно тегов.
  'local function bmpt(self) local d=self and self.paramTable and self.paramTable[2] if type(d)~=\'table\' then return nil end local pt=d.point if type(pt)~=\'table\' or pt.x==nil or pt.y==nil then return nil end return d,pt end',
  // Верх игрового окна в px от верха экрана — для позиции нашей панели-полоски.
  'local function calcTop(self)',
  'local ok,y=pcall(function()',
  'local go=self.gameObject if not go then return nil end',
  'local roots=CS.UIRoot.list if not roots or roots.Count<1 then return nil end',
  'local manual=roots[0].manualHeight local sh=CS.UnityEngine.Screen.height',
  'local py=go.transform.position.y',
  'if manual and manual>0 and py then return math.floor((1-py/manual)*sh) end return nil end)',
  'return ok and y or nil end',
  "local function full(t) local s='' if type(t)=='table' then local n=0 for k,v in pairs(t) do local tv=type(v) local vv if tv=='table' then vv='{t}' elseif tv=='function' then vv='fn' elseif tv=='userdata' then vv='ud' else vv=tostring(v) end if #vv>60 then vv=string.sub(vv,1,60) end s=s..tostring(k)..'='..vv..';' n=n+1 if n>120 then break end end end return (string.gsub(s,'[\\r\\n\"]',' ')) end",
  // Имя/тип/мощь/киллы из unitData окна тегов. Город игрока — всё прямо тут; сундук/босс/моб/
  // ресурс/мехагород — через тип + Config (мирроринг enrich шеринга). Иначе — best-effort.
  'local function enrich(ud,p)',
  "if type(ud)~='table' then return end",
  'local C=_G.Config',
  'local cat=nil local name=nil',
  "local function addpow(v) v=tonumber(v) if v and v>0 then p[#p+1]='\"power\":'..string.format('%.0f',v) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end end",
  "local function cfgName(tb,id) if not (tb and id) then return nil end local row=C and C[tb] and C[tb][id] if type(row)=='table' then return row.name or row.name2,row end return nil end",
  // Короткое имя альянса по unionId (сундуки/тайники не несут occupierUnionShortName).
  // Сначала своё объединение, затем кандидаты-геттеры мирового менеджера; via= для диагностики.
  "local function usn(uid) uid=tonumber(uid) if not uid or uid==0 then return nil,'noid' end local res=nil local via=''",
  "pcall(function() local ad=_G.Data and _G.Data.AllianceData if ad then local oid=ad.unionId or ad.id or ad.unionDbId if oid and tonumber(oid)==uid then res=ad.shortName or ad.alias or ad.simpleName via='own' end end end)",
  "if res and tostring(res)~='' then return res,via end",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local cands={'GetUnionSimpleInfoById','GetUnionSimpleInfo','GetUnionInfoById','GetUnionInfo','GetUnionShortNameById','GetUnionShortName','GetUnionDataById','GetUnionData'} for _,m in ipairs(cands) do if not (res and tostring(res)~='') then pcall(function() local f=wmm[m] if type(f)=='function' then local r=f(wmm,uid) if type(r)=='table' then res=r.shortName or r.unionShortName or r.alias or r.simpleName elseif type(r)=='string' then res=r end if res and tostring(res)~='' then via=m end end end) end end end",
  "if res and tostring(res)~='' then return res,via end return nil,'miss' end",
  // 1) Город игрока
  "if ud.playerName or ud.playerId then cat='player'",
  "local nick=ud.playerName and tostring(ud.playerName) or ''",
  "local tag=ud.playerUnionShortName and tostring(ud.playerUnionShortName) or ''",
  "if tag~='' then name='['..tag..'] '..nick else name=nick end",
  "if ud.playerUnionShortName then p[#p+1]='\"union\":\"'..esc(ud.playerUnionShortName)..'\"' end",
  "if ud.playerPower and ud.playerPower>0 then p[#p+1]='\"power\":'..string.format('%.0f',ud.playerPower) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end",
  "if ud.killEnemyCount and ud.killEnemyCount>0 then p[#p+1]='\"kills\":'..string.format('%.0f',ud.killEnemyCount) p[#p+1]='\"killsIcon\":\"pic_jisha\"' end",
  // 2) Сундук / тайник (secret base) — рендерится как «Сундук» + грейд/звёзды/владелец.
  'elseif ud.taskId then',
  "p[#p+1]='\"secretTaskId\":'..tostring(ud.taskId)",
  "if ud.occupierName and tostring(ud.occupierName)~='' then p[#p+1]='\"playerName\":\"'..esc(ud.occupierName)..'\"' end",
  "local usnv=nil local usnvia='none' if ud.occupierUnionShortName and tostring(ud.occupierUnionShortName)~='' then usnv=ud.occupierUnionShortName usnvia='unit' else usnv,usnvia=usn(ud.occupierUnionId) end",
  "if usnv and tostring(usnv)~='' then p[#p+1]='\"union\":\"'..esc(usnv)..'\"' end",
  // Дискавери: поля своего альянса + какие union-методы есть у мирового менеджера.
  "local adump='' pcall(function() local ad=_G.Data and _G.Data.AllianceData if ad then adump='AD[' for _,k in ipairs({'unionId','id','unionDbId','shortName','alias','simpleName','name'}) do local v=ad[k] if v~=nil then adump=adump..k..'='..tostring(v)..';' end end adump=adump..']' end end)",
  "local wmeth='' pcall(function() local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager() if type(wmm)=='table' then for k,v in pairs(wmm) do local ks=tostring(k) if type(v)=='function' and (string.find(ks,'nion') or string.find(ks,'NION')) then wmeth=wmeth..ks..',' end end end end)",
  "p[#p+1]='\"udiag\":\"uid='..tostring(ud.occupierUnionId)..' via='..esc(tostring(usnvia))..' '..esc(adump)..' WM{'..esc(wmeth)..'}\"'",
  "local st=C and C.SecretTask and C.SecretTask[ud.taskId]",
  "if type(st)=='table' then if st.quality then p[#p+1]='\"grade\":'..tostring(st.quality) end if st.secretLevel then p[#p+1]='\"stars\":'..tostring(st.secretLevel) end end",
  // 3) Ресурс
  "elseif ud.resourceId then cat='ResourceInfo' local n,row=cfgName('ResourceInfo',ud.resourceId) name=n if row then addpow(row.recAbility) end",
  // 4) Босс/ралли
  "elseif ud.rallyId then cat='SlgRallyInfo' name=cfgName('SlgRallyInfo',ud.rallyId) addpow(ud.ability)",
  // 5) Монстр
  "elseif ud.monsterId then cat='SlgMonsterInfo' name=cfgName('SlgMonsterInfo',ud.monsterId) addpow(ud.ability)",
  // 6) Мехагород
  "elseif ud.mechaCityCfg or ud.mechaCityProtoInfo then cat='MechCity' if type(ud.mechaCityCfg)=='table' then name=ud.mechaCityCfg.name or ud.mechaCityCfg.name2 end",
  'end',
  "if name and tostring(name)~='' then p[#p+1]='\"name\":\"'..esc(name)..'\"' end",
  "if cat then p[#p+1]='\"cat\":\"'..esc(cat)..'\"' end",
  "if ud.level and ud.level>0 then p[#p+1]='\"lv\":'..tostring(ud.level) end",
  "p[#p+1]='\"diag\":\"'..esc(full(ud))..'\"'",
  'end',
  'local function publish(self,open)',
  'local d,pt=bmpt(self) if not d then return end',
  '_G.__bm_seq=(_G.__bm_seq or 0)+1 local seq=_G.__bm_seq',
  "if not open then wr('{\"seq\":'..seq..',\"open\":false}') return end",
  'local p={}',
  "p[#p+1]='\"seq\":'..seq p[#p+1]='\"open\":true'",
  "p[#p+1]='\"x\":'..tostring(pt.x or 0) p[#p+1]='\"y\":'..tostring(pt.y or 0) p[#p+1]='\"sid\":'..tostring(pt.sid or 0)",
  "p[#p+1]='\"shareType\":1'",
  'enrich(d.unitData,p)',
  "if type(d.unitData)=='table' and d.unitData.taskId then p[#p+1]='\"pdiag\":\"'..esc(full(d))..'\"' end",
  "local top=calcTop(self) if top and top>0 then p[#p+1]='\"dialogTopPx\":'..tostring(top) end",
  "wr('{'..table.concat(p,',')..'}')",
  'end',
  // Per-idx guard (see share hook): re-arms after a Lua state reset / class reload.
  "if rawget(idx,'__bm_oe')==nil then",
  '_G.__bm_seq=_G.__bm_seq or 0',
  'local oe=idx.OnEnter idx.__bm_oe=oe or false',
  'idx.OnEnter=function(self,...) local r if oe then r=oe(self,...) end pcall(function() if bmpt(self) then _G.__bm_panel=self publish(self,true) end end) return r end',
  'local ox=idx.OnExit idx.__bm_ox=ox or false',
  'idx.OnExit=function(self,...) pcall(function() if bmpt(self) then _G.__bm_panel=nil publish(self,false) end end) if ox then return ox(self,...) end end',
  'end',
  "local g=io.open(OK,'w') if g then g:write('ok') g:close() end",
  'end)',
].join(' ');

const RELOC_PANEL_HOOK_LUA = [
  'pcall(function()',
  "local F='/data/data/com.phs.global/files/squadrelay_reloc_panel.json'",
  "local S='/sdcard/Download/squadrelay_reloc_panel.json'",
  "local OK='/data/data/com.phs.global/files/squadrelay_reloc_panel_hook.ok'",
  "local ROUTE_CLICK_F='/data/data/com.phs.global/files/squadrelay_route_map_click.json'",
  "local OFFICER_F='/data/data/com.phs.global/files/squadrelay_route_officer.json'",
  "local OFFICER_S='/sdcard/Download/squadrelay_route_officer.json'",
  'local function pt(self)',
  '  local p=nil',
  '  if self and self.paramTable then',
  '    for i=1,4 do',
  '      local a=self.paramTable[i]',
  '      if type(a)=="table" then',
  '        local q=a.point or (a.x and a.y and a) or nil',
  '        if type(q)=="table" and q.x~=nil and q.y~=nil then p=q break end',
  '      end',
  '    end',
  '  end',
  '  if self then p=p or self.point or self.cellPos or self.targetPoint end',
  '  if type(p)~="table" or p.x==nil or p.y==nil then return nil end return p end',
  'local function resolveSid(p,x,y,sid)',
  '  local s=tonumber(sid)',
  '  if s and s>0 then return s end',
  '  if type(p)=="table" then s=tonumber(p.sid) end',
  '  if s and s>0 then return s end',
  '  pcall(function()',
  '    local pd=_G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    if pd then s=tonumber(pd.serverId or pd.sid or pd.worldServerId or pd.worldId) end',
  '    if (not s or s<=0) and _G.GlobalMapCtrlManager then',
  '      local gm=_G.GlobalMapCtrlManager',
  '      if gm.GetCurServerId then s=tonumber(gm:GetCurServerId()) end',
  '    end',
  '  end)',
  '  return s and s>0 and s or 0',
  'end',
  'local function rectPx(w)',
  '  local ok,l,t,r,b=pcall(function()',
  '    if not w then return end',
  '    local corners=w.worldCorners',
  '    if not corners or corners.Length<4 then return end',
  '    local cam=CS.UICamera.currentCamera',
  '    if (not cam) or cam.isNull then return end',
  '    local minx,maxx=1e9,-1e9 local miny,maxy=1e9,-1e9',
  '    for i=0,3 do',
  '      local sp=cam:WorldToScreenPoint(corners[i])',
  '      minx=math.min(minx,sp.x) maxx=math.max(maxx,sp.x)',
  '      miny=math.min(miny,sp.y) maxy=math.max(maxy,sp.y)',
  '    end',
  '    local sh=CS.UnityEngine.Screen.height',
  '    return math.floor(minx),math.floor(sh-maxy),math.floor(maxx),math.floor(sh-miny)',
  '  end)',
  '  if not ok or not l then return nil end return l,t,r,b',
  'end',
  'local function labelMatch(tx)',
  '  if not tx or tx=="" then return false end',
  '  local low=string.lower(tostring(tx))',
  '  if string.find(low,"перемещ",1,true) then return true end',
  '  if string.find(low,"перемест",1,true) then return true end',
  '  if string.find(low,"reloc",1,true) then return true end',
  '  if string.find(low,"move",1,true) then return true end',
  '  return false',
  'end',
  'local function findRelocBtnByGeometry()',
  '  local best,bw=nil,0',
  '  pcall(function()',
  '    local roots=CS.UIRoot.list',
  '    if not roots then return end',
  '    for ri=0,roots.Count-1 do',
  '      local go=roots[ri] and roots[ri].gameObject',
  '      if go then',
  '        local widgets=go:GetComponentsInChildren(typeof(CS.UIWidget),true)',
  '        if widgets then',
  '          for i=0,widgets.Length-1 do',
  '            local wg=widgets[i]',
  '            if wg and wg.gameObject and wg.gameObject.activeInHierarchy then',
  '              local nm=string.lower(tostring(wg.gameObject.name or ""))',
  '              local skip=string.find(nm,"hero",1,true) or string.find(nm,"mail",1,true) or string.find(nm,"bag",1,true)',
  '              if not skip then',
  '                local ww=wg.width or 0 local hh=wg.height or 0',
  '                if ww>=70 and ww<=420 and hh>=28 and hh<=180 then',
  '                  best,bw=pickBtn(best,bw,wg)',
  '                end',
  '              end',
  '            end',
  '          end',
  '        end',
  '      end',
  '    end',
  '  end)',
  '  return best',
  'end',
  'local function pickBtn(best,bw,w)',
  '    if not w then return best,bw end',
  '    local l,t,r,b=rectPx(w)',
  '    if not l then return best,bw end',
  '    local ww=r-l',
  '    if ww>bw then return w,ww end',
  '    return best,bw',
  '  end',
  'local function scoreBtnWidget(w)',
  '    if not w or not w.gameObject or not w.gameObject.activeInHierarchy then return -1 end',
  '    local l,t,r,b=rectPx(w)',
  '    if not l then return -1 end',
  '    local ww,hh=r-l,b-t',
  '    if ww<50 or ww>450 or hh<20 or hh>200 then return -1 end',
  '    local score=ww',
  '    pcall(function()',
  '      local nm=string.lower(tostring(w.gameObject.name or ""))',
  '      if string.find(nm,"btn",1,true) or string.find(nm,"move",1,true) or string.find(nm,"green",1,true) then score=score+500 end',
  '      if w.gameObject:GetComponent(typeof(CS.UIButton)) then score=score+300 end',
  '      local el=CS.UIEventListener.Get(w.gameObject)',
  '      if el and el.onClick then score=score+200 end',
  '    end)',
  '    return score',
  '  end',
  'local function pickBtnScored(best,bs,w)',
  '    local sc=scoreBtnWidget(w)',
  '    if sc<0 then return best,bs end',
  '    if sc>bs then return w,sc end',
  '    return best,bs',
  '  end',
  'local function findRelocBtn(self)',
  '  local best,bw=nil,0',
  '  if type(self)=="table" then',
  '    for _,n in ipairs({"btnMove","btnRelocation","BtnMove","moveBtn","btnGreen","btnConfirm","btnOk","Button"}) do',
  '      if self[n] then best,bw=pickBtn(best,bw,self[n]) end',
  '    end',
  '    pcall(function()',
  '      local go=self.gameObject',
  '      if not go then return end',
  '      local labels=go:GetComponentsInChildren(typeof(CS.UILabel),true)',
  '      if not labels then return end',
  '      for i=0,labels.Length-1 do',
  '        local lb=labels[i]',
  '        if lb and labelMatch(lb.text) then',
  '          local wg=lb.gameObject:GetComponent(typeof(CS.UIWidget))',
  '          if wg then best,bw=pickBtn(best,bw,wg) end',
  '        end',
  '      end',
  '    end)',
  '  end',
  '  return best',
  'end',
  'local function pickBtnNg(best,bw,w)',
  '    if not w then return best,bw end',
  '    local ww=w.width or 0',
  '    if ww>bw then return w,ww end',
  '    return best,bw',
  '  end',
  'local function findRelocBtnFromTileMenu()',
  '  local best,bw,early=nil,0,nil',
  '  pcall(function()',
  '    local tileGo=nil',
  '    local posGo=CS.UnityEngine.GameObject.Find("m_pos_txt")',
  '    if posGo and (not posGo.isNull) and posGo.activeInHierarchy then',
  '      local tr=posGo.transform',
  '      while tr do',
  '        local nm=tostring(tr.gameObject.name or "")',
  '        if string.find(nm,"WorldUnitMenuPanel",1,true) then tileGo=tr.gameObject break end',
  '        tr=tr.parent',
  '      end',
  '    end',
  '    if not tileGo then',
  '      for _,pn in ipairs({"WorldUnitMenuPanel_Tile(Clone)","WorldUnitMenuPanel_Tile"}) do',
  '        local g=CS.UnityEngine.GameObject.Find(pn)',
  '        if g and (not g.isNull) and g.activeInHierarchy then tileGo=g break end',
  '      end',
  '    end',
  '    if not tileGo then return end',
  '    local menuTr=tileGo.transform:Find("menu")',
  '    local btnRoot=menuTr and menuTr:Find("m_buttons")',
  '    if btnRoot then',
  '      for i=0,btnRoot.childCount-1 do',
  '        local ch=btnRoot:GetChild(i)',
  '        if ch and ch.gameObject and ch.gameObject.activeInHierarchy then',
  '          local nm=string.lower(tostring(ch.gameObject.name or ""))',
  '          if not string.find(nm,"squadrelay",1,true) then',
  '            local wg=ch.gameObject:GetComponent(typeof(CS.UIWidget))',
  '            if wg then',
  '              local lbs=ch.gameObject:GetComponentsInChildren(typeof(CS.UILabel),true)',
  '              if lbs then',
  '                for j=0,lbs.Length-1 do',
  '                  local lb=lbs[j]',
  '                  if lb and labelMatch(lb.text) then early=wg return end',
  '                end',
  '              end',
  '              local tmps=ch.gameObject:GetComponentsInChildren(typeof(CS.TextMeshProNGUI),true)',
  '              if tmps then',
  '                for j=0,tmps.Length-1 do',
  '                  local tx=tmps[j]',
  '                  if tx and labelMatch(tx.text) then early=wg return end',
  '                end',
  '              end',
  '              best,bw=pickBtnNg(best,bw,wg)',
  '            end',
  '          end',
  '        end',
  '      end',
  '    end',
  '  end)',
  '  if early then return early end',
  '  return best',
  'end',
  'local function walkItemWidgets(item)',
  '  local best,bs,early=nil,0,nil',
  '  if type(item)~="table" then return nil end',
  '  for _,n in ipairs({"btnMove","btnRelocation","BtnMove","moveBtn","btnGreen","btnConfirm","btnOk","Button","btn","button","moveButton","moveBtn","okBtn","confirmBtn"}) do',
  '    if item[n] then best,bs=pickBtnScored(best,bs,item[n]) end',
  '  end',
  '  pcall(function()',
  '    local go=item.gameObject',
  '    if not go then return end',
  '    local labels=go:GetComponentsInChildren(typeof(CS.UILabel),true)',
  '    if labels then',
  '      for i=0,labels.Length-1 do',
  '        local lb=labels[i]',
  '        if lb then',
  '          local wg=lb.gameObject:GetComponent(typeof(CS.UIWidget))',
  '          if wg then',
  '            if labelMatch(lb.text) then early=wg return end',
  '            best,bs=pickBtnScored(best,bs,wg)',
  '          end',
  '        end',
  '      end',
  '    end',
  '    if early then return end',
  '    local widgets=go:GetComponentsInChildren(typeof(CS.UIWidget),true)',
  '    if widgets then',
  '      for i=0,widgets.Length-1 do',
  '        best,bs=pickBtnScored(best,bs,widgets[i])',
  '      end',
  '    end',
  '    local par=go.transform.parent',
  '    if par then',
  '      for ci=0,par.childCount-1 do',
  '        local ch=par:GetChild(ci)',
  '        if ch and ch.gameObject and ch.gameObject~=go then',
  '          local wg=ch.gameObject:GetComponent(typeof(CS.UIWidget))',
  '          if wg then best,bs=pickBtnScored(best,bs,wg) end',
  '        end',
  '      end',
  '    end',
  '  end)',
  '  if early then return early end',
  '  return best',
  'end',
  'local function findRelocBtnFromMapView(muv)',
  '  if type(muv)~="table" then return nil end',
  '  local item=muv.CityRelocationItem',
  '  if item then',
  '    local b=walkItemWidgets(item)',
  '    if b then return b end',
  '  end',
  '  return findRelocBtn(muv)',
  'end',
  'local function findRelocBtnAny(self)',
  '  return findRelocBtnFromTileMenu() or findRelocBtnFromMapView(self) or findRelocBtnGlobal() or findRelocBtnByGeometry()',
  'end',
  'local function findRelocBtnGlobal()',
  '  local best,bw=nil,0',
  '  pcall(function()',
  '    local roots=CS.UIRoot.list',
  '    if not roots then return end',
  '    for ri=0,roots.Count-1 do',
  '      local root=roots[ri]',
  '      local go=root and root.gameObject',
  '      if go then',
  '        local labels=go:GetComponentsInChildren(typeof(CS.UILabel),true)',
  '        if labels then',
  '          for i=0,labels.Length-1 do',
  '            local lb=labels[i]',
  '            if lb and lb.isVisible and labelMatch(lb.text) then',
  '              local wg=lb.gameObject:GetComponent(typeof(CS.UIWidget))',
  '              if wg then best,bw=pickBtn(best,bw,wg) end',
  '            end',
  '          end',
  '        end',
  '      end',
  '    end',
  '  end)',
  '  return best',
  'end',
  'local function officerOk()',
  '  local enabled=true',
  '  pcall(function()',
  '    for _,p in ipairs({OFFICER_F,OFFICER_S}) do',
  '      local f=io.open(p,"r")',
  '      if f then',
  '        local s=f:read("*a") f:close()',
  '        if s and string.find(s,"\\"enabled\\"%s*:%s*false") then enabled=false return end',
  '        if s and string.find(s,"\\"enabled\\"%s*:%s*true") then enabled=true return end',
  '      end',
  '    end',
  '  end)',
  '  return enabled',
  'end',
  'local function hideRouteBtn()',
  '  pcall(function()',
  '    local go=_G.__sr_route_btn_go',
  '    if go and (not go.isNull) and go.activeSelf then go:SetActive(false) end',
  '  end)',
  'end',
  'local function bindRouteClick(go,x,y,sid)',
  '  pcall(function()',
  '    local el=CS.UIEventListener.Get(go)',
  '    el.onClick=function()',
  '      local xi=math.floor(tonumber(x) or 0) local yi=math.floor(tonumber(y) or 0)',
  '      local si=math.floor(tonumber(sid) or 0)',
  '      if xi<=0 or yi<=0 then return end',
  '      local f=io.open(ROUTE_CLICK_F,"w")',
  '      if f then f:write("{\\"x\\":"..xi..",\\"y\\":"..yi..",\\"sid\\":"..si..",\\"ts\\":"..os.time().."}") f:close() end',
  '    end',
  '  end)',
  'end',
  'local function positionRouteBtn(srcW,go)',
  '  local srcGo=srcW.gameObject local tr=go.transform',
  '  tr:SetParent(srcGo.transform.parent,false)',
  '  tr.localScale=srcGo.transform.localScale',
  '  local w=srcW.width or 100 local lp=srcGo.transform.localPosition',
  '  local gap=math.max(4,math.floor(w*0.06))',
  '  tr.localPosition=CS.UnityEngine.Vector3(lp.x+w+gap,lp.y,lp.z)',
  '  tr:SetSiblingIndex(srcGo.transform:GetSiblingIndex()+1)',
  'end',
  'local function setRouteLabel(go)',
  '  pcall(function()',
  '    local tmps=go:GetComponentsInChildren(typeof(CS.TextMeshProNGUI),true)',
  '    if tmps and tmps.Length>0 then',
  '      for i=0,tmps.Length-1 do tmps[i].text="Маршрут" end',
  '      return',
  '    end',
  '    tmps=go:GetComponentsInChildren(typeof(CS.TMPro.TextMeshProUGUI),true)',
  '    if tmps and tmps.Length>0 then',
  '      for i=0,tmps.Length-1 do tmps[i].text="Маршрут" end',
  '      return',
  '    end',
  '    local labels=go:GetComponentsInChildren(typeof(CS.UILabel),true)',
  '    if labels and labels.Length>0 then',
  '      for i=0,labels.Length-1 do',
  '        labels[i].text="Маршрут"',
  '        if labels[i].MarkAsChanged then labels[i]:MarkAsChanged() end',
  '      end',
  '    end',
  '  end)',
  'end',
  'local function syncRouteBtn(relocW,x,y,sid)',
  '  if not officerOk() then hideRouteBtn() return false end',
  '  if not relocW or not relocW.gameObject then return false end',
  '  local ok=false',
  '  pcall(function()',
  '    local srcGo=relocW.gameObject local go=_G.__sr_route_btn_go',
  '    if go and go.isNull then go=nil _G.__sr_route_btn_go=nil end',
  '    if not go then',
  '      go=CS.UnityEngine.Object.Instantiate(srcGo)',
  '      go.name="SquadRelayRouteBtn"',
  '      _G.__sr_route_btn_go=go',
  '    end',
  '    positionRouteBtn(relocW,go)',
  '    setRouteLabel(go)',
  '    go:SetActive(true)',
  '    bindRouteClick(go,x,y,sid)',
  '    ok=true',
  '  end)',
  '  return ok',
  'end',
  'local function btnColorRgb(w)',
  '  local ok,c=pcall(function()',
  '    local sp=w.sprite',
  '    if not sp then sp=w:GetComponent(typeof(CS.UISprite)) end',
  '    if sp and sp.color then',
  '      local cl=sp.color',
  '      return math.floor(cl.r*255)*65536+math.floor(cl.g*255)*256+math.floor(cl.b*255)',
  '    end',
  '  end)',
  '  return ok and c or nil',
  'end',
  'local function calcBottom(self)',
  '  local ok,y=pcall(function()',
  '    local go=self and self.gameObject if not go then return nil end',
  '    local roots=CS.UIRoot.list if not roots or roots.Count<1 then return nil end',
  '    local manual=roots[0].manualHeight local sh=CS.UnityEngine.Screen.height',
  '    local py=go.transform.position.y',
  '    if manual and manual>0 and py then return math.floor((1-py/manual)*sh)+math.floor(sh*0.08) end return nil end)',
  '  return ok and y or nil end',
  'local function wr(s)',
  '  local f=io.open(F,"w") if f then f:write(s) f:close() end',
  '  f=io.open(S,"w") if f then f:write(s) f:close() end',
  'end',
  'local function appendBtnMetrics(s,btn,self)',
  '  local b=btn or findRelocBtnAny(self)',
  '  if not b then return s end',
  '  local l,t,r,bb=rectPx(b)',
  '  if not l then return s end',
  "  s=s..',\"relocBtnLeftPx\":'..l..',\"relocBtnTopPx\":'..t..',\"relocBtnRightPx\":'..r..',\"relocBtnBottomPx\":'..bb",
  "  s=s..',\"relocBtnWidthPx\":'..(r-l)..',\"relocBtnHeightPx\":'..(bb-t)",
  '  local c=btnColorRgb(b)',
  "  if c then s=s..',\"relocBtnColorArgb\":'..c end",
  '  return s',
  'end',
  'local function publishClose()',
  '  hideRouteBtn()',
  '  _G.__rp_seq=(_G.__rp_seq or 0)+1',
  "  wr('{\"seq\":'.._G.__rp_seq..',\"open\":false}')",
  'end',
  'local function publishOpenXY(x,y,sid,uiSelf)',
  '  local xi=math.floor(tonumber(x) or 0) local yi=math.floor(tonumber(y) or 0)',
  '  if xi<=0 or yi<=0 then return end',
  '  local si=resolveSid(nil,xi,yi,sid)',
  '  _G.__rp_seq=(_G.__rp_seq or 0)+1 local seq=_G.__rp_seq',
  "  local s='{\"seq\":'..seq..',\"open\":true,\"x\":'..xi..',\"y\":'..yi..',\"sid\":'..si",
  '  local bot=uiSelf and calcBottom(uiSelf) or nil',
  "  if bot and bot>0 then s=s..',\"panelBottomPx\":'..tostring(bot) end",
  '  s=appendBtnMetrics(s,nil,uiSelf)',
  '  local reloc=findRelocBtnAny(uiSelf)',
  '  local inGame=false',
  '  pcall(function() inGame=syncRouteBtn(reloc,xi,yi,si) end)',
  "  s=s..',\"inGameRouteBtn\":'..(inGame and 'true' or 'false')",
  "  s=s..'}' wr(s)",
  'end',
  'local function publish(self,open)',
  '  if not open then publishClose() return end',
  '  local p=pt(self) if not p then return end',
  '  publishOpenXY(p.x,p.y,p.sid,self)',
  'end',
  'local function wrapLifecycle(classIdx,name)',
  '  if rawget(classIdx,"__rp_"..name)~=nil then return end',
  '  local orig=classIdx[name]',
  '  if type(orig)~="function" then return end',
  '  classIdx["__rp_"..name]=orig',
  '  classIdx[name]=function(self,...)',
  '    local r=orig(self,...)',
  '    local function pub() pcall(function() publish(self,true) end) end',
  '    pub()',
  '    pcall(function() if _G.DelayCall then _G.DelayCall(0.15,pub) elseif _G.Timer and _G.Timer.New then _G.Timer.New(0.15,pub) end end)',
  '    return r',
  '  end',
  'end',
  'local function tryInstallWinHook()',
  '  local idx=nil',
  '  for _,m in ipairs({"UIs.WorldMapUI.WorldCityRelocationPosWin","UIs.WorldMapUI.WorldCityRelocationWin"}) do',
  '    pcall(function() require(m) end)',
  '    local t=package.loaded[m]',
  '    if type(t)=="table" then idx=t break end',
  '  end',
  '  if not idx then return false end',
  '  local classIdx=(getmetatable(idx) or {}).__index or idx',
  '  _G.__rp_seq=_G.__rp_seq or 0',
  '  wrapLifecycle(classIdx,"OnEnter")',
  '  wrapLifecycle(classIdx,"OnShow")',
  '  if rawget(classIdx,"__rp_ox")==nil then',
  '    local ox=classIdx.OnExit classIdx.__rp_ox=ox or false',
  '    classIdx.OnExit=function(self,...) pcall(function() publish(self,false) end) if ox then return ox(self,...) end end',
  '  end',
  '  if type(classIdx.ShowRelocateBase)=="function" and rawget(classIdx,"__rp_srb")==nil then',
  '    local osrb=classIdx.ShowRelocateBase classIdx.__rp_srb=osrb',
  '    classIdx.ShowRelocateBase=function(self,...)',
  '      local r=osrb(self,...)',
  '      local function pub() pcall(function() publish(self,true) end) end',
  '      pub()',
  '      pcall(function() if _G.DelayCall then _G.DelayCall(0.15,pub) end end)',
  '      return r',
  '    end',
  '  end',
  '  return true',
  'end',
  'local function tryInstallMapViewHook()',
  '  local gmc=_G.GlobalMapCtrlManager',
  '  if not gmc then return false end',
  '  local wm=gmc.GetWorldManager and gmc:GetWorldManager()',
  '  local muv=wm and wm.mapUnitsView',
  '  if type(muv)~="table" then return false end',
  '  if rawget(muv,"__rp_mv") then return true end',
  '  local oShow=muv.ShowCityRelocationItem',
  '  if type(oShow)~="function" then return false end',
  '  muv.__rp_mv=true',
  '  _G.__rp_seq=_G.__rp_seq or 0',
  '  muv.ShowCityRelocationItem=function(self,x,y,sid,...)',
  '    local r=oShow(self,x,y,sid,...)',
  '    local sx,sy,ssid=x,y,sid',
  '    local tries=0 local synced=false',
  '    local function trySync()',
  '      tries=tries+1',
  '      pcall(function()',
  '        local rw=findRelocBtnAny(self)',
  '        local ok=rw and syncRouteBtn(rw,sx,sy,ssid)',
  '        if ok then synced=true end',
  '        if tries==1 or ok then publishOpenXY(sx,sy,ssid,self) end',
  '      end)',
  '      if not synced and tries<20 then',
  '        pcall(function() if _G.DelayCall then _G.DelayCall(0.15,trySync) elseif _G.Timer and _G.Timer.New then _G.Timer.New(0.15,trySync) end end)',
  '      end',
  '    end',
  '    trySync()',
  '    return r',
  '  end',
  '  local oHide=muv.HideCityRelocationItem',
  '  if type(oHide)=="function" and rawget(muv,"__rp_h")==nil then',
  '    muv.__rp_h=oHide',
  '    muv.HideCityRelocationItem=function(self,...)',
  '      hideRouteBtn()',
  '      pcall(publishClose)',
  '      return oHide(self,...)',
  '    end',
  '  end',
  '  return true',
  'end',
  'local armed=false',
  'if tryInstallMapViewHook() then armed=true end',
  'if tryInstallWinHook() then armed=true end',
  'if armed then local g=io.open(OK,"w") if g then g:write("ok") g:close() end end',
  'end)',
].join(' ');

const ROUTE_MODE_INSTALL_LUA = [
  'pcall(function()',
  "  pcall(function() require('UIs.WorldMapUI.WorldCityRelocationPosWin') end)",
  '  local idx=package.loaded["UIs.WorldMapUI.WorldCityRelocationPosWin"]',
  '  local classIdx=idx and ((getmetatable(idx) or {}).__index or idx)',
  '  if classIdx and rawget(classIdx,"__sr_cr_wrap")==nil then',
  '    local orig=classIdx.CityRelocationHandler',
  '    classIdx.__sr_cr_wrap=true',
  '    classIdx.CityRelocationHandler=function(win, rtype)',
  '      if _G.__sr_route_mode then return end',
  '      if orig then return orig(win, rtype) end',
  '    end',
  '  end',
  'end)',
].join(' ');

// Auto-help: SquadRelay writes a persistent config file; this script installs an EVENT-DRIVEN
// hook in the game so "help all" is sent the instant an alliance help request appears (same as
// tapping the in-game "Помощь" button right when it pops up), not on a fixed poll.
const AUTOHELP_FILE = '/data/data/com.phs.global/files/squadrelay_autohelp.json';
const AUTOHELP_SDCARD = '/sdcard/Download/squadrelay_autohelp.json';
const AUTOHELP_OK_FILE = '/data/data/com.phs.global/files/squadrelay_autohelp_hook.ok';
const AUTOHELP_DIAG_FILE = '/data/data/com.phs.global/files/squadrelay_autohelp_diag.txt';
const AUTOHELP_MIN_INTERVAL_MS = 5000;
const AUTOHELP_MAX_INTERVAL_MS = 600000;
// Delay before ANY background runLua (share/bookmark hooks, auto-assault, roster, auto-help).
// Running DoString on the Unity main thread during login / alliance-sync corrupts Lua loading
// (PlayerData.bytes missing → infinite loading screen). Auto-help already used 30s; v41 lowered
// assault to 2s but share/bookmark hooks had no delay at all — same failure mode.
const BRIDGE_LUA_STARTUP_DELAY_MS = 30000;
// After world-ready: hooks/auto-help wait longer (fragile during login UI). Assault scan uses
// a shorter grace so joins start sooner once playerId is known.
const BRIDGE_ASSAULT_GRACE_MS = 5000;
const BRIDGE_POST_WORLD_READY_GRACE_MS = 20000;
const WORLD_READY_FILE = '/data/data/com.phs.global/files/squadrelay_world_ready.ok';
const WORLD_READY_FILE_LUA = "'" + WORLD_READY_FILE + "'";
const WORLD_READY_PROBE_LUA =
  'pcall(function() local D=_G.Data if type(D)~="table" then return end local function mark() local f=io.open(' +
  WORLD_READY_FILE_LUA +
  ',"w") if f then f:write("1") f:close() end end local pd=D.PlayerData or D.UserData if type(pd)=="table" then local id=tonumber(pd.playerId or pd.id or pd.uid or pd.roleId) if id and id>0 then mark() return end end local pid=tonumber(_G.__sr_my_pid) if pid and pid>0 then mark() return end local ad=D.AllianceData if type(ad)=="table" and type(ad.member)=="table" then local md=ad.member.memberDic if type(md)=="table" then for k,m in pairs(md) do local p=m and m.profile if type(p)=="table" then local id=tonumber(p.id or k) if id and id>0 and (m.isSelf or p.isSelf or m.self) then _G.__sr_my_pid=id mark() return end end end end end if type(ad)=="table" and type(ad.wars)=="table" and type(ad.wars.assemblyDic)=="table" then for _ in pairs(ad.wars.assemblyDic) do mark() return end end end)';

// Event-driven auto-help. Captured live: tapping the real "Помощь" button sends `UnionHelpAllC2S`
// (plus `GetUnionHelpListC2S` to refresh the list). Instead of polling, we wrap the alliance help
// data-update methods (AddDataOne / Refresh*) on the help object's class metatable. The game calls
// these whenever the server pushes a help update (i.e. exactly when the in-game help button would
// appear / change), so our wrapper fires `UnionHelpAllC2S` in the same instant — verified live:
// after GetUnionHelpListC2S the wrapper emitted UnionHelpAllC2S we never called directly.
// Safeguards:
//  - os.time() debounce (3s) prevents a tight loop (our send → server refresh → wrapper → send).
//  - _G.__sr_help_enabled gates sending so the in-app toggle can disable it without re-patching.
//  - on first wrap we send GetUnionHelpListC2S once to catch requests already pending at login.
// We intentionally do NOT gate on the help object's IsHaveCanHelpData(): that local flag reflected
// the player's OWN already-maxed requests and was false even when help was actually available.
// A "help all" with nothing to help is a harmless server-side no-op (verified).
const AUTOHELP_INSTALL_LUA = [
  'pcall(function()',
  '  _G.__sr_help_enabled = true',
  "  local D = rawget(_G, 'Data')",
  '  local ad = D and D.AllianceData',
  '  local h = ad and ad.help',
  '  if not h then return end',
  '  local mt = getmetatable(h)',
  '  local idx = mt and mt.__index',
  "  if type(idx) ~= 'table' then return end",
  "  local sm = package.loaded['Logic.Proto.Send.union_help']",
  '  if not sm or not sm.UnionHelpAllC2S then return end',
  "  if rawget(_G, '__sr_send_help') == nil then",
  '    _G.__sr_help_last = 0',
  '    _G.__sr_send_help = function()',
  '      if not _G.__sr_help_enabled then return end',
  '      local now = os.time()',
  '      if now - (_G.__sr_help_last or 0) < 3 then return end',
  '      _G.__sr_help_last = now',
  '      pcall(function() sm.UnionHelpAllC2S() end)',
  '    end',
  '  end',
  "  local names = {'AddDataOne', 'RefreshDataMore', 'RefreshAllData', 'RefreshDataOne'}",
  '  local newly = false',
  '  for i = 1, #names do',
  '    local n = names[i]',
  "    if type(idx[n]) == 'function' and not rawget(idx, '__sr_w_' .. n) then",
  '      local orig = idx[n]',
  "      rawset(idx, '__sr_w_' .. n, true)",
  '      idx[n] = function(...)',
  '        local function after(...) pcall(_G.__sr_send_help) return ... end',
  '        return after(orig(...))',
  '      end',
  '      newly = true',
  '    end',
  '  end',
  '  if newly and sm.GetUnionHelpListC2S then pcall(function() sm.GetUnionHelpListC2S() end) end',
  "  local g=io.open('" + AUTOHELP_OK_FILE + "','w') if g then g:write('ok') g:close() end",
  'end)',
].join('\n');

// Toggle off: stop the wrappers from sending (they stay installed but become no-ops).
const AUTOHELP_DISABLE_LUA = 'pcall(function() _G.__sr_help_enabled = false end)';

const AUTOASSAULT_FILE = '/data/data/com.phs.global/files/squadrelay_autoassault.json';
const AUTOASSAULT_FILE_LUA = "'" + AUTOASSAULT_FILE + "'";
const AUTOASSAULT_SDCARD = '/sdcard/Download/squadrelay_autoassault.json';
const AUTOASSAULT_SCAN_INTERVAL_MS = 1500;
// Реальное вступление включено. Доп. защита: авто-вступление шлёт пакет только если
// для нужного teamIndex есть кэш состава (build_team != nil) — т.е. после хотя бы
// одного ручного вступления этим отрядом.
const AUTOASSAULT_JOIN_ENABLED = true;
const AUTOASSAULT_MATCH_FILE = '/data/data/com.phs.global/files/squadrelay_autoassault_match.json';
// ~17KB scan script inlined in JS; DoString aborts on large chunks — load from file instead.
const AUTOASSAULT_SCAN_FILE = '/data/data/com.phs.global/files/squadrelay_aa_scan.lua';
const AUTOASSAULT_SCAN_FILE_LUA = "'" + AUTOASSAULT_SCAN_FILE + "'";
const AUTOASSAULT_SCAN_RUN_LUA =
  'pcall(function() local lf,le=loadfile(' +
  AUTOASSAULT_SCAN_FILE_LUA +
  '); if not lf then error(tostring(le or "loadfile nil")) end lf() end)';
const AUTOASSAULT_CFG_READ_MAX = 65536;
const ASSAULT_JOIN_ACTION = 'com.lastasylum.alliance.action.ASSAULT_JOIN';
// Ростер альянса (игра → приложение): мост периодически читает memberDic и шлёт
// broadcast со списком соалийцев [{id,name,power,level,rank}] в SquadRelay, чтобы
// в настройках штурма показывать актуальный список из игры, а не из приложения.
const ALLIANCE_ROSTER_FILE = '/data/data/com.phs.global/files/squadrelay_alliance_roster.json';
const ALLIANCE_ROSTER_FILE_LUA = "'" + ALLIANCE_ROSTER_FILE + "'";
const SELF_PLAYER_FILE = '/data/data/com.phs.global/files/squadrelay_self_player.json';
const SELF_PLAYER_FILE_LUA = "'" + SELF_PLAYER_FILE + "'";
const ALLIANCE_ROSTER_ACTION = 'com.lastasylum.alliance.action.ALLIANCE_ROSTER';
const ALLIANCE_ROSTER_SCAN_INTERVAL_MS = 20000;
const ALLIANCE_ROSTER_LUA = [
  'pcall(function()',
  '  local ad = _G.Data and _G.Data.AllianceData',
  '  local md = ad and ad.member and ad.member.memberDic',
  '  if type(md) ~= "table" then return end',
  '  local q = string.char(34)',
  '  local function markSelf(m, pidKey)',
  '    local p = m and m.profile',
  '    local selfId = tonumber(p and p.id or pidKey)',
  '    if not selfId or selfId <= 0 then return end',
  '    _G.__sr_my_pid = selfId',
  '    _G.__sr_aa_my_pid = selfId',
  '    local ccSelf = m and m.cityCoords',
  '    local cx, cy, cs = 0, 0, 0',
  '    if type(ccSelf) == "table" and ccSelf.x and ccSelf.y then',
  '      cx = math.floor(tonumber(ccSelf.x))',
  '      cy = math.floor(tonumber(ccSelf.y))',
  '      cs = math.floor(tonumber(ccSelf.sid) or 0)',
  '      _G.__sr_my_castle = { x = cx, y = cy, sid = cs }',
  '    end',
  '    local nm = p and p.name and tostring(p.name) or ""',
  '    local sf = io.open(' + SELF_PLAYER_FILE_LUA + ', "w")',
  '    if sf then',
  '      local esc = nm:gsub(q, string.char(39))',
  '      sf:write("{"..q.."id"..q..":"..q..tostring(selfId)..q..","..q.."name"..q..":"..q..esc..q..","..q.."x"..q..":"..tostring(cx)..","..q.."y"..q..":"..tostring(cy)..","..q.."sid"..q..":"..tostring(cs).."}")',
  '      sf:close()',
  '    end',
  '  end',
  '  local out = {}',
  '  for pid, m in pairs(md) do',
  '    local p = m and m.profile',
  '    if type(p) == "table" and p.name then',
  '      if m.isSelf or m.self or p.isSelf or p.self then markSelf(m, pid) end',
  '      local nm = tostring(p.name):gsub(q, string.char(39)):gsub(string.char(92), " ")',
  '      local cc = m.cityCoords',
  '      local cx = (type(cc) == "table" and tonumber(cc.x)) or 0',
  '      local cy = (type(cc) == "table" and tonumber(cc.y)) or 0',
  '      local cs = (type(cc) == "table" and tonumber(cc.sid)) or (tonumber(p.serverId) or 0)',
  // «Поверженные» лежат не в member-записи (m.killNum часто пустой), а в профиле игрока —
  // перебираем известные имена поля (как killEnemyCount в шаринге «В рейд»).
  '      local kn = tonumber(p.killEnemyCount) or tonumber(p.killNum) or tonumber(p.kill) or tonumber(p.killCount) or tonumber(m.killEnemyCount) or tonumber(m.killNum) or 0',
  '      out[#out + 1] = "{"..q.."id"..q..":"..q..tostring(p.id or pid)..q..","..q.."name"..q..":"..q..nm..q..","..q.."power"..q..":"..tostring(math.floor(tonumber(p.battlePower) or 0))..","..q.."level"..q..":"..tostring(math.floor(tonumber(p.level) or 0))..","..q.."castle"..q..":"..tostring(math.floor(tonumber(m.castleLevel) or 0))..","..q.."rank"..q..":"..tostring(math.floor(tonumber(m.rank) or 0))..","..q.."kills"..q..":"..tostring(math.floor(kn))..","..q.."x"..q..":"..tostring(math.floor(cx))..","..q.."y"..q..":"..tostring(math.floor(cy))..","..q.."sid"..q..":"..tostring(math.floor(cs))..","..q.."logout"..q..":"..tostring(math.floor(tonumber(m.logoutTime) or 0)).."}"',
  '    end',
  '  end',
  '  if not (_G.__sr_my_pid and tonumber(_G.__sr_my_pid) > 0) then',
  '    local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    local myName = pd and (pd.playerName or pd.name or pd.nickName or pd.nickname)',
  '    if myName and tostring(myName) ~= "" then',
  '      for pid2, m2 in pairs(md) do',
  '        local p2 = m2 and m2.profile',
  '        if type(p2) == "table" and tostring(p2.name) == tostring(myName) then markSelf(m2, pid2) break end',
  '      end',
  '    end',
  '  end',
  '  local s = "[" .. table.concat(out, ",") .. "]"',
  '  local f = io.open(' + ALLIANCE_ROSTER_FILE_LUA + ', "w") if f then f:write(s) f:close() end',
  '  local rp = ad and ad.rallyPoint and ad.rallyPoint.point',
  '  if type(rp) ~= "table" or not rp.x or not rp.y then',
  '    local ter = ad and ad.territory',
  '    rp = ter and ter.allianceRallyWorldPoint',
  '  end',
  '  if type(rp) == "table" and rp.x and rp.y then',
  '    local rx = math.floor(tonumber(rp.x) or 0)',
  '    local ry = math.floor(tonumber(rp.y) or 0)',
  '    local rs = math.floor(tonumber(rp.sid) or 0)',
  '    local rf = io.open(' + ALLIANCE_RALLY_FILE_LUA + ', "w")',
  '    if rf then rf:write("{"..q.."x"..q..":"..tostring(rx)..","..q.."y"..q..":"..tostring(ry)..","..q.."sid"..q..":"..tostring(rs).."}") rf:close() end',
  '  end',
  'end)',
].join('\n');
// Пункт сбора альянса — только по pulse (открытие «Перемещение») или вместе с ростером.
const ALLIANCE_RALLY_LUA = [
  'pcall(function()',
  '  local q=string.char(34)',
  '  local ad = _G.Data and _G.Data.AllianceData',
  '  local rp = ad and ad.rallyPoint and ad.rallyPoint.point',
  '  if type(rp) ~= "table" or not rp.x or not rp.y then',
  '    local ter = ad and ad.territory',
  '    rp = ter and ter.allianceRallyWorldPoint',
  '  end',
  '  if type(rp) ~= "table" or not rp.x or not rp.y then',
  '    pcall(function()',
  '      local cd = _G.Data and (_G.Data.CollectData or _G.Data.WorldCollectData or _G.Data.MarkData)',
  '      if type(cd) ~= "table" then return end',
  '      local lists = { cd.allianceList, cd.unionList, cd.alliance, cd.union, cd.markDic, cd.collectDic }',
  '      for _, lst in ipairs(lists) do',
  '        if type(lst) == "table" then',
  '          for _, item in pairs(lst) do',
  '            if type(item) == "table" then',
  '              local nm = string.lower(tostring(item.name or item.markName or item.title or ""))',
  '              if string.find(nm, "сбор", 1, true) or string.find(nm, "rally", 1, true) or string.find(nm, "пункт", 1, true) then',
  '                local pt = item.point or item.pos or item.worldPoint or item',
  '                if type(pt) == "table" and pt.x and pt.y then rp = { x = pt.x, y = pt.y, sid = pt.sid or item.sid } break end',
  '              end',
  '            end',
  '          end',
  '        end',
  '        if type(rp) == "table" and rp.x and rp.y then break end',
  '      end',
  '    end)',
  '  end',
  '  if type(rp) ~= "table" or not rp.x or not rp.y then return end',
  '  local rx = math.floor(tonumber(rp.x) or 0)',
  '  local ry = math.floor(tonumber(rp.y) or 0)',
  '  local rs = math.floor(tonumber(rp.sid) or 0)',
  '  if rs <= 0 then',
  '    local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    rs = math.floor(tonumber(pd and (pd.serverId or pd.sid or pd.server)) or 0)',
  '  end',
  '  if rx <= 0 or ry <= 0 then return end',
  '  local rf = io.open(' + ALLIANCE_RALLY_FILE_LUA + ', "w")',
  '  if rf then rf:write("{"..q.."x"..q..":"..tostring(rx)..","..q.."y"..q..":"..tostring(ry)..","..q.."sid"..q..":"..tostring(rs).."}") rf:close() end',
  'end)',
].join('\n');
// Счётчики предметов перемещения: itemsCount + Config.Item/itemDic (VIP-магазин может не попадать в один ключ).
const RELOCATE_ITEMS_LUA = [
  'pcall(function()',
  '  local id = _G.Data and _G.Data.ItemData',
  '  if type(id) ~= "table" then return end',
  '  local ic = id.itemsCount',
  '  if type(ic) ~= "table" then return end',
  '  local cfg = _G.Config and _G.Config.Item',
  '  local function n(v) return math.max(0, math.floor(tonumber(v) or 0)) end',
  '  local function ls(s) return string.lower(tostring(s)) end',
  '  local function sumIc(pred)',
  '    local t = 0',
  '    for k, v in pairs(ic) do if pred(ls(k)) then t = t + n(v) end end',
  '    return t',
  '  end',
  '  local function isDirectKey(k)',
  '    if string.find(k, "random", 1, true) or string.find(k, "union", 1, true) then return false end',
  '    return k == "item_useup_targetmove" or string.find(k, "targetmove", 1, true) or string.find(k, "directmove", 1, true)',
  '  end',
  '  local function isDirectCfg(ut, ck)',
  '    if isDirectKey(ut) or isDirectKey(ck) then return true end',
  '    return false',
  '  end',
  '  local function isAllianceKey(k) return string.find(k, "unionmove", 1, true) ~= nil end',
  '  local function isRandomKey(k) return string.find(k, "randommove", 1, true) ~= nil end',
  '  local direct = sumIc(isDirectKey)',
  '  local alliance = sumIc(isAllianceKey)',
  '  local random = sumIc(isRandomKey)',
  '  if type(cfg) == "table" then',
  '    for k, v in pairs(ic) do',
  '      local cid = tonumber(k)',
  '      if cid then',
  '        local row = cfg[cid] or cfg[tostring(cid)]',
  '        if type(row) == "table" and isDirectCfg(ls(row.useUpType or row.useType or row.effectType or ""), ls(cid)) then',
  '          direct = direct + n(v)',
  '        end',
  '      end',
  '    end',
  '    local dic = id.itemDic or id.itemsDic or id.bagDic',
  '    if type(dic) == "table" then',
  '      local bag = 0',
  '      for k, e in pairs(dic) do',
  '        local cid = k',
  '        if type(e) == "table" then cid = e.itemId or e.id or e.cfgId or e.configId or k end',
  '        local row = cfg[cid] or cfg[tostring(cid)] or cfg[tonumber(cid)]',
  '        if type(row) == "table" and isDirectCfg(ls(row.useUpType or row.useType or row.effectType or ""), ls(cid)) then',
  '          local num = 0',
  '          if type(e) == "table" then num = n(e.num or e.count or e.itemNum or e.amount) else num = n(e) end',
  '          bag = bag + num',
  '        end',
  '      end',
  '      if bag > direct then direct = bag end',
  '    end',
  '  end',
  '  local gic = id.GetItemCount or id.GetItemNum or id.getItemCount',
  '  if type(gic) == "function" then',
  '    local api = 0',
  '    for _, key in ipairs({',
  '      "item_UseUp_targetMove", "item_UseUp_vipTargetMove", "item_vip_targetMove",',
  '      "item_UseUp_DirectMove", "item_UseUp_directMove",',
  '    }) do',
  '      local ok, c = pcall(gic, id, key)',
  '      if ok then api = api + n(c) end',
  '    end',
  '    if api > direct then direct = api end',
  '  end',
  '  local q = string.char(34)',
  '  local f = io.open(' + RELOCATE_ITEMS_FILE_LUA + ', "w")',
  '  if f then',
  '    f:write("{"..q.."direct"..q..":"..tostring(direct)..","..q.."alliance"..q..":"..tostring(alliance)..","..q.."random"..q..":"..tostring(random).."}")',
  '    f:close()',
  '  end',
  'end)',
].join('\n');
// Lightweight self pid/castle resolver (must stay small — large DoString blocks abort the VM).
const MARK_SELF_LUA = [
  'pcall(function()',
  '  local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '  local myName = nil',
  '  if type(pd) == "table" then',
  '    myName = pd.playerName or pd.name or pd.nickName or pd.nickname or pd.roleName or pd.userName',
  '  end',
  '  if not myName or tostring(myName) == "" then return end',
  '  local md = _G.Data and _G.Data.AllianceData and _G.Data.AllianceData.member and _G.Data.AllianceData.member.memberDic',
  '  if type(md) ~= "table" then return end',
  '  local q = string.char(34)',
  '  for pid, m in pairs(md) do',
  '    local p = m and m.profile',
  '    if type(p) == "table" then',
  '      local pn = tostring(p.name or "")',
  '      if pn == tostring(myName) or string.lower(pn) == string.lower(tostring(myName)) then',
  '        local selfId = tonumber(p.id or pid)',
  '        if selfId and selfId > 0 then',
  '          _G.__sr_my_pid = selfId',
  '          _G.__sr_aa_my_pid = selfId',
  '          local cx, cy, cs = 0, 0, 0',
  '          local cc = m.cityCoords',
  '          if type(cc) == "table" and cc.x and cc.y then',
  '            cx = math.floor(tonumber(cc.x))',
  '            cy = math.floor(tonumber(cc.y))',
  '            cs = math.floor(tonumber(cc.sid) or 0)',
  '            _G.__sr_my_castle = { x = cx, y = cy, sid = cs }',
  '          end',
  '          local sf = io.open(' + SELF_PLAYER_FILE_LUA + ', "w")',
  '          if sf then',
  '            local esc = tostring(myName):gsub(q, string.char(39))',
  '            sf:write("{"..q.."id"..q..":"..q..tostring(selfId)..q..","..q.."name"..q..":"..q..esc..q..","..q.."x"..q..":"..tostring(cx)..","..q.."y"..q..":"..tostring(cy)..","..q.."sid"..q..":"..tostring(cs).."}")',
  '            sf:close()',
  '          end',
  '        end',
  '        break',
  '      end',
  '    end',
  '  end',
  'end)',
].join('\n');
// Кэш реальных составов марша (team) по teamIndex, снятый с настоящих вступлений.
// Хранится как Lua-чанк "return {...}" (легко сериализовать/загрузить через load()).
const JOIN_CACHE_FILE = '/data/data/com.phs.global/files/squadrelay_teamcache.lua';
const JOIN_CACHE_FILE_LUA = "'" + JOIN_CACHE_FILE + "'";

const AUTOASSAULT_MATCH_FILE_LUA = "'" + AUTOASSAULT_MATCH_FILE + "'";

// Устанавливает обёртку на TroopOperateParam:DoSendMsg (родитель RallyJoin), чтобы:
//  1) кэшировать team (расстановку героев) по teamIndex с РЕАЛЬНЫХ вступлений игрока,
//  2) запомнить свой playerId (передаётся как targetPlayerId),
//  3) предоставить _G.__sr_aa_build_team(idx) для авто-вступления (глубокая копия из кэша).
// Кэш персистится в файл и подгружается при установке (переживает рестарт игры).
const INSTALL_JOIN_CACHE_LUA = [
  'pcall(function()',
  '  local TOP = package.loaded["AbyssEmpire.Logic.Troop.Define.TroopOperateParam"]',
  '  if type(TOP) ~= "table" then return end',
  '  _G.__sr_team_cache = _G.__sr_team_cache or {}',
  '  if not _G.__sr_cache_loaded then',
  '    _G.__sr_cache_loaded = true',
  '    local cf = io.open(' + JOIN_CACHE_FILE_LUA + ', "r")',
  '    if cf then',
  '      local s = cf:read("*a") cf:close()',
  '      if s and #s > 0 then',
  '        local ok, ch = pcall(load, s)',
  '        if ok and type(ch) == "function" then',
  '          local d = select(2, pcall(ch))',
  '          if type(d) == "table" then',
  '            if type(d.teams) == "table" then for k, v in pairs(d.teams) do _G.__sr_team_cache[tonumber(k) or k] = v end end',
  '            local p0 = tonumber(d.pid)',
  '            if p0 and p0 > 0 then _G.__sr_my_pid = p0 end',
  '            if p0 and p0 <= 0 and _G.__sr_save_cache then pcall(_G.__sr_save_cache) end',
  '          end',
  '        end',
  '      end',
  '    end',
  '    if tonumber(_G.__sr_my_pid) and tonumber(_G.__sr_my_pid) <= 0 then _G.__sr_my_pid = nil end',
    '    local pd0 = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    if pd0 then',
  '      local myId0 = tonumber(pd0.playerId or pd0.id or pd0.uid or pd0.roleId)',
  '      if myId0 and myId0 > 0 then _G.__sr_my_pid = myId0 end',
  '    end',
  '    if not (_G.__sr_my_pid and tonumber(_G.__sr_my_pid) > 0) then',
  '      local md0 = _G.Data and _G.Data.AllianceData and _G.Data.AllianceData.member and _G.Data.AllianceData.member.memberDic',
  '      if type(md0) == "table" then',
  '        for k0, m0 in pairs(md0) do',
  '          if type(m0) == "table" and (m0.isSelf or m0.self or (m0.profile and (m0.profile.isSelf or m0.profile.self))) then',
  '            local id0 = tonumber(m0.profile and m0.profile.id or k0)',
  '            if id0 and id0 > 0 then _G.__sr_my_pid = id0 break end',
  '          end',
  '        end',
  '      end',
  '    end',
  '    if not (_G.__sr_my_pid and tonumber(_G.__sr_my_pid) > 0) then',
  '      local pdN = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '      local myName0 = pdN and (pdN.playerName or pdN.name or pdN.nickName or pdN.nickname)',
  '      if myName0 and tostring(myName0) ~= "" then',
  '        local mdN = _G.Data and _G.Data.AllianceData and _G.Data.AllianceData.member and _G.Data.AllianceData.member.memberDic',
  '        if type(mdN) == "table" then',
  '          for kN, mN in pairs(mdN) do',
  '            local pN = mN and mN.profile',
  '            if type(pN) == "table" and tostring(pN.name) == tostring(myName0) then',
  '              local idN = tonumber(pN.id or kN)',
  '              if idN and idN > 0 then _G.__sr_my_pid = idN break end',
  '            end',
  '          end',
  '        end',
  '      end',
  '    end',
  '  end',
  // Если кэша нет (ни разу не вступали этим отрядом) — сид из пресетов TroopManager.
  // wingManId = index отряда (0/1/2), как в ручном join-capture.
    '  local TM0 = package.loaded["AbyssEmpire.Logic.Troop.TroopManager"]',
    '  if type(TM0) == "table" and type(TM0.GetAllTroopInfos) == "function" then',
    '    local ok0, all0 = pcall(TM0.GetAllTroopInfos, TM0)',
    '    if ok0 and type(all0) == "table" then',
    '      local seeded = false',
    '      for _, troop in pairs(all0) do',
    '        if type(troop) == "table" then',
    '          local idx = tonumber(troop.index)',
    '          if idx ~= nil and type(troop.heroIds) == "table" and #troop.heroIds > 0 then',
    '            local cur = _G.__sr_team_cache[idx]',
    '            if type(cur) ~= "table" or type(cur.units) ~= "table" or #cur.units == 0 then',
    '              local units = {}',
    '              for i = 1, #troop.heroIds do units[i] = {heroId = troop.heroIds[i], slotId = i - 1, heroSource = 0} end',
    '              _G.__sr_team_cache[idx] = {wingManId = idx, units = units}',
    '              seeded = true',
    '            end',
    '          end',
    '        end',
    '      end',
    '      if seeded and _G.__sr_save_cache then pcall(_G.__sr_save_cache) end',
    '    end',
    '  end',
  '  local function emitTeam(t)',
  '    local us = {}',
  '    if type(t.units) == "table" then',
  '      for i = 1, #t.units do',
  '        local u = t.units[i]',
  '        us[#us + 1] = string.format("{heroId=%s,slotId=%s,heroSource=%s}", tostring(tonumber(u.heroId) or 0), tostring(tonumber(u.slotId) or 0), tostring(tonumber(u.heroSource) or 0))',
  '      end',
  '    end',
  '    return string.format("{wingManId=%s,units={%s}}", tostring(tonumber(t.wingManId) or 0), table.concat(us, ","))',
  '  end',
  '  _G.__sr_save_cache = function()',
  '    local parts = {}',
  '    for k, v in pairs(_G.__sr_team_cache) do parts[#parts + 1] = string.format("[%s]=%s", tostring(k), emitTeam(v)) end',
  '    local pid = tonumber(_G.__sr_my_pid)',
  '    if not pid or pid <= 0 then pid = nil end',
  '    local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    if pd then local id = tonumber(pd.playerId or pd.id or pd.uid or pd.roleId) if id and id > 0 then pid = id end end',
  '    if pid and pid > 0 then _G.__sr_my_pid = pid end',
  '    local s',
  '    if pid and pid > 0 then s = string.format("return {pid=%s,teams={%s}}", tostring(pid), table.concat(parts, ","))',
  '    else s = string.format("return {teams={%s}}", table.concat(parts, ",")) end',
  '    local f = io.open(' + JOIN_CACHE_FILE_LUA + ', "w") if f then f:write(s) f:close() end',
  '  end',
  '  _G.__sr_aa_build_team = function(idx)',
  // 1) Если есть кэш реального вступления для этого индекса — используем его (точный формат).
  '    local src = _G.__sr_team_cache and _G.__sr_team_cache[idx]',
  '    if type(src) == "table" and type(src.units) == "table" and #src.units > 0 then',
  '      local units = {}',
  '      for i = 1, #src.units do local u = src.units[i] units[i] = {heroId = u.heroId, slotId = u.slotId, heroSource = u.heroSource} end',
  '      return {wingManId = src.wingManId ~= nil and tonumber(src.wingManId) or idx, units = units}',
  '    end',
  // 2) Иначе собираем состав из ГОТОВОГО пресета отряда (как настроено в игре), без ручных
  //    вступлений: TroopManager.GetAllTroopInfos() -> troop с index/wingManId/heroIds.
  '    local TM = package.loaded["AbyssEmpire.Logic.Troop.TroopManager"]',
  '    if type(TM) == "table" and type(TM.GetAllTroopInfos) == "function" then',
  '      local okp, all = pcall(TM.GetAllTroopInfos, TM)',
  '      if okp and type(all) == "table" then',
  '        for _, troop in pairs(all) do',
  '          if type(troop) == "table" and tonumber(troop.index) == idx and type(troop.heroIds) == "table" and #troop.heroIds > 0 then',
  '            local units = {}',
  '            for i = 1, #troop.heroIds do units[i] = {heroId = troop.heroIds[i], slotId = i - 1, heroSource = 0} end',
  '            return {wingManId = idx, units = units}',
  '          end',
  '        end',
  '      end',
  '    end',
  '    return nil',
  '  end',
  // Вступление в штурм: RallyJoin.Set(teamIndex, {allianceWarsAssemblyObj}) + оригинальный DoSendMsg.
  // DoSendMsg вызывает sender() без аргументов — нужен игровой sender или fallback с proto.
  '  _G.__sr_aa_join_rally = function(war, pickedIdx, team)',
  '    if type(war) ~= "table" or type(team) ~= "table" then return false end',
  '    local rp = war.rallyPoint',
  '    if type(rp) ~= "table" or not rp.x then',
  '      local pid = tonumber(war.playerId)',
  '      if pid and pid > 0 then',
  '        local md = _G.Data and _G.Data.AllianceData and _G.Data.AllianceData.member and _G.Data.AllianceData.member.memberDic',
  '        if type(md) == "table" then',
  '          local m = md[pid] or md[tostring(pid)]',
  '          local cc = m and m.cityCoords',
  '          if type(cc) == "table" and cc.x and cc.y then rp = { x = tonumber(cc.x), y = tonumber(cc.y), sid = tonumber(cc.sid) } war.rallyPoint = rp end',
  '        end',
  '      end',
  '    end',
  '    if type(rp) ~= "table" or not rp.x then return false end',
  '    local RJ = package.loaded["AbyssEmpire.Logic.Troop.Define.Param.RallyJoin"]',
  '    local TOP = package.loaded["AbyssEmpire.Logic.Troop.Define.TroopOperateParam"]',
  '    local sm = package.loaded["Logic.Proto.Send.union_war"]',
  '    if type(RJ) ~= "table" or type(TOP) ~= "table" or not sm or not sm.JoinUnionRallyC2S then return false end',
  '    local orig = TOP.__sr_dsm_orig or TOP.DoSendMsg',
  '    local creator = tonumber(war.playerId) or 0',
  '    local proto = { warId = war.id, teamIndex = pickedIdx, target = rp, targetPlayerId = creator, team = team }',
  '    local function memberCount()',
  '      local n = 0',
  '      local m = war.attackSide and war.attackSide.member',
  '      if type(m) == "table" then for _ in pairs(m) do n = n + 1 end end',
  '      return n',
  '    end',
  '    local beforeMembers = memberCount()',
  '    local pt = { allianceWarsAssemblyObj = war }',
  '    pcall(RJ.Set, RJ, pickedIdx, pt)',
  '    local inst = pt',
  '    setmetatable(inst, { __index = RJ })',
  '    if type(inst.sender) ~= "function" then',
  '      inst.warId = war.id',
  '      inst.targetPlayerId = creator',
  '      inst.troopIndex = pickedIdx',
  '      inst.teamIndex = pickedIdx',
  '      inst.paramTable = { allianceWarsAssemblyObj = war }',
  '      inst.allianceWarsAssemblyObj = war',
  '      inst.marchType = 7',
  '      inst.operate = "Battle"',
  '      inst.defaultOperate = "Battle"',
  '      inst.marchFormationType = 9',
  '      inst.waitingTime = 60000',
  '      inst.targetType = 6',
  '      inst.endMainLocationX = rp.x',
  '      inst.endMainLocationY = rp.y',
  '      inst.worldPointStruct = { sid = rp.sid, pid = rp.pid, x = rp.x, y = rp.y }',
  '      inst.worldMapUnitData = { mainLocationX = rp.x, mainLocationY = rp.y, type = 1, point = rp }',
  '      inst.sender = function(self, data, client)',
  '        local payload = proto',
  '        if type(data) == "table" and data.warId then payload = data end',
  '        return sm.JoinUnionRallyC2S(payload, self)',
  '      end',
  '      inst.defaultSender = function(self, data, client) return self:sender(data, client) end',
  '      setmetatable(inst, { __index = TOP })',
  '    end',
  '    local ok = pcall(orig, inst, proto)',
  '    if memberCount() > beforeMembers then return true end',
  '    local TM = package.loaded["AbyssEmpire.Logic.Troop.TroopManager"]',
  '    if type(TM) == "table" and type(TM.GetAllTroopInfos) == "function" then',
  '      local ok2, all = pcall(TM.GetAllTroopInfos, TM)',
  '      if ok2 and type(all) == "table" then',
  '        for _, troop in pairs(all) do',
  '          if type(troop) == "table" and tonumber(troop.index) == pickedIdx then',
  '            local st = tonumber(troop.status) or 0',
  '            local mid = tonumber(troop.marchId) or 0',
  '            if st ~= 0 or mid ~= 0 then return true end',
  '          end',
  '        end',
  '      end',
  '    end',
  // Пакет ушёл без ошибки Lua — ответ сервера асинхронный, memberCount обновится позже.
  '    return ok',
  '  end',
  '  if not TOP.__sr_dsm_orig then',
  '    TOP.__sr_dsm_orig = TOP.DoSendMsg',
  '    TOP.DoSendMsg = function(self, protoData)',
  '      pcall(function()',
  '        if type(protoData) == "table" and protoData.warId and protoData.teamIndex ~= nil and type(protoData.team) == "table" and type(protoData.team.units) == "table" then',
  '          local idx = tonumber(protoData.teamIndex)',
  '          local src = protoData.team',
  '          local units = {}',
  '          for i = 1, #src.units do local u = src.units[i] units[i] = {heroId = u.heroId, slotId = u.slotId, heroSource = u.heroSource} end',
  '          _G.__sr_team_cache[idx] = {wingManId = src.wingManId or idx, units = units}',
  // targetPlayerId в join-proto — создатель штурма, не наш id; не перезаписывать __sr_my_pid.
  '          local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '          if pd then',
  '            local myId = tonumber(pd.playerId or pd.id or pd.uid or pd.roleId)',
  '            if myId and myId > 0 then _G.__sr_my_pid = myId end',
  '          end',
  '          if _G.__sr_save_cache then pcall(_G.__sr_save_cache) end',
  '        end',
  '      end)',
  '      return TOP.__sr_dsm_orig(self, protoData)',
  '    end',
  '  end',
  'end)',
].join('\n');
const AUTOASSAULT_SCAN_LUA = [
  'local __sr_aa_scan_ok, __sr_aa_scan_err = pcall(function()',
  '  local diag = {}',
  '  local function D(s) diag[#diag + 1] = tostring(s) end',
  '  local function skip(id, why) D("skip id=" .. tostring(id) .. " " .. why) end',
  '  local function jsonNum(s, key)',
  '    local v = s:match("\\"" .. key .. "\\":(%d+)")',
  '    return v and tonumber(v) or nil',
  '  end',
  '  local function loadCfgFromJson()',
  '    local f = io.open(' + AUTOASSAULT_FILE_LUA + ', "r")',
  '    if not f then return nil end',
  '    local s = f:read("*a") f:close()',
  '    if not s or #s < 20 then return nil end',
  '    local cfg = {}',
  '    cfg.enabled = s:find("\\"enabled\\":true", 1, true) ~= nil',
  '    cfg.maxDistance = jsonNum(s, "maxDistance") or 9999',
  '    cfg.maxDistanceCreator = jsonNum(s, "maxDistanceCreator") or cfg.maxDistance',
  '    cfg.maxDistanceTarget = jsonNum(s, "maxDistanceTarget") or cfg.maxDistance',
  '    cfg.minRemainingSec = jsonNum(s, "minRemainingSec") or 0',
  '    cfg.levelMin = jsonNum(s, "levelMin") or 0',
  '    cfg.levelMax = jsonNum(s, "levelMax") or 0',
  '    cfg.maxConcurrent = jsonNum(s, "maxConcurrent") or 0',
  '    cfg.disableAtEpochMs = jsonNum(s, "disableAtEpochMs") or 0',
  '    cfg.myPlayerId = jsonNum(s, "myPlayerId")',
  '    cfg.myCastleX = jsonNum(s, "myCastleX")',
  '    cfg.myCastleY = jsonNum(s, "myCastleY")',
  '    cfg.myCastleSid = jsonNum(s, "myCastleSid")',
  '    cfg.joinEnabled = true',
  '    cfg.targetTypes = {}',
  '    local tt = s:match("\\"targetTypes\\":%[([^%]]*)%]")',
  '    if tt then for v in tt:gmatch("\\"([^\\"]+)\\"") do cfg.targetTypes[#cfg.targetTypes + 1] = v end end',
  '    cfg.allowedUserIds = {}',
  '    local ids = s:match("\\"allowedUserIds\\":%[([^%]]*)%]")',
  '    if ids then for v in ids:gmatch("\\"(%d+)\\"") do cfg.allowedUserIds[#cfg.allowedUserIds + 1] = v end end',
  '    cfg.squads = {}',
  '    for idx, pmin, pmax in s:gmatch("\\"index\\":(%d+),\\"powerMin\\":(%d+),\\"powerMax\\":(%d+)") do',
  '      cfg.squads[#cfg.squads + 1] = { index = tonumber(idx), powerMin = tonumber(pmin), powerMax = tonumber(pmax) }',
  '    end',
  '    return cfg',
  '  end',
  '  local cfg = _G.__sr_aa_cfg',
  '  if type(cfg) ~= "table" or not cfg.myPlayerId then',
  '    local fc = loadCfgFromJson()',
  '    if type(fc) == "table" then cfg = fc _G.__sr_aa_cfg = fc end',
  '  end',
  '  if not cfg then D("abort: __sr_aa_cfg nil"); return end',
  '  if not cfg.enabled then D("abort: cfg.enabled=false"); return end',
  '  if cfg.disableAtEpochMs and cfg.disableAtEpochMs > 0 and (os.time()*1000) >= cfg.disableAtEpochMs then return end',
  '  local minRem = tonumber(cfg.minRemainingSec) or 0',
  '  local lvMin = tonumber(cfg.levelMin) or 0',
  '  local lvMax = tonumber(cfg.levelMax) or 0',
  '  local maxConc = tonumber(cfg.maxConcurrent) or 0',
  '  local legD = tonumber(cfg.maxDistance) or 9999',
  '  local maxDC = tonumber(cfg.maxDistanceCreator) or legD',
  '  local maxDT = tonumber(cfg.maxDistanceTarget) or legD',
  '  local ad = _G.Data and _G.Data.AllianceData',
  '  local wars = ad and ad.wars',
  '  if type(wars) ~= "table" then D("abort: no AllianceData.wars"); return end',
  // Присоединяемые штурмы лежат в под-словарях wars.assemblyDic / wars.activityDic
  // (keyed по id), а не в самом wars (это контейнер под-словарей).
  '  local function dcount(t) local n=0 if type(t)=="table" then for _ in pairs(t) do n=n+1 end end return n end',
  '  local dics = {}',
  '  local seenWarIds = {}',
  '  if type(wars.assemblyDic) == "table" then dics[#dics + 1] = wars.assemblyDic end',
  '  if type(wars.activityDic) == "table" then dics[#dics + 1] = wars.activityDic end',
  '  if #dics == 0 then D("abort: no assemblyDic/activityDic"); return end',
  '  local sm = package.loaded["Logic.Proto.Send.union_war"]',
  '  if not sm or not sm.JoinUnionRallyC2S then return end',
  '  local TOP = package.loaded["AbyssEmpire.Logic.Troop.Define.TroopOperateParam"]',
  '  if not TOP or not TOP.DoSendMsg then return end',
  '  local C = _G.Config',
  '  local troopFree, troopBusySec',
  '  local function cheb(ax,ay,bx,by) return math.max(math.abs(ax-bx), math.abs(ay-by)) end',
  '  local function memberDic()',
  '    local ad = _G.Data and _G.Data.AllianceData',
  '    return ad and ad.member and ad.member.memberDic',
  '  end',
  '  local function memberIsSelf(m)',
  '    if type(m) ~= "table" then return false end',
  '    if m.isSelf or m.self then return true end',
  '    local p = m.profile',
  '    if type(p) == "table" and (p.isSelf or p.self) then return true end',
  '    return false',
  '  end',
  '  local function resolvePlayerName()',
  '    local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    if type(pd) ~= "table" then return nil end',
  '    return pd.playerName or pd.name or pd.nickName or pd.nickname',
  '  end',
  '  local function ptFromMember(m)',
  '    local cc = m and m.cityCoords',
  '    if type(cc) == "table" and cc.x and cc.y then',
  '      return { x = tonumber(cc.x), y = tonumber(cc.y), sid = tonumber(cc.sid) }',
  '    end',
  '    return nil',
  '  end',
  '  local function memberRecord(pid)',
  '    pid = tonumber(pid)',
  '    if not pid or pid <= 0 then return nil end',
  '    local md = memberDic()',
  '    if type(md) ~= "table" then return nil end',
  '    local m = md[pid] or md[tostring(pid)]',
  '    if not m then',
  '      for _, mem in pairs(md) do',
  '        local p = mem and mem.profile',
  '        if type(p) == "table" and tonumber(p.id) == pid then m = mem break end',
  '      end',
  '    end',
  '    return m',
  '  end',
  '  local function applySelfId(pid, m)',
  '    pid = tonumber(pid)',
  '    if not pid or pid <= 0 then return nil end',
  '    _G.__sr_my_pid = pid',
  '    _G.__sr_aa_my_pid = pid',
  '    if type(m) == "table" then',
  '      local pt = ptFromMember(m)',
  '      if pt then _G.__sr_my_castle = pt end',
  '    end',
  '    return pid',
  '  end',
  '  local function selfFromFile()',
  '    local rf = io.open(' + SELF_PLAYER_FILE_LUA + ', "r")',
  '    if not rf then return nil end',
  '    local s = rf:read("*a") rf:close()',
  '    if not s or #s < 5 then return nil end',
  '    local q = string.char(34)',
  '    local id = tonumber(s:match(q .. "id" .. q .. ":" .. q .. "(%d+)" .. q))',
  '    local x = tonumber(s:match(q .. "x" .. q .. ":(%-?%d+)"))',
  '    local y = tonumber(s:match(q .. "y" .. q .. ":(%-?%d+)"))',
  '    local sid = tonumber(s:match(q .. "sid" .. q .. ":(%-?%d+)"))',
  '    if id and id > 0 and x and y and x > 0 and y > 0 then',
  '      _G.__sr_my_castle = { x = x, y = y, sid = sid or 0 }',
  '    end',
  '    if id and id > 0 then return applySelfId(id, memberRecord(id)) end',
  '    return nil',
  '  end',
  '  local function resolveMyPlayerId()',
  '    if tonumber(_G.__sr_my_pid) and tonumber(_G.__sr_my_pid) <= 0 then _G.__sr_my_pid = nil end',
  '    if tonumber(_G.__sr_aa_my_pid) and tonumber(_G.__sr_aa_my_pid) <= 0 then _G.__sr_aa_my_pid = nil end',
  '    local cfgMy = cfg and tonumber(cfg.myPlayerId)',
  '    if cfgMy and cfgMy > 0 then return applySelfId(cfgMy, memberRecord(cfgMy)) end',
  '    local pid = tonumber(_G.__sr_aa_my_pid)',
  '    if pid and pid > 0 then return pid end',
  '    pid = selfFromFile()',
  '    if pid and pid > 0 then return pid end',
  '    local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    if type(pd) == "table" then',
  '      pid = tonumber(pd.playerId or pd.id or pd.uid or pd.roleId)',
  '    end',
  '    local cached = tonumber(_G.__sr_my_pid)',
  '    if (not pid or pid <= 0) and cached and cached > 0 then pid = cached end',
  '    if not pid or pid <= 0 then',
  '      local md = memberDic()',
  '      if type(md) == "table" then',
  '        for k, m in pairs(md) do',
  '          if memberIsSelf(m) then',
  '            pid = tonumber(m.profile and m.profile.id or k)',
  '            break',
  '          end',
  '        end',
  '      end',
  '    end',
  '    if not pid or pid <= 0 then',
  '      local myName = resolvePlayerName()',
  '      if myName and tostring(myName) ~= "" then',
  '        local md = memberDic()',
  '        if type(md) == "table" then',
  '          for k, m in pairs(md) do',
  '            local p = m and m.profile',
  '            if type(p) == "table" and tostring(p.name) == tostring(myName) then',
  '              pid = tonumber(p.id or k)',
  '              break',
  '            end',
  '          end',
  '        end',
  '      end',
  '    end',
  '    if not pid or pid <= 0 then',
  '      local ad = _G.Data and _G.Data.AllianceData',
  '      local me = ad and (ad.myMember or ad.selfMember or (ad.member and ad.member.self))',
  '      if type(me) == "table" then',
  '        pid = tonumber(me.profile and me.profile.id or me.playerId or me.id)',
  '      end',
  '    end',
  '    if pid and pid > 0 then',
  '      return applySelfId(pid, memberRecord(pid))',
  '    end',
  '    return nil',
  '  end',
  '  local function selfMemberRecord()',
  '    local md = memberDic()',
  '    if type(md) ~= "table" then return nil end',
  '    local pid = resolveMyPlayerId()',
  '    if pid and pid > 0 then return memberRecord(pid) end',
  '    local myName = resolvePlayerName()',
  '    if myName and tostring(myName) ~= "" then',
  '      for k, m in pairs(md) do',
  '        local p = m and m.profile',
  '        if type(p) == "table" and tostring(p.name) == tostring(myName) then return m end',
  '      end',
  '    end',
  '    for k, m in pairs(md) do',
  '      if memberIsSelf(m) then return m end',
  '    end',
  '    return nil',
  '  end',
  '  local function castlePt()',
  '    if cfg then',
  '      local cx = tonumber(cfg.myCastleX)',
  '      local cy = tonumber(cfg.myCastleY)',
  '      if cx and cy and cx > 0 and cy > 0 then',
  '        return { x = cx, y = cy, sid = tonumber(cfg.myCastleSid) or 0 }',
  '      end',
  '    end',
  '    local pd = _G.Data and (_G.Data.PlayerData or _G.Data.UserData)',
  '    if type(pd) == "table" then',
  '      local pt = pd.castlePoint or pd.homePoint or pd.basePoint or pd.point',
  '      if type(pt) == "table" and pt.x and pt.y then',
  '        return { x = tonumber(pt.x), y = tonumber(pt.y), sid = tonumber(pt.sid) }',
  '      end',
  '    end',
  '    local pt = ptFromMember(selfMemberRecord())',
  '    if pt then return pt end',
  '    local myId = resolveMyPlayerId()',
  '    if myId and myId > 0 then',
  '      pt = ptFromMember(memberRecord(myId))',
  '      if pt then return pt end',
  '    end',
  '    local cached = _G.__sr_my_castle',
  '    if type(cached) == "table" and cached.x and cached.y then',
  '      return { x = tonumber(cached.x), y = tonumber(cached.y), sid = tonumber(cached.sid) }',
  '    end',
  '    return nil',
  '  end',
  // Точка сбора = город создателя (rallyPoint совпадает с cityCoords в ростере).
  // Дистанция: maxDistanceCreator — до города создателя / rallyPoint; maxDistanceTarget — до монстра (targetPoint).
  '  local function memberCityPt(playerId)',
  '    local m = memberRecord(playerId)',
  '    local cc = m and m.cityCoords',
  '    if type(cc) == "table" and cc.x and cc.y then',
  '      return { x = tonumber(cc.x), y = tonumber(cc.y), sid = tonumber(cc.sid) }',
  '    end',
  '    return nil',
  '  end',
  '  local function creatorRallyPt(war)',
  '    local rp = war.rallyPoint',
  '    if type(rp) == "table" and rp.x and rp.y then return rp end',
  '    return memberCityPt(war.playerId)',
  '  end',
  '  local function cfgRow(lairId)',
  '    return C and C.SlgRallyInfo and C.SlgRallyInfo[lairId]',
  '  end',
  '  local function targetPower(row)',
  '    if type(row) == "table" then return tonumber(row.recAbility) or 0 end',
  '    return 0',
  '  end',
  '  local function targetLevel(row, war)',
  '    if type(row) == "table" and row.level then return tonumber(row.level) or 0 end',
  '    if war.targetLevel and tonumber(war.targetLevel) and tonumber(war.targetLevel) > 0 then return tonumber(war.targetLevel) end',
  '    return 0',
  '  end',
  // Классификация типа цели: монстр (есть SlgRallyInfo) / игрок (есть защитник) / город (иначе).
  '  local function classify(war, row)',
  '    if type(row) == "table" then return "monster" end',
  '    local def = war.defenceSide',
  '    if type(def) == "table" and tonumber(def.maxMember) and tonumber(def.maxMember) > 0 then return "player" end',
  '    if war.targetPlayerId or war.defencePlayerId then return "player" end',
  '    return "city"',
  '  end',
  '  local function typeAllowed(t)',
  '    local types = cfg.targetTypes',
  '    if type(types) ~= "table" or #types == 0 then return true end',
  '    for i = 1, #types do if tostring(types[i]) == t then return true end end',
  '    return false',
  '  end',
  '  local function allowedCreator(war)',
  '    local names = cfg.allowedNames',
  '    local ids = cfg.allowedUserIds',
  '    local hasNames = type(names) == "table" and #names > 0',
  '    local hasIds = type(ids) == "table" and #ids > 0',
  '    if not hasNames and not hasIds then return true end',
  '    if hasNames then',
  '      local name = tostring(war.playerName or "")',
  '      for i = 1, #names do if tostring(names[i]) == name then return true end end',
  '    end',
  '    if hasIds then',
  '      local pid = tonumber(war.playerId)',
  '      if pid then for i = 1, #ids do if tonumber(ids[i]) == pid then return true end end end',
  '    end',
  '    return false',
  '  end',
  '  local function squadOk(pow, idx)',
  '    local squads = cfg.squads',
  '    if type(squads) ~= "table" then return false end',
  '    for i = 1, #squads do',
  '      local s = squads[i]',
  '      if type(s) == "table" and tonumber(s.index) == idx then',
  '        local mn = tonumber(s.powerMin) or 0',
  '        local mx = tonumber(s.powerMax) or 999999999',
  '        return pow >= mn and pow <= mx',
  '      end',
  '    end',
  '    return false',
  '  end',
  '  local function memberCount(atk)',
  '    local n = 0',
  '    local m = atk and atk.member',
  '    if type(m) == "table" then for _ in pairs(m) do n = n + 1 end end',
  '    return n',
  '  end',
  // Текущее число активных авто-маршей (грубая оценка по нашим вступлениям этой сессии).
  '  local function activeJoins()',
  '    local t = _G.__sr_aa_active',
  '    if type(t) ~= "table" then return 0 end',
  '    local now = os.time()',
  '    local n = 0',
  '    for id, endt in pairs(t) do if endt and endt > now then n = n + 1 else t[id] = nil end end',
  '    return n',
  '  end',
  '  troopBusySec = function(idx)',
  '    local TM = package.loaded["AbyssEmpire.Logic.Troop.TroopManager"]',
  '    if type(TM) ~= "table" or type(TM.GetAllTroopInfos) ~= "function" then return 0 end',
  '    local ok, all = pcall(TM.GetAllTroopInfos, TM)',
  '    if not ok or type(all) ~= "table" then return 0 end',
  '    for _, troop in pairs(all) do',
  '      if type(troop) == "table" and tonumber(troop.index) == idx then',
  '        local st = tonumber(troop.status) or 0',
  '        local mid = tonumber(troop.marchId) or 0',
  '        if st == 0 and mid == 0 then return 0 end',
  '        local nowMs = os.time() * 1000',
  '        local endMs = tonumber(troop.endTime or troop.marchEndTime or troop.finishTime)',
  '        if endMs and endMs > nowMs then return (endMs - nowMs) / 1000 end',
  '        local startMs = tonumber(troop.startTime) or 0',
  '        local durMs = tonumber(troop.marchDuration or troop.totalTime or troop.duration) or 0',
  '        if startMs > 0 and durMs > 0 then',
  '          local etaMs = startMs + durMs - nowMs',
  '          if etaMs > 0 then return etaMs / 1000 end',
  '        end',
  '        local MD = _G.Data and _G.Data.MarchData',
  '        local march = MD and MD.ownerTeamIndex2March and MD.ownerTeamIndex2March[idx]',
  '        if type(march) == "table" then',
  '          local ms = tonumber(march.marchStartTime or march.startTime) or 0',
  '          local dur = tonumber(march.marchDuration or march.duration) or 0',
  '          local sp = tonumber(march.speedupTime) or 0',
  '          if ms > 0 and dur > 0 then',
  '            local etaMs = ms + dur - sp - nowMs',
  '            if etaMs > 0 then return etaMs / 1000 end',
  '          end',
  '        end',
  '        return 0',
  '      end',
  '    end',
  '    return 0',
  '  end',
  // «Время до старта» (minRem): только для ЗАНЯТЫХ отрядов — хватит ли времени после возврата.
  // Свободный отряд вступает сразу (игра сама проверит успеет ли марш до точки сбора).
  '  local function formationWindowSec(war)',
  '    local st = tonumber(war.rallyStartTime)',
  '    local en = tonumber(war.rallyEndTime)',
  '    if st and en and en > st then return (en - st) / 1000 end',
  '    return 0',
  '  end',
  '  local function effectiveMinRem(war)',
  '    if minRem <= 0 then return 0 end',
  '    local w = formationWindowSec(war)',
  '    if w > 0 and w <= 120 then return math.min(minRem, math.max(5, math.floor(w * 0.5))) end',
  '    return minRem',
  '  end',
  '  local function remOkForSquad(war, idx)',
  '    if troopFree(idx) then return true end',
  '    local eff = effectiveMinRem(war)',
  '    if eff <= 0 then return true end',
  '    if not war.rallyEndTime then return true end',
  '    local remSec = (tonumber(war.rallyEndTime) - os.time() * 1000) / 1000',
  '    local remWhenReady = remSec - troopBusySec(idx)',
  '    return remWhenReady >= eff',
  '  end',
  '  troopFree = function(idx)',
  '    local TM = package.loaded["AbyssEmpire.Logic.Troop.TroopManager"]',
  '    if type(TM) ~= "table" or type(TM.GetAllTroopInfos) ~= "function" then return true end',
  '    local ok, all = pcall(TM.GetAllTroopInfos, TM)',
  '    if not ok or type(all) ~= "table" then return true end',
  '    for _, troop in pairs(all) do',
  '      if type(troop) == "table" and tonumber(troop.index) == idx then',
  '        local st = tonumber(troop.status) or 0',
  '        local mid = tonumber(troop.marchId) or 0',
  '        return st == 0 and mid == 0',
  '      end',
  '    end',
  '    return false',
  '  end',
  '  local function myPlayerId()',
  '    return resolveMyPlayerId()',
  '  end',
  '  local function alreadyInRally(war)',
  '    local pid = myPlayerId()',
  '    if not pid then return false end',
  '    local m = war.attackSide and war.attackSide.member',
  '    if type(m) ~= "table" then return false end',
  '    for _, mem in pairs(m) do',
  '      if type(mem) == "table" and tonumber(mem.playerId) == pid then return true end',
  '    end',
  '    return false',
  '  end',
  // Отряд уже в любом штурме (марш или запись в member с нашим teamIndex) — нельзя слать в другой.
  '  local function squadInRallyMember(idx)',
  '    local pid = myPlayerId()',
  '    if not pid then return false end',
  '    local function scanDic(dic)',
  '      if type(dic) ~= "table" then return false end',
  '      for _, w in pairs(dic) do',
  '        if type(w) == "table" and w.isRally then',
  '          local m = w.attackSide and w.attackSide.member',
  '          if type(m) == "table" then',
  '            for _, mem in pairs(m) do',
  '              if type(mem) == "table" and tonumber(mem.playerId) == pid and tonumber(mem.teamIndex) == idx then return true end',
  '            end',
  '          end',
  '        end',
  '      end',
  '      return false',
  '    end',
  '    local w0 = _G.Data and _G.Data.AllianceData and _G.Data.AllianceData.wars',
    '    return scanDic(w0 and w0.assemblyDic) or scanDic(w0 and w0.activityDic)',
  '  end',
  '  _G.__sr_aa_active = _G.__sr_aa_active or {}',
  '  _G.__sr_aa_pending = _G.__sr_aa_pending or {}',
  '  local function squadPending(idx)',
  '    local t = _G.__sr_aa_pending[tostring(idx)]',
  '    if t and t > os.time() then return true end',
  '    if t then _G.__sr_aa_pending[tostring(idx)] = nil end',
  '    return false',
  '  end',
  '  local function markSquadPending(idx)',
  '    _G.__sr_aa_pending[tostring(idx)] = os.time() + 8',
  '  end',
  '  local function squadUnavailable(idx)',
  '    if squadPending(idx) then return true end',
  '    if squadInRallyMember(idx) then return true end',
  '    return not troopFree(idx)',
  '  end',
  '  local function alreadyJoinedWar(warId)',
  '    local t = _G.__sr_aa_active',
  '    if type(t) ~= "table" then return false end',
  '    local endt = t[tostring(warId)]',
  '    if not endt then return false end',
  '    if endt > os.time() then return true end',
  '    t[tostring(warId)] = nil',
  '    return false',
  '  end',
  '  local function markJoinedWar(warId, war)',
  '    _G.__sr_aa_active = _G.__sr_aa_active or {}',
  '    local endt = os.time() + 120',
  '    if war and war.rallyEndTime then endt = math.floor(tonumber(war.rallyEndTime) / 1000) + 120 end',
  '    _G.__sr_aa_active[tostring(warId)] = endt',
  '  end',
  '  local function marchingSquads()',
  '    local n = 0',
  '    local TM = package.loaded["AbyssEmpire.Logic.Troop.TroopManager"]',
  '    if type(TM) ~= "table" or type(TM.GetAllTroopInfos) ~= "function" then return activeJoins() end',
  '    local ok, all = pcall(TM.GetAllTroopInfos, TM)',
  '    if not ok or type(all) ~= "table" then return activeJoins() end',
  '    for _, troop in pairs(all) do',
  '      if type(troop) == "table" then',
  '        local st = tonumber(troop.status) or 0',
  '        local mid = tonumber(troop.marchId) or 0',
  '        if st ~= 0 or mid ~= 0 then n = n + 1 end',
  '      end',
  '    end',
  '    return n',
  '  end',
  '  local usedSquads = {}',
  '  local joinsThisScan = 0',
  '  local maxJoinsPerScan = 1',
  '  if type(cfg.squads) == "table" then maxJoinsPerScan = #cfg.squads end',
  '  if maxJoinsPerScan < 1 then maxJoinsPerScan = 1 end',
  '  if maxJoinsPerScan > 3 then maxJoinsPerScan = 3 end',
  '  local cp = castlePt()',
  '  local rallySeen = 0',
  '  D("myPid=" .. tostring(resolveMyPlayerId()) .. " cfgPid=" .. tostring(cfg.myPlayerId) .. " castlePt=" .. (cp and (cp.x .. "," .. cp.y) or "nil") .. " joinFn=" .. tostring(type(_G.__sr_aa_join_rally)) .. " buildTeam=" .. tostring(type(_G.__sr_aa_build_team)))',
  '  for di = 1, #dics do',
  '  local dic = dics[di]',
  '  for _, war in pairs(dic) do',
  '    if type(war) ~= "table" or not war.isRally then goto continue end',
  '    rallySeen = rallySeen + 1',
  '    local warKey = war.id and tostring(war.id) or nil',
  '    local wid = war.id or "?"',
  '    if warKey and seenWarIds[warKey] then skip(wid, "dup"); goto continue end',
  '    if warKey then seenWarIds[warKey] = true end',
  '    if maxConc > 0 and marchingSquads() >= maxConc then skip(wid, "maxConcurrent"); break end',
  '    do',
  '      local myId = resolveMyPlayerId()',
  '      local creatorId = tonumber(war.playerId)',
  '      if myId and creatorId and creatorId > 0 and creatorId == myId then skip(wid, "ownRally"); goto continue end',
  '    end',
  '    if war.id and (alreadyJoinedWar(war.id) or alreadyInRally(war)) then skip(wid, "alreadyIn"); goto continue end',
  '    local atk = war.attackSide',
  '    local maxM = atk and tonumber(atk.maxMember) or 0',
  '    local cnt = memberCount(atk)',
  '    if maxM > 0 and cnt >= maxM then skip(wid, "full " .. cnt .. "/" .. maxM); goto continue end',
  '    if not allowedCreator(war) then skip(wid, "creator " .. tostring(war.playerId)); goto continue end',
  '    local row = cfgRow(war.targetLairId)',
  '    local ttype = classify(war, row)',
  '    if not typeAllowed(ttype) then skip(wid, "type " .. ttype); goto continue end',
  '    local lv = targetLevel(row, war)',
  '    if lvMin > 0 and lv > 0 and lv < lvMin then skip(wid, "lv<min"); goto continue end',
  '    if lvMax > 0 and lv > 0 and lv > lvMax then skip(wid, "lv>max"); goto continue end',
  '    local joinPt = creatorRallyPt(war)',
  '    if type(joinPt) ~= "table" or not joinPt.x or not joinPt.y then skip(wid, "noRallyPt"); goto continue end',
  '    if (maxDC > 0 or maxDT > 0) and not cp then skip(wid, "noCastlePt"); goto continue end',
  '    local distC = cp and cheb(cp.x, cp.y, joinPt.x, joinPt.y) or -1',
  '    if maxDC > 0 and cp and distC >= 0 and distC > maxDC then skip(wid, "distC=" .. math.floor(distC) .. ">" .. maxDC); goto continue end',
  '    local distT = -1',
  '    local tp = war.targetPoint',
  '    if type(tp) == "table" and tp.x and tp.y then',
  '      distT = cp and cheb(cp.x, cp.y, tp.x, tp.y) or -1',
  '    end',
  '    if maxDT > 0 and cp and distT >= 0 and distT > maxDT then skip(wid, "distT=" .. math.floor(distT) .. ">" .. maxDT); goto continue end',
  '    local pow = targetPower(row)',
  '    local pickedIdx = nil',
  '    local squads = cfg.squads',
  '    if type(squads) == "table" then',
  // Один отряд на штурм (лимит игры), но берём первый СВОБОДНЫЙ — чтобы отряды 2/3 шли в другие штурмы.
  '      for si = 1, #squads do',
  '        local s = squads[si]',
  '        if type(s) == "table" then',
  '          local idx = tonumber(s.index)',
  '          if idx ~= nil and not usedSquads[idx] and squadOk(pow, idx) and remOkForSquad(war, idx) and not squadUnavailable(idx) then',
  '            pickedIdx = idx',
  '            break',
  '          end',
  '        end',
  '      end',
  '    end',
  '    if pickedIdx == nil then skip(wid, "noFreeSquad"); goto continue end',
  '    D("match id=" .. tostring(wid) .. " squad=" .. pickedIdx .. " creator=" .. tostring(war.playerName))',
  '    local matchJson = string.format(\'{"creator":"%s","type":"%s","power":%d,"level":%d,"dist":%d,"distCreator":%d,"distTarget":%d,"squad":%d,"id":"%s","time":%d}\', tostring(war.playerName or ""):gsub(\'"\',"\'"), ttype, math.floor(pow), math.floor(lv), math.floor(distC), math.floor(distC), math.floor(distT), pickedIdx, tostring(war.id or ""), os.time())',
  '    local f = io.open(' + AUTOASSAULT_MATCH_FILE_LUA + ', "w") if f then f:write(matchJson) f:close() end',
  '    if cfg.joinEnabled and war.id then',
  '      local rpJoin = war.rallyPoint',
  '      if type(rpJoin) ~= "table" or not rpJoin.x then rpJoin = joinPt end',
  '      if type(rpJoin) ~= "table" or not rpJoin.x then skip(wid, "noRpJoin"); goto continue end',
  // До maxJoinsPerScan вступлений за проход: разные отряды в разные штурмы (usedSquads).
  '      if joinsThisScan >= maxJoinsPerScan then skip(wid, "joinCap"); goto continue end',
  '      local team = (_G.__sr_aa_build_team and _G.__sr_aa_build_team(pickedIdx)) or nil',
  '      if type(team) ~= "table" then skip(wid, "noTeam idx=" .. pickedIdx); goto continue end',
  '      if type(war.rallyPoint) ~= "table" then war.rallyPoint = rpJoin end',
  '      usedSquads[pickedIdx] = true',
  '      joinsThisScan = joinsThisScan + 1',
  '      if _G.__sr_aa_join_rally and _G.__sr_aa_join_rally(war, pickedIdx, team) then',
  '        D("join ok id=" .. tostring(wid) .. " squad=" .. pickedIdx)',
  '        markSquadPending(pickedIdx)',
  '        markJoinedWar(war.id, war)',
  '      elseif squadInRallyMember(pickedIdx) or not troopFree(pickedIdx) then',
  '        D("join assumed id=" .. tostring(wid) .. " squad=" .. pickedIdx)',
  '        markSquadPending(pickedIdx)',
  '        markJoinedWar(war.id, war)',
  '      else',
  '        skip(wid, "joinFailed squad=" .. pickedIdx)',
  '      end',
  '    end',
  '    ::continue::',
  '  end',
  '  end',
  '  D("done rallies=" .. rallySeen .. " joins=" .. joinsThisScan)',
  '  local df = io.open(' + AUTOASSAULT_SCAN_DIAG_FILE_LUA + ', "w")',
  '  if df then df:write(table.concat(diag, "\\n")) df:close() end',
  'end)',
  'if not __sr_aa_scan_ok then',
  '  local ef = io.open(' + AUTOASSAULT_SCAN_ERR_FILE_LUA + ', "w")',
  '  if ef then ef:write(tostring(__sr_aa_scan_err or "unknown")) ef:close() end',
  'end',
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
let lastOpenChatText = '';
let lastCityRelocateText = '';
let lastAllianceRallyText = '';
let allianceRallyLastBroadcastMs = 0;
let relocateItemsLastTick = 0;
let lastRelocateItemsText = '';
let relocateItemsLastBroadcastMs = 0;
let lastEvalText = '';
let actionSeen = {};
let actionCatchUntil = 0;
let actionBaselineReady = false;
let liveLuaEnv = ptr(0);
let liveLuaEnvCapturedMs = 0;
let joinCacheEnvMs = 0;
let joinCacheLastInstallAt = 0;
let doStringLogUntil = 0;
let cachedAppFrame = ptr(0);
let mainThreadFlyQueue = [];
let unityMainTid = -1;
let autoHelpEnabled = false;
let autoHelpIntervalMs = 30000;
let autoHelpLastRun = 0;
let lastAutoHelpCfg = '';
let autoHelpAppliedEnabled = null;
let autoAssaultEnabled = false;
let autoAssaultMaxDistance = 500;
let autoAssaultMaxDistanceCreator = 500;
let autoAssaultMaxDistanceTarget = 500;
let autoAssaultSquads = [];
let autoAssaultAllowedNames = [];
let autoAssaultAllowedUserIds = [];
let autoAssaultTargetTypes = [];
let autoAssaultLevelMin = 0;
let autoAssaultLevelMax = 0;
let autoAssaultMinRemainingSec = 5;
let autoAssaultCooldownSec = 3;
let autoAssaultMaxConcurrent = 0;
let autoAssaultDisableAtMs = 0;
let autoAssaultMyPlayerId = 0;
let autoAssaultMyCastleX = 0;
let autoAssaultMyCastleY = 0;
let autoAssaultMyCastleSid = 0;
let lastAutoAssaultCfg = '';
let autoAssaultLastTick = 0;
let autoAssaultCfgPushed = false;
let lastAutoAssaultMatchText = '';
let autoAssaultColdStartPending = true;
let worldReadyCached = false;
let worldReadyAtMs = 0;
let worldReadyLastProbeMs = 0;
let backgroundLuaUnblockedLogged = false;
let assaultGraceUnblockedLogged = false;
let allianceRosterLastTick = 0;
let lastAllianceRosterText = '';
let allianceRosterLastBroadcastMs = 0;
// Принудительно ре-броадкастим ростер не реже, чем раз в это время, даже если он не
// менялся: приложение получает данные, даже если его оверлей подписался ПОСЛЕ первой
// (единственной) отправки или было переустановлено. Иначе ростер «висит» в файле игры,
// но не доходит до приложения, и списки соалийцев/участников остаются пустыми.
const ALLIANCE_ROSTER_REBROADCAST_MS = 30000;

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

const _diagDone = {};
function bridgeLuaStartupReady(now) {
  return liveLuaEnvCapturedMs > 0 && now - liveLuaEnvCapturedMs >= BRIDGE_LUA_STARTUP_DELAY_MS;
}

function tryWorldReadyViaIl2cpp() {
  try {
    attachIl2CppThread();
    let luaCls = findManagedClass('GameFrameWork', 'LuaManager');
    if (luaCls.isNull()) {
      luaCls = findManagedClass('', 'LuaManager');
    }
    if (luaCls.isNull()) return false;
    const initLua = readStaticByte(luaCls, 0x18);
    const worldNet = readStaticByte(luaCls, 0x0a);
    return initLua === 1 && worldNet === 1;
  } catch (e) {
    return false;
  }
}

function markWorldReady(source) {
  worldReadyCached = true;
  worldReadyAtMs = Date.now();
  log('world ready: session loaded (' + source + ')');
}

function tickWorldReadyProbe(now) {
  if (worldReadyCached) return;
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  if (!bridgeLuaStartupReady(now)) return;
  if (now - worldReadyLastProbeMs < 5000) return;
  worldReadyLastProbeMs = now;
  mainThreadFlyQueue.push(function () {
    if (!liveLuaEnv || liveLuaEnv.isNull()) {
      if (tryWorldReadyViaIl2cpp()) markWorldReady('il2cpp pre-lua');
      return;
    }
    writeFileEmpty(WORLD_READY_FILE);
    doStringNow(WORLD_READY_PROBE_LUA);
    let t = readFileUtf8(WORLD_READY_FILE, 8);
    if (t && t.trim() === '1') {
      markWorldReady('lua probe');
      return;
    }
    if (tryWorldReadyViaIl2cpp()) {
      markWorldReady('il2cpp isInitLua+isWorldNetwork');
      return;
    }
    diagOnce('world-not-ready', 'world-ready probe returned: ' + JSON.stringify(t));
  });
}

function bridgeWorldReady(now) {
  if (worldReadyCached) return true;
  tickWorldReadyProbe(now);
  return false;
}

function bridgeAssaultReady(now) {
  if (!bridgeWorldReady(now)) return false;
  if (worldReadyAtMs <= 0) return false;
  if (now - worldReadyAtMs < BRIDGE_ASSAULT_GRACE_MS) return false;
  if (!assaultGraceUnblockedLogged) {
    assaultGraceUnblockedLogged = true;
    log('autoassault unblocked (grace=' + BRIDGE_ASSAULT_GRACE_MS + 'ms after world ready)');
  }
  return true;
}

// Auto-help is event-driven (fires when help data updates). It only needs startup delay +
// world-ready — NOT the 20s hook grace (that blocked help after v50).
function bridgeAutoHelpReady(now) {
  if (!bridgeLuaStartupReady(now)) return false;
  return bridgeWorldReady(now);
}

function bridgeBackgroundLuaReady(now) {
  if (!bridgeWorldReady(now)) return false;
  if (worldReadyAtMs <= 0) return false;
  if (now - worldReadyAtMs < BRIDGE_POST_WORLD_READY_GRACE_MS) return false;
  if (!backgroundLuaUnblockedLogged) {
    backgroundLuaUnblockedLogged = true;
    log(
      'background lua unblocked (grace=' +
        BRIDGE_POST_WORLD_READY_GRACE_MS +
        'ms after world ready)',
    );
  }
  return true;
}

function diagOnce(key, msg) {
  if (_diagDone[key]) return;
  _diagDone[key] = true;
  log('DIAG ' + key + ': ' + msg);
}

const LOG_ROTATE_BYTES = 1024 * 1024;
let logWriteCount = 0;

function truncateLogFile(path) {
  try {
    const f = new File(path, 'w');
    f.write('');
    f.flush();
    f.close();
  } catch (e) {}
}

function maybeRotateLogFiles() {
  if (typeof Java === 'undefined' || !Java.available) return;
  try {
    Java.perform(function () {
      const JFile = Java.use('java.io.File');
      for (let i = 0; i < 2; i++) {
        const path = i === 0 ? LOG : LOG_SDCARD;
        const file = JFile.$new(path);
        if (!file.exists()) continue;
        const len = file.length();
        if (len > LOG_ROTATE_BYTES) truncateLogFile(path);
      }
    });
  } catch (e) {}
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
  logWriteCount++;
  if (logWriteCount === 1 || logWriteCount % 250 === 0) maybeRotateLogFiles();
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

// --- liveLuaEnv robust capture -------------------------------------------------
// The game build only rarely calls the hooked LuaEnv.DoString, so after a game
// restart `liveLuaEnv` could stay null forever and the share-panel hook never
// arms ("В рейд" never appears). Capture the LuaEnv via il2cpp reflection from
// any xLua LuaBase-derived object (the LuaFunction caught by the Action hook, or
// the LuaFunction/LuaEnv passed to the flyWorld* setters) instead of relying on
// DoString. CRITICAL: every helper here touches il2cpp and MUST only ever be
// called from a Unity thread (inside a game hook's onEnter/onLeave) — calling
// il2cpp reflection from the background Frida timer thread aborts the process.
let luaEnvFieldOffsetCache = -1;

function il2cppClassName(obj) {
  if (!obj || obj.isNull()) return null;
  // Reject obvious non-heap values (e.g. small ints stored in adjacent fields).
  if (obj.compare(ptr('0x100000')) < 0) return null;
  if (!obj.and(7).isNull()) return null;
  const objGetClass = nativeFn('il2cpp_object_get_class', 'pointer', ['pointer']);
  const classGetName = nativeFn('il2cpp_class_get_name', 'pointer', ['pointer']);
  if (!objGetClass || !classGetName) {
    diagOnce('classname-fns', 'il2cppClassName missing fns ogc=' + !!objGetClass + ' cgn=' + !!classGetName);
    return null;
  }
  try {
    const c = objGetClass(obj);
    if (!c || c.isNull()) return null;
    const np = classGetName(c);
    if (!np || np.isNull()) return null;
    return np.readCString();
  } catch (e) {
    diagOnce('classname-exc', 'il2cppClassName threw: ' + e);
    return null;
  }
}

function luaEnvFieldOffset(funcObj) {
  if (luaEnvFieldOffsetCache >= 0) return luaEnvFieldOffsetCache;
  const objGetClass = nativeFn('il2cpp_object_get_class', 'pointer', ['pointer']);
  const getFieldFromName = nativeFn('il2cpp_class_get_field_from_name', 'pointer', ['pointer', 'pointer']);
  const fieldGetOffset = nativeFn('il2cpp_field_get_offset', 'uint32', ['pointer']);
  const classGetParent = nativeFn('il2cpp_class_get_parent', 'pointer', ['pointer']);
  if (!objGetClass || !getFieldFromName || !fieldGetOffset) return -1;
  try {
    let cls = objGetClass(funcObj);
    const nameBuf = Memory.allocUtf8String('luaEnv');
    for (let depth = 0; depth < 8 && cls && !cls.isNull(); depth++) {
      const field = getFieldFromName(cls, nameBuf);
      if (field && !field.isNull()) {
        luaEnvFieldOffsetCache = fieldGetOffset(field);
        return luaEnvFieldOffsetCache;
      }
      cls = classGetParent ? classGetParent(cls) : ptr(0);
    }
  } catch (e) {}
  return -1;
}

// Generic: enumerate the instance fields of a managed object and return the
// first field value whose class is a XLua.LuaEnv. Unity/managed-thread only.
function findLuaEnvInFields(obj) {
  if (!obj || obj.isNull()) return null;
  const objGetClass = nativeFn('il2cpp_object_get_class', 'pointer', ['pointer']);
  const classGetFields = nativeFn('il2cpp_class_get_fields', 'pointer', ['pointer', 'pointer']);
  const fieldGetOffset = nativeFn('il2cpp_field_get_offset', 'uint32', ['pointer']);
  const fieldGetFlags = nativeFn('il2cpp_field_get_flags', 'int', ['pointer']);
  const classGetParent = nativeFn('il2cpp_class_get_parent', 'pointer', ['pointer']);
  if (!objGetClass || !classGetFields || !fieldGetOffset) return null;
  let cls;
  try { cls = objGetClass(obj); } catch (e) { return null; }
  for (let depth = 0; depth < 6 && cls && !cls.isNull(); depth++) {
    const iter = Memory.alloc(Process.pointerSize);
    iter.writePointer(ptr(0));
    let field;
    try {
      while (!(field = classGetFields(cls, iter)).isNull()) {
        if (fieldGetFlags) {
          const fl = fieldGetFlags(field);
          if (fl & 0x10) continue; // FIELD_ATTRIBUTE_STATIC — skip
        }
        let off;
        try { off = fieldGetOffset(field); } catch (e) { continue; }
        if (off < 0x10 || off > 0x4000) continue;
        try {
          const val = obj.add(off).readPointer();
          const cn = il2cppClassName(val);
          if (cn && cn.indexOf('LuaEnv') >= 0) return val;
        } catch (e) {}
      }
    } catch (e) {}
    cls = classGetParent ? classGetParent(cls) : ptr(0);
  }
  return null;
}

// Capture liveLuaEnv from a managed object that is either a XLua.LuaEnv itself,
// an xLua LuaBase-derived object (LuaFunction/LuaTable) holding `luaEnv`, or any
// manager (LuaManager/AppFrame) that references the LuaEnv in one of its fields.
// Unity/managed-thread only (never the Frida timer thread).
function captureLuaEnvFrom(obj, tag) {
  if (liveLuaEnv && !liveLuaEnv.isNull()) return true;
  if (!obj || obj.isNull()) return false;
  // Candidate may already be the LuaEnv (e.g. flyWorldLua setter value).
  const ownCn = il2cppClassName(obj);
  if (ownCn && ownCn.indexOf('LuaEnv') >= 0) {
    liveLuaEnv = obj;
    log('liveLuaEnv captured (direct) via ' + tag + ' class=' + ownCn);
    return true;
  }
  // Fast path: a named `luaEnv` field (LuaBase.luaEnv).
  try {
    const off = luaEnvFieldOffset(obj);
    if (off >= 0) {
      const cand = obj.add(off).readPointer();
      const cn = il2cppClassName(cand);
      if (cn && cn.indexOf('LuaEnv') >= 0) {
        liveLuaEnv = cand;
        log('liveLuaEnv captured via ' + tag + '.luaEnv off=0x' + off.toString(16) + ' class=' + cn);
        return true;
      }
    }
  } catch (e) {}
  // Generic: scan all instance fields for a LuaEnv-typed value.
  const found = findLuaEnvInFields(obj);
  if (found && !found.isNull()) {
    liveLuaEnv = found;
    log('liveLuaEnv captured via ' + tag + ' field-scan class=' + il2cppClassName(found));
    return true;
  }
  return false;
}

// LuaManager is a static class on this build (SimpleInstrSend/RequireLua/FormatKXY
// are static), so the XLua.LuaEnv lives in one of its STATIC fields. Resolve the
// class, enumerate its static fields and grab the LuaEnv-typed one. Reflection
// (assembly enumeration) — Unity/managed-thread only.
function captureLuaEnvFromClassStatics(ns, name, tag) {
  if (liveLuaEnv && !liveLuaEnv.isNull()) return true;
  diagOnce('cs-enter:' + name, 'captureLuaEnvFromClassStatics entered');
  let cls;
  try { cls = findManagedClass(ns, name); } catch (e) { diagOnce('cs-findcls:' + name, 'findManagedClass threw: ' + e); return false; }
  if (!cls || cls.isNull()) { diagOnce('statics:' + name, 'class NOT found (ns=' + ns + ')'); return false; }
  const classInit = nativeFn('il2cpp_runtime_class_init', 'void', ['pointer']);
  if (classInit) { try { classInit(cls); } catch (e) {} }
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  const classGetFields = nativeFn('il2cpp_class_get_fields', 'pointer', ['pointer', 'pointer']);
  const fieldGetOffset = nativeFn('il2cpp_field_get_offset', 'uint32', ['pointer']);
  const fieldGetFlags = nativeFn('il2cpp_field_get_flags', 'int', ['pointer']);
  if (!getStaticFieldData || !classGetFields || !fieldGetOffset || !fieldGetFlags) {
    diagOnce('cs-fns:' + name, 'missing fn gsfd=' + !!getStaticFieldData + ' cgf=' + !!classGetFields + ' fgo=' + !!fieldGetOffset + ' fgf=' + !!fieldGetFlags);
    return false;
  }
  const staticData = getStaticFieldData(cls);
  if (!staticData || staticData.isNull()) { diagOnce('statics:' + name, 'no static field data'); return false; }
  const iter = Memory.alloc(Process.pointerSize);
  iter.writePointer(ptr(0));
  let field;
  const diagNames = [];
  try {
    while (!(field = classGetFields(cls, iter)).isNull()) {
      const fl = fieldGetFlags(field);
      if (!(fl & 0x10)) continue; // only FIELD_ATTRIBUTE_STATIC
      let off;
      try { off = fieldGetOffset(field); } catch (e) { continue; }
      if (off < 0 || off > 0x4000) continue;
      try {
        const val = staticData.add(off).readPointer();
        const cn = il2cppClassName(val);
        if (cn) diagNames.push('0x' + off.toString(16) + ':' + cn);
        if (cn && cn.indexOf('LuaEnv') >= 0) {
          liveLuaEnv = val;
          log('liveLuaEnv captured via ' + tag + ' static off=0x' + off.toString(16) + ' class=' + cn);
          return true;
        }
        // A static LuaFunction/LuaTable (e.g. GameCommandBase.flyWorldFun) holds luaEnv.
        if (cn && (cn.indexOf('LuaFunction') >= 0 || cn.indexOf('LuaTable') >= 0 || cn.indexOf('LuaManager') >= 0)) {
          if (captureLuaEnvFrom(val, tag + '/static:' + cn)) return true;
        }
      } catch (e) {}
    }
  } catch (e) {}
  diagOnce('statics:' + name, 'static objs=[' + diagNames.join(', ') + ']');
  return false;
}

// Try every reliable static source for the LuaEnv. Unity/managed-thread only.
function captureLuaEnvViaReflection(tag) {
  if (liveLuaEnv && !liveLuaEnv.isNull()) return true;
  diagOnce('refl-enter', 'captureLuaEnvViaReflection entered (tag=' + tag + ')');
  try {
    if (captureLuaEnvFromClassStatics('GameFrameWork', 'LuaManager', tag + '/LuaManager')) return true;
    if (captureLuaEnvFromClassStatics('', 'LuaManager', tag + '/LuaManager2')) return true;
    if (captureLuaEnvFromClassStatics('', 'GameCommandBase', tag + '/GameCommandBase')) return true;
  } catch (e) {
    diagOnce('refl-exc', 'reflection threw: ' + e);
  }
  return false;
}

function installActionCatchHook() {
  if (installActionCatchHook.done) return;
  const base = libBase();
  if (!base) return;
  installActionCatchHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaFunction_Action), {
      onEnter(args) {
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvFrom(args[0], 'Action');
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

// LuaManager.RequireLua(string) is static and called whenever a Lua module/UI
// panel loads (incl. the share panel). Its return value is an xLua LuaTable that
// holds a `luaEnv` field, so onLeave is a reliable, Unity-thread place to capture
// the LuaEnv when DoString/Action never fire in this build.
function installRequireLuaHook() {
  if (installRequireLuaHook.done) return;
  const base = libBase();
  if (!base) return;
  installRequireLuaHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaManager_RequireLua), {
      onLeave(retval) {
        if (!liveLuaEnv || liveLuaEnv.isNull()) {
          diagOnce('RequireLua-fire', 'RequireLua fired (retval=' + retval + ' class=' + il2cppClassName(retval) + ')');
          try { captureLuaEnvFrom(retval, 'RequireLua'); } catch (e) { diagOnce('rl-exc1', 'captureLuaEnvFrom threw: ' + e); }
          if (!liveLuaEnv || liveLuaEnv.isNull()) {
            try { captureLuaEnvViaReflection('RequireLua'); } catch (e) { diagOnce('rl-exc2', 'reflection threw: ' + e); }
          }
        }
      },
    });
    log('RequireLua capture hook installed');
  } catch (e) {
    log('RequireLua capture hook failed: ' + e);
  }
}
installRequireLuaHook.done = false;

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
    doStringNow(code);
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
      onEnter(args) {
        // Static setter: args[0] is the LuaFunction value being assigned.
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvFrom(args[0], 'set_flyWorldFun');
      },
      onLeave() {
        log('>>> flyWorldFun SET by game');
        logFlyFields('after set_flyWorldFun');
      },
    });
    Interceptor.attach(base.add(RVA.set_flyWorldLua), {
      onEnter(args) {
        // Static setter: args[0] is the value (LuaEnv or a LuaBase) being assigned.
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvFrom(args[0], 'set_flyWorldLua');
      },
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
        diagOnce('lua-send-fire', 'LuaManager.SimpleInstrSend fired');
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvViaReflection('LuaManager.SimpleInstrSend');
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
        diagOnce('app-send-fire', 'AppFrame.SimpleInstrSend fired (self class=' + il2cppClassName(self) + ')');
        // Networking fires reliably during normal play (incl. Pong) on a managed
        // (il2cpp) thread — a safe place to capture the LuaEnv when Action/DoString
        // don't. Prefer the LuaManager static fields; fall back to scanning self.
        if (!liveLuaEnv || liveLuaEnv.isNull()) {
          if (!captureLuaEnvViaReflection('AppFrame.SimpleInstrSend')) {
            captureLuaEnvFrom(self, 'AppFrame.SimpleInstrSend');
          }
        }
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
  writeFileEmpty(CITY_RELOCATE_FILE);
  writeFileEmpty(CITY_RELOCATE_SDCARD);
  lastTriggerText = '';
  lastCityRelocateText = '';
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
  installRequireLuaHook();
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
  const now = Date.now();
  if (!bridgeLuaStartupReady(now) || !bridgeBackgroundLuaReady(now)) return;
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

// Триггер открытия личного чата с игроком (app пишет {playerId, playerName}).
function pollOpenChatFile() {
  const now = Date.now();
  if (!bridgeLuaStartupReady(now) || !bridgeBackgroundLuaReady(now)) return;
  const paths = [OPEN_CHAT_FILE, OPEN_CHAT_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim()) continue;
      if (text === lastOpenChatText) return;
      lastOpenChatText = text;
      const mid = text.match(/"playerId"\s*:\s*"?([0-9]+)"?/);
      if (!mid) {
        log('open-chat parse failed: ' + text.trim());
        writeFileEmpty(paths[i]);
        return;
      }
      let cname = '';
      const mname = text.match(/"playerName"\s*:\s*"((?:[^"\\]|\\.)*)"/);
      if (mname) {
        cname = mname[1].replace(/\\"/g, '"').replace(/\\\\/g, '\\');
      }
      log('open-chat trigger: pid=' + mid[1] + ' name=' + cname);
      openPrivateChat(mid[1], cname);
      writeFileEmpty(paths[i]);
      lastOpenChatText = '';
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('open-chat poll error: ' + e);
      }
    }
  }
}

// Открыть приватный чат (ЛС) с игроком в игре.
// Реверс (захват реального вызова) подтвердил точку входа на PHS Global v1.0.81:
//   Logic.Datas.Chat.ChatUtils.OpenChatPanel({channelType=3, privatePlayerId=<id>, privatePlayerName=<name>})
//   channelType=3 — приватный канал. Выполняется на главном потоке Unity через runLua.
function openPrivateChat(pid, name) {
  const numId = String(pid).replace(/[^0-9]/g, '');
  if (!numId) return;
  const safeName = String(name || '')
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/[\r\n]/g, ' ');
  const code = [
    'pcall(function()',
    '  local CU = package.loaded["Logic.Datas.Chat.ChatUtils"]',
    '  if not CU or type(CU.OpenChatPanel) ~= "function" then return end',
    '  CU.OpenChatPanel({ channelType = 3, privatePlayerId = ' + numId + ', privatePlayerName = "' + safeName + '" })',
    'end)',
  ].join('\n');
  runLua(code);
}

const RELOCATE_ERR_FILE = '/data/data/com.phs.global/files/squadrelay_relocate_err.txt';
const RELOCATE_RESULT_FILE = '/data/data/com.phs.global/files/squadrelay_relocate_result.json';
const RELOCATE_RESULT_ACTION = 'com.lastasylum.alliance.action.RELOCATE_RESULT';

function sendRelocateResultBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) return;
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) return;
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(RELOCATE_RESULT_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000);
      ctx.sendBroadcast(intent);
      log('relocate-result broadcast -> ' + SHARE_APP_PKG + ' (' + payload.length + 'b)');
    });
  } catch (e) {
    log('relocate-result broadcast failed: ' + e);
  }
}

function runCityRelocateLua(tag, mode, innerLua, opts) {
  const broadcast = !opts || opts.broadcast !== false;
  const onDone = opts && typeof opts.onDone === 'function' ? opts.onDone : null;
  const code = [
    'pcall(function()',
    '  local RF = "' + RELOCATE_RESULT_FILE + '"',
    '  local MODE = "' + mode + '"',
    '  local function writeResult(ok, err)',
    '    local q = string.char(34)',
    '    local e = string.gsub(tostring(err or ""), q, " ")',
    '    e = string.gsub(e, "[\\r\\n]", " ")',
    '    if #e > 240 then e = string.sub(e, 1, 240) end',
    '    local f = io.open(RF, "w")',
    '    if f then',
    '      f:write("{"..q.."ok"..q..":"..(ok and "true" or "false")..","..q.."mode"..q..":"..q..MODE..q..","..q.."error"..q..":"..q..e..q.."}")',
    '      f:close()',
    '    end',
    '  end',
    '  local ok, e = pcall(function()',
    innerLua,
    '  end)',
    '  if ok then writeResult(true, "") else',
    '    local f = io.open("' + RELOCATE_ERR_FILE + '", "a")',
    '    if f then f:write("' + tag + ' "..tostring(e).."\\n") f:close() end',
    '    writeResult(false, e)',
    '  end',
    'end)',
  ].join('\n');
  mainThreadFlyQueue.push(function () {
    writeFileEmpty(RELOCATE_RESULT_FILE);
    const relayFail = function (error) {
      const payload = JSON.stringify({ ok: false, mode: mode, error: error });
      if (onDone) {
        onDone(false, error);
      } else {
        sendRelocateResultBroadcast(payload);
      }
    };
    if (!liveLuaEnv || liveLuaEnv.isNull()) {
      relayFail('lua_env_not_ready');
      return;
    }
    if (!doStringNow(code)) {
      relayFail('lua_exec_failed');
      return;
    }
    let raw = '';
    try {
      raw = readFileUtf8(RELOCATE_RESULT_FILE, 2048).trim();
    } catch (e) {
      log('relocate-result read error: ' + e);
    }
    if (!raw) {
      const fail = JSON.stringify({ ok: false, mode: mode, error: 'no_result' });
      if (onDone) {
        onDone(false, 'no_result');
      } else {
        sendRelocateResultBroadcast(fail);
      }
      return;
    }
    if (onDone) {
      try {
        const parsed = JSON.parse(raw);
        onDone(!!parsed.ok, parsed.error || '');
      } catch (e) {
        onDone(false, 'bad_result');
      }
      return;
    }
    if (broadcast) {
      sendRelocateResultBroadcast(raw);
    }
  });
}

// Прямое перемещение: карта 3×3 (CityRelocationItem) + CityRelocationHandler — как кнопка «Перенос» в игре.
function cityRelocateDirect(x, y, sid) {
  const xi = Math.floor(Number(x));
  const yi = Math.floor(Number(y));
  const si = Math.floor(Number(sid));
  if (!xi || !yi || !si) {
    log('city-relocate direct: invalid coords x=' + x + ' y=' + y + ' sid=' + sid);
    sendRelocateResultBroadcast(
      JSON.stringify({ ok: false, mode: 'direct', error: 'invalid_coords' }),
    );
    return;
  }
  const inner = [
    '    pcall(function() require("Logic.Map.WorldMapEnum") end)',
    '    pcall(function() require("UIs.WorldMapUI.WorldCityRelocationPosWin") end)',
    '    pcall(function() require("Logic.Map.MapLogic.Helper.WorldMapHelper") end)',
    '    local RT = package.loaded["Logic.Map.WorldMapEnum"] and package.loaded["Logic.Map.WorldMapEnum"].RelocationType',
    '    local CITY = RT and RT.CITY or 1',
    '    local x=' + xi + ' local y=' + yi + ' local sid=' + si,
    '    local pt = { x = x, y = y, sid = sid }',
    '    local WH = package.loaded["Logic.Map.MapLogic.Helper.WorldMapHelper"]',
    '    if WH and WH.IsEnable_Relocate then',
    '      local en = WH.IsEnable_Relocate(CITY, x, y, sid)',
    '      if not en then error("relocate_not_enabled") end',
    '    end',
    '    local gmc = _G.GlobalMapCtrlManager',
    '    local wm = gmc and gmc.GetWorldManager and gmc:GetWorldManager()',
    '    local muv = wm and wm.mapUnitsView',
    '    if not muv or not muv.ShowCityRelocationItem then error("no_map_view") end',
    '    if muv.HideCityRelocationItem then pcall(function() muv:HideCityRelocationItem() end) end',
    '    muv:ShowCityRelocationItem(x, y, sid)',
    '    local item = muv.CityRelocationItem',
    '    if not item or not item.SetCellPos then error("no_relocation_item") end',
    '    item:SetCellPos(x, y, sid, CITY)',
    '    if item.CheckCellAvailable then',
    '      local av = item:CheckCellAvailable(x, y, sid)',
    '      if not av then error("cell_blocked") end',
    '    end',
    '    local idx = package.loaded["UIs.WorldMapUI.WorldCityRelocationPosWin"]',
    '    local classIdx = idx and ((getmetatable(idx) or {}).__index or idx)',
    '    if not classIdx or not classIdx.CityRelocationHandler then error("no_handler") end',
    '    local win = {',
    '      relocateType = CITY,',
    '      paramTable = { pt, { point = pt } },',
    '      cellX = x, cellY = y, cellPos = pt, point = pt, targetPoint = pt,',
    '      cityRelocationItem = item,',
    '    }',
    '    setmetatable(win, { __index = classIdx })',
    '    classIdx.CityRelocationHandler(win, CITY)',
    '    if muv.HideCityRelocationItem then pcall(function() muv:HideCityRelocationItem() end) end',
    '    if item.Hide then pcall(function() item:Hide() end) end',
    '    if wm and wm.relocationStatusIsOpen ~= nil then wm.relocationStatusIsOpen = false end',
    '    if WH and WH.RelocateCityUpdateWorldMapView then pcall(function() WH:RelocateCityUpdateWorldMapView() end) end',
  ].join('\n');
  log('city-relocate direct x=' + xi + ' y=' + yi + ' sid=' + si);
  runCityRelocateLua('direct', 'direct', inner);
}

// Перемещение альянса: RequestRallyPointRelocateC2S (проверено в v62).
function cityRelocateAlliance() {
  const inner = [
    '    pcall(function() require("Logic.Proto.Send.union_territory") end)',
    '    pcall(function() require("Logic.Map.WorldMapEnum") end)',
    '    local ut = package.loaded["Logic.Proto.Send.union_territory"]',
    '    if not ut or not ut.RequestRallyPointRelocateC2S then error("no_api") end',
    '    local RT = package.loaded["Logic.Map.WorldMapEnum"] and package.loaded["Logic.Map.WorldMapEnum"].RelocationType',
    '    local ALLY = RT and RT.ALLY_BOSS or 2',
    '    ut.RequestRallyPointRelocateC2S({ relocateType = ALLY, type = ALLY }, {})',
  ].join('\n');
  log('city-relocate alliance');
  runCityRelocateLua('alliance', 'alliance', inner);
}

// Случайное перемещение: UseItemC2S (как кнопка предмета в сумке — захват sr_bag_manual_capture).
function cityRelocateRandom() {
  const inner = [
    '    pcall(function() require("Logic.Proto.Send.item") end)',
    '    pcall(function() require("Logic.Map.MapLogic.Helper.WorldMapHelper") end)',
    '    local si = package.loaded["Logic.Proto.Send.item"]',
    '    if not si or not si.UseItemC2S then error("no_api") end',
    '    local WH = package.loaded["Logic.Map.MapLogic.Helper.WorldMapHelper"]',
    '    if WH and WH.IsInWorldMap and not WH:IsInWorldMap() then error("not_on_world_map") end',
    '    local id = _G.Data and _G.Data.ItemData',
    '    local ic = id and id.itemsCount',
    '    local before = tonumber(ic and ic.item_UseUp_randomMove) or 0',
    '    if before <= 0 then error("no_random_stock") end',
    '    local uuid = nil',
    '    for k, row in pairs((id and id.itemsTab) or {}) do',
    '      if type(row) == "table" and row.itemId == "item_UseUp_randomMove" then',
    '        uuid = row.uuid or k',
    '        break',
    '      end',
    '    end',
    '    if not uuid then error("no_random_uuid") end',
    '    si.UseItemC2S({ items = { { itemId = "item_UseUp_randomMove", uuid = uuid, count = 1 } } })',
  ].join('\n');
  log('city-relocate random UseItemC2S');
  runCityRelocateLua('random', 'random', inner);
}

function sendRelocPanelBroadcast(payload) {
  sendAppPayloadBroadcast(RELOC_PANEL_ACTION, payload);
}

function sendRouteMapClickBroadcast(payload) {
  sendAppPayloadBroadcast(ROUTE_MAP_CLICK_ACTION, payload);
}

function sendRoutePlacementBroadcast(payload) {
  sendAppPayloadBroadcast(ROUTE_PLACEMENT_ACTION, payload);
}

function sendAppPayloadBroadcast(action, payload) {
  if (typeof Java === 'undefined' || !Java.available) {
    log('broadcast skipped (no Java): ' + action);
    return;
  }
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) return;
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(action);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000);
      ctx.sendBroadcast(intent);
      log('broadcast -> ' + action);
    });
  } catch (e) {
    log('broadcast failed ' + action + ': ' + e);
  }
}

function routePlacementStart(x, y, sid) {
  const xi = Math.floor(Number(x));
  const yi = Math.floor(Number(y));
  const si = Math.floor(Number(sid));
  if (!xi || !yi || !si) {
    sendRoutePlacementBroadcast(JSON.stringify({ ok: false, error: 'invalid_coords' }));
    return;
  }
  const inner = [
    '    _G.__sr_route_mode = true',
    '    pcall(function() require("Logic.Map.WorldMapEnum") end)',
    '    pcall(function() require("Logic.Map.MapLogic.Helper.WorldMapHelper") end)',
    '    local RT = package.loaded["Logic.Map.WorldMapEnum"] and package.loaded["Logic.Map.WorldMapEnum"].RelocationType',
    '    local CITY = RT and RT.CITY or 1',
    '    local x=' + xi + ' local y=' + yi + ' local sid=' + si,
    '    local gmc = _G.GlobalMapCtrlManager',
    '    local wm = gmc and gmc.GetWorldManager and gmc:GetWorldManager()',
    '    local muv = wm and wm.mapUnitsView',
    '    if not muv or not muv.ShowCityRelocationItem then error("no_map_view") end',
    '    if muv.HideCityRelocationItem then pcall(function() muv:HideCityRelocationItem() end) end',
    '    muv:ShowCityRelocationItem(x, y, sid)',
    '    local item = muv.CityRelocationItem',
    '    if not item or not item.SetCellPos then error("no_relocation_item") end',
    '    item:SetCellPos(x, y, sid, CITY)',
    '    if wm and wm.relocationStatusIsOpen ~= nil then wm.relocationStatusIsOpen = true end',
  ].join('\n');
  log('route-placement start x=' + xi + ' y=' + yi + ' sid=' + si);
  mainThreadFlyQueue.push(function () {
    runLua(ROUTE_MODE_INSTALL_LUA);
    if (!liveLuaEnv || liveLuaEnv.isNull() || !doStringNow(inner)) {
      sendRoutePlacementBroadcast(JSON.stringify({ ok: false, error: 'lua_failed' }));
    }
  });
}

function routePlacementCancel() {
  const inner = [
    '    _G.__sr_route_mode = false',
    '    _G.__sr_route_pending = nil',
    '    local gmc = _G.GlobalMapCtrlManager',
    '    local wm = gmc and gmc.GetWorldManager and gmc:GetWorldManager()',
    '    local muv = wm and wm.mapUnitsView',
    '    if muv and muv.HideCityRelocationItem then pcall(function() muv:HideCityRelocationItem() end) end',
    '    local item = muv and muv.CityRelocationItem',
    '    if item and item.Hide then pcall(function() item:Hide() end) end',
    '    if wm and wm.relocationStatusIsOpen ~= nil then wm.relocationStatusIsOpen = false end',
  ].join('\n');
  log('route-placement cancel');
  mainThreadFlyQueue.push(function () {
    if (liveLuaEnv && !liveLuaEnv.isNull()) doStringNow(inner);
  });
}

function routePlacementConfirm() {
  const inner = [
    '    local gmc = _G.GlobalMapCtrlManager',
    '    local wm = gmc and gmc.GetWorldManager and gmc:GetWorldManager()',
    '    local muv = wm and wm.mapUnitsView',
    '    local item = muv and muv.CityRelocationItem',
    '    if not item then error("no_item") end',
    '    local x = tonumber(item.cellX or item.x or (item.cellPos and item.cellPos.x)) or 0',
    '    local y = tonumber(item.cellY or item.y or (item.cellPos and item.cellPos.y)) or 0',
    '    local sid = tonumber(item.sid or (item.cellPos and item.cellPos.sid)) or 0',
    '    if item.GetCellPos then',
    '      local ok, cx, cy, cs = pcall(function() return item:GetCellPos() end)',
    '      if ok and cx and cy then x, y = tonumber(cx) or x, tonumber(cy) or y end',
    '      if ok and cs then sid = tonumber(cs) or sid end',
    '    end',
    '    if x <= 0 or y <= 0 or sid <= 0 then error("bad_coords") end',
    '    _G.__sr_route_pending = { x = x, y = y, sid = sid }',
    '    local RF = "' + ROUTE_PLACEMENT_RESULT_FILE + '"',
    '    local f = io.open(RF, "w")',
    '    if f then f:write("{\"ok\":true,\"x\":"..x..",\"y\":"..y..",\"sid\":"..sid.."}") f:close() end',
  ].join('\n');
  log('route-placement confirm');
  mainThreadFlyQueue.push(function () {
    writeFileEmpty(ROUTE_PLACEMENT_RESULT_FILE);
    if (!liveLuaEnv || liveLuaEnv.isNull() || !doStringNow(inner)) {
      sendRoutePlacementBroadcast(JSON.stringify({ ok: false, error: 'lua_failed' }));
      return;
    }
    let raw = '';
    try {
      raw = readFileUtf8(ROUTE_PLACEMENT_RESULT_FILE, 512).trim();
    } catch (e) {}
    if (raw) {
      sendRoutePlacementBroadcast(raw);
    } else {
      sendRoutePlacementBroadcast(JSON.stringify({ ok: false, error: 'no_result' }));
    }
  });
}

function refreshRouteMarkersFromFile() {
  const paths = [ROUTE_MARKERS_SDCARD, ROUTE_MARKERS_FILE];
  let text = '';
  for (let i = 0; i < paths.length; i++) {
    try {
      text = readFileUtf8(paths[i], 256 * 1024).trim();
      if (text) break;
    } catch (e) {}
  }
  if (!text) return;
  let markers = [];
  try {
    const parsed = JSON.parse(text);
    if (parsed && Array.isArray(parsed.markers)) markers = parsed.markers;
  } catch (e) {
    log('route-markers parse failed: ' + e);
    return;
  }
  const rows = [];
  for (let i = 0; i < markers.length; i++) {
    const m = markers[i];
    if (!m || !m.x || !m.y || !m.sid) continue;
    const label = String(m.label || m.routeName || 'route').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    rows.push(
      '{id="' +
        String(m.id || i) +
        '",x=' +
        Math.floor(Number(m.x)) +
        ',y=' +
        Math.floor(Number(m.y)) +
        ',sid=' +
        Math.floor(Number(m.sid)) +
        ',label="' +
        label +
        '"}',
    );
  }
  const lua = [
    'pcall(function()',
    '  _G.__sr_route_markers = {' + rows.join(',') + '}',
    '  _G.__sr_route_labels = {}',
    '  for _, m in ipairs(_G.__sr_route_markers) do',
    '    if m.label then _G.__sr_route_labels[m.id or (tostring(m.x)..":"..tostring(m.y))] = m.label end',
    '  end',
    '  _G.__sr_route_mode = false',
    '  local gmc = _G.GlobalMapCtrlManager',
    '  local wm = gmc and gmc.GetWorldManager and gmc:GetWorldManager()',
    '  local muv = wm and wm.mapUnitsView',
    '  if muv and muv.HideCityRelocationItem then pcall(function() muv:HideCityRelocationItem() end) end',
    '  if wm and wm.relocationStatusIsOpen ~= nil then wm.relocationStatusIsOpen = false end',
    'end)',
  ].join('\n');
  mainThreadFlyQueue.push(function () {
    if (liveLuaEnv && !liveLuaEnv.isNull()) doStringNow(lua);
    log('route-markers refresh count=' + rows.length);
  });
}

function pollRoutePlacementFile() {
  const now = Date.now();
  if (!bridgeAutoHelpReady(now)) return;
  const paths = [ROUTE_PLACEMENT_FILE, ROUTE_PLACEMENT_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim()) continue;
      const modeM = text.match(/"mode"\s*:\s*"([^"]+)"/);
      const mode = modeM ? modeM[1] : '';
      log('route-placement trigger: ' + text.trim());
      if (mode === 'start') {
        const mx = text.match(/"x"\s*:\s*(-?\d+)/);
        const my = text.match(/"y"\s*:\s*(-?\d+)/);
        const ms = text.match(/"sid"\s*:\s*(-?\d+)/);
        if (mx && my && ms) {
          routePlacementStart(parseInt(mx[1], 10), parseInt(my[1], 10), parseInt(ms[1], 10));
        }
      } else if (mode === 'cancel') {
        routePlacementCancel();
      } else if (mode === 'confirm') {
        routePlacementConfirm();
      }
      writeFileEmpty(paths[i]);
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('route-placement poll error: ' + e);
      }
    }
  }
}

let relocPanelHookOk = false;
let lastRelocPanelInstallAt = 0;
let lastRelocPanelText = '';
let lastRouteMapClickText = '';

function pollRouteMapClickFile() {
  try {
    const text = readFileUtf8(ROUTE_MAP_CLICK_FILE, 512).trim();
    if (!text || text === lastRouteMapClickText) return;
    lastRouteMapClickText = text;
    log('route-map click: ' + text);
    sendRouteMapClickBroadcast(text);
    writeFileEmpty(ROUTE_MAP_CLICK_FILE);
    lastRouteMapClickText = '';
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('route-map click poll error: ' + e);
    }
  }
}

function maybeInstallRelocPanelHook() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeLuaStartupReady(now) || !bridgeBackgroundLuaReady(now)) return;
  const interval = relocPanelHookOk ? 2000 : 100;
  if (now - lastRelocPanelInstallAt < interval) return;
  lastRelocPanelInstallAt = now;
  runLua(RELOC_PANEL_HOOK_LUA);
  runLua(ROUTE_MODE_INSTALL_LUA);
  if (!relocPanelHookOk) {
    const ok = readFileUtf8(RELOC_PANEL_OK_FILE);
    if (ok && ok.indexOf('ok') >= 0) {
      relocPanelHookOk = true;
      log('reloc-panel hook armed (re-arming every 2s)');
    }
  }
}

function pollRelocPanelFile() {
  const paths = [RELOC_PANEL_SDCARD, RELOC_PANEL_FILE];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim() || text === lastRelocPanelText) return;
      lastRelocPanelText = text;
      log('reloc-panel payload: ' + text.trim());
      sendRelocPanelBroadcast(text.trim());
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('reloc-panel poll error: ' + e);
      }
    }
  }
}

let lastRouteMarkersPulse = '';
let lastRouteMarkerStartupPullAt = 0;

function tickRouteMarkerStartupPull(nowMs) {
  if (nowMs - lastRouteMarkerStartupPullAt < 8000) return;
  if (!bridgeBackgroundLuaReady(nowMs)) return;
  lastRouteMarkerStartupPullAt = nowMs;
  refreshRouteMarkersFromFile();
}

function pollRouteMarkersPulse() {
  const paths = [ROUTE_MARKERS_PULSE_SDCARD, ROUTE_MARKERS_PULSE_FILE];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim() || text === lastRouteMarkersPulse) return;
      lastRouteMarkersPulse = text;
      writeFileEmpty(paths[i]);
      refreshRouteMarkersFromFile();
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('route-markers pulse error: ' + e);
      }
    }
  }
}

function pollCityRelocateFile() {
  const now = Date.now();
  if (!bridgeAutoHelpReady(now)) return;
  const paths = [CITY_RELOCATE_FILE, CITY_RELOCATE_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim()) continue;
      const modeM = text.match(/"mode"\s*:\s*"([^"]+)"/);
      const mode = modeM ? modeM[1] : '';
      log('city-relocate trigger: ' + text.trim());
      if (mode === 'alliance') {
        cityRelocateAlliance();
      } else if (mode === 'random') {
        cityRelocateRandom();
      } else if (mode === 'direct') {
        const mx = text.match(/"x"\s*:\s*(-?\d+)/);
        const my = text.match(/"y"\s*:\s*(-?\d+)/);
        const ms = text.match(/"sid"\s*:\s*(-?\d+)/);
        if (mx && my && ms) {
          cityRelocateDirect(parseInt(mx[1], 10), parseInt(my[1], 10), parseInt(ms[1], 10));
        } else {
          log('city-relocate direct parse failed: ' + text.trim());
        }
      } else {
        log('city-relocate unknown mode: ' + mode);
      }
      writeFileEmpty(paths[i]);
      lastCityRelocateText = '';
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('city-relocate poll error: ' + e);
      }
    }
  }
}

// Канал произвольного Lua для отладки/реверса: выполняем код из файла (он сам пишет результат).
// Eval runs after login + LuaEnv (same gate as relocate-items), not the +20s post-world grace.
function pollEvalFile() {
  const now = Date.now();
  if (!bridgeLuaStartupReady(now)) return;
  const paths = [EVAL_FILE, EVAL_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i], 256 * 1024);
      if (!text || !text.trim()) continue;
      if (text === lastEvalText) return;
      lastEvalText = text;
      log('eval run: len=' + text.length);
      runLua(text);
      writeFileEmpty(paths[i]);
      lastEvalText = '';
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('eval poll error: ' + e);
      }
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
      const wasEnabled = autoHelpEnabled;
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
      // Install hook immediately when enabled (incl. config sync on game entry).
      if (autoHelpEnabled && !wasEnabled) {
        autoHelpLastRun = 0;
      } else if (!autoHelpEnabled) {
        autoHelpLastRun = Date.now();
      }
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
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeAutoHelpReady(now)) {
    diagOnce('autohelp-wait', 'autohelp waiting: startup=' + bridgeLuaStartupReady(now) + ' world=' + worldReadyCached);
    return;
  }
  if (autoHelpEnabled) {
    if (now - autoHelpLastRun < autoHelpIntervalMs) return;
    autoHelpLastRun = now;
    autoHelpAppliedEnabled = true;
    try {
      runLua(AUTOHELP_INSTALL_LUA);
      mainThreadFlyQueue.push(function () {
        const ok = readFileUtf8(AUTOHELP_OK_FILE, 8);
        if (ok && ok.indexOf('ok') >= 0) {
          diagOnce('autohelp-armed', 'autohelp hook armed (event-driven UnionHelpAllC2S)');
        } else {
          log('autohelp hook not confirmed (AllianceData.help missing or not loaded yet)');
        }
      });
    } catch (e) {
      log('autohelp install error: ' + e);
    }
  } else {
    // Disabled: tell the already-installed wrappers to stop sending (run once per off-transition).
    if (autoHelpAppliedEnabled === false) return;
    autoHelpAppliedEnabled = false;
    try {
      runLua(AUTOHELP_DISABLE_LUA);
    } catch (e) {
      log('autohelp disable error: ' + e);
    }
  }
}

function luaEscape(s) {
  return String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

function readSelfPlayerForCfg() {
  const out = {
    id: autoAssaultMyPlayerId,
    cx: autoAssaultMyCastleX,
    cy: autoAssaultMyCastleY,
    cs: autoAssaultMyCastleSid,
  };
  if (out.id > 0 && out.cx > 0 && out.cy > 0) return out;
  try {
    const text = readFileUtf8(SELF_PLAYER_FILE, 512);
    if (!text || !text.trim()) return out;
    const o = JSON.parse(text);
    if (parseInt(o.id, 10) > 0) out.id = parseInt(o.id, 10) || 0;
    if (parseInt(o.x, 10) > 0) out.cx = parseInt(o.x, 10) || 0;
    if (parseInt(o.y, 10) > 0) out.cy = parseInt(o.y, 10) || 0;
    out.cs = parseInt(o.sid, 10) || 0;
  } catch (e) {}
  return out;
}

function buildAutoAssaultCfgLua() {
  const self = readSelfPlayerForCfg();
  const squadParts = autoAssaultSquads.map(function (s) {
    return '{index=' + s.index + ',powerMin=' + s.powerMin + ',powerMax=' + s.powerMax + '}';
  });
  const nameParts = autoAssaultAllowedNames.map(function (n) {
    return '"' + luaEscape(n) + '"';
  });
  const typeParts = autoAssaultTargetTypes.map(function (t) {
    return '"' + luaEscape(t) + '"';
  });
  const idParts = autoAssaultAllowedUserIds.map(function (id) {
    return '"' + luaEscape(String(id)) + '"';
  });
  return (
    'pcall(function() _G.__sr_aa_cfg={enabled=' +
    (autoAssaultEnabled ? 'true' : 'false') +
    ',maxDistance=' +
    autoAssaultMaxDistance +
    ',maxDistanceCreator=' +
    autoAssaultMaxDistanceCreator +
    ',maxDistanceTarget=' +
    autoAssaultMaxDistanceTarget +
    ',minRemainingSec=' +
    autoAssaultMinRemainingSec +
    ',levelMin=' +
    autoAssaultLevelMin +
    ',levelMax=' +
    autoAssaultLevelMax +
    ',maxConcurrent=' +
    autoAssaultMaxConcurrent +
    ',disableAtEpochMs=' +
    autoAssaultDisableAtMs +
    ',joinEnabled=' +
    (AUTOASSAULT_JOIN_ENABLED ? 'true' : 'false') +
    ',targetTypes={' +
    typeParts.join(',') +
    '},allowedNames={' +
    nameParts.join(',') +
    '},allowedUserIds={' +
    idParts.join(',') +
    '},squads={' +
    squadParts.join(',') +
    '},myPlayerId=' +
    self.id +
    ',myCastleX=' +
    self.cx +
    ',myCastleY=' +
    self.cy +
    ',myCastleSid=' +
    self.cs +
    '}} end)'
  );
}

function resetAutoAssaultBridgeState() {
  // In-memory only: do NOT write {"enabled":false} to the config file on bridge start.
  // That stub was polled as a real config update and disabled scans until the app re-synced.
  autoAssaultEnabled = false;
  lastAutoAssaultCfg = '';
  autoAssaultColdStartPending = true;
  worldReadyCached = false;
  worldReadyAtMs = 0;
  backgroundLuaUnblockedLogged = false;
  assaultGraceUnblockedLogged = false;
  worldReadyLastProbeMs = 0;
  writeFileEmpty(WORLD_READY_FILE);
  ensureAutoAssaultScanFile.done = false;
}

function ensureAutoAssaultScanFile() {
  try {
    const f = new File(AUTOASSAULT_SCAN_FILE, 'w');
    f.write(AUTOASSAULT_SCAN_LUA);
    f.flush();
    f.close();
    ensureAutoAssaultScanFile.done = true;
  } catch (e) {
    log('autoassault scan file write failed: ' + e);
  }
}
ensureAutoAssaultScanFile.done = false;

function pollAutoAssaultConfig() {
  const paths = [AUTOASSAULT_FILE, AUTOASSAULT_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i], AUTOASSAULT_CFG_READ_MAX);
      if (!text || !text.trim()) continue;
      if (text === lastAutoAssaultCfg) return;
      lastAutoAssaultCfg = text;
      autoAssaultCfgPushed = false;
      let cfg;
      try {
        cfg = JSON.parse(text);
      } catch (e) {
        log('autoassault config parse failed: ' + e);
        return;
      }
      autoAssaultEnabled = !!cfg.enabled;
      autoAssaultColdStartPending = false;
      autoAssaultMaxDistance = parseInt(cfg.maxDistance, 10) || 500;
      const legDist = autoAssaultMaxDistance;
      autoAssaultMaxDistanceCreator =
        cfg.maxDistanceCreator !== undefined && cfg.maxDistanceCreator !== null
          ? parseInt(cfg.maxDistanceCreator, 10) || legDist
          : legDist;
      autoAssaultMaxDistanceTarget =
        cfg.maxDistanceTarget !== undefined && cfg.maxDistanceTarget !== null
          ? parseInt(cfg.maxDistanceTarget, 10) || legDist
          : legDist;
      autoAssaultMinRemainingSec = parseInt(cfg.minRemainingSec, 10);
      if (isNaN(autoAssaultMinRemainingSec)) autoAssaultMinRemainingSec = 5;
      autoAssaultCooldownSec = parseInt(cfg.cooldownSec, 10) || 3;
      autoAssaultLevelMin = parseInt(cfg.levelMin, 10) || 0;
      autoAssaultLevelMax = parseInt(cfg.levelMax, 10) || 0;
      autoAssaultMaxConcurrent = parseInt(cfg.maxConcurrent, 10) || 0;
      autoAssaultDisableAtMs = parseInt(cfg.disableAtEpochMs, 10) || 0;
      autoAssaultMyPlayerId = parseInt(cfg.myPlayerId, 10) || 0;
      autoAssaultMyCastleX = parseInt(cfg.myCastleX, 10) || 0;
      autoAssaultMyCastleY = parseInt(cfg.myCastleY, 10) || 0;
      autoAssaultMyCastleSid = parseInt(cfg.myCastleSid, 10) || 0;
      autoAssaultTargetTypes = [];
      if (cfg.targetTypes && cfg.targetTypes.length) {
        for (let t = 0; t < cfg.targetTypes.length; t++) {
          if (cfg.targetTypes[t]) autoAssaultTargetTypes.push(String(cfg.targetTypes[t]));
        }
      }
      autoAssaultSquads = [];
      if (cfg.squads && cfg.squads.length) {
        for (let j = 0; j < cfg.squads.length; j++) {
          const s = cfg.squads[j];
          if (!s) continue;
          autoAssaultSquads.push({
            index: parseInt(s.index, 10) || 0,
            powerMin: parseInt(s.powerMin, 10) || 0,
            powerMax: parseInt(s.powerMax, 10) || 999999999,
          });
        }
      }
      autoAssaultAllowedNames = [];
      if (cfg.allowedNames && cfg.allowedNames.length) {
        for (let k = 0; k < cfg.allowedNames.length; k++) {
          const n = cfg.allowedNames[k];
          if (n && String(n).trim()) autoAssaultAllowedNames.push(String(n).trim());
        }
      }
      autoAssaultAllowedUserIds = [];
      if (cfg.allowedUserIds && cfg.allowedUserIds.length) {
        for (let u = 0; u < cfg.allowedUserIds.length; u++) {
          const id = cfg.allowedUserIds[u];
          if (id !== undefined && id !== null && String(id).trim()) {
            autoAssaultAllowedUserIds.push(String(id).trim());
          }
        }
      }
      log(
        'autoassault config: enabled=' +
          autoAssaultEnabled +
          ' maxDistC=' +
          autoAssaultMaxDistanceCreator +
          ' maxDistT=' +
          autoAssaultMaxDistanceTarget +
          ' squads=' +
          autoAssaultSquads.length +
          ' names=' +
          autoAssaultAllowedNames.length +
          ' userIds=' +
          autoAssaultAllowedUserIds.length +
          ' myPid=' +
          autoAssaultMyPlayerId,
      );
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('autoassault config poll error: ' + e);
      }
    }
  }
}

function tickAutoAssault() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeLuaStartupReady(now)) {
    diagOnce('aa-wait-startup', 'autoassault waiting: startup delay (' + BRIDGE_LUA_STARTUP_DELAY_MS + 'ms)');
    return;
  }
  if (!bridgeAssaultReady(now)) {
    diagOnce(
      'aa-wait-grace',
      'autoassault waiting: world=' +
        worldReadyCached +
        ' graceMs=' +
        (worldReadyAtMs > 0 ? now - worldReadyAtMs : -1) +
        '/' +
        BRIDGE_ASSAULT_GRACE_MS,
    );
    tickWorldReadyProbe(now);
    return;
  }
  pollAutoAssaultConfig();
  // Re-arm join-cache hook periodically (idempotent in lua). The game recreates its Lua
  // state during login/scene transitions, wiping _G.__sr_aa_cfg and TOP.__sr_dsm_orig; a
  // one-shot install keyed only on liveLuaEnvCapturedMs never runs again after that.
  if (now - joinCacheLastInstallAt >= 2000) {
    joinCacheLastInstallAt = now;
    try {
      runLua(INSTALL_JOIN_CACHE_LUA);
    } catch (e) {
      log('join cache install error: ' + e);
    }
  }
  if (!autoAssaultEnabled) {
    diagOnce('aa-disabled', 'autoassault scan skipped: enabled=false');
    return;
  }
  if (autoAssaultDisableAtMs > 0 && Date.now() >= autoAssaultDisableAtMs) return;
  const cooldownMs = Math.max(autoAssaultCooldownSec * 1000, AUTOASSAULT_SCAN_INTERVAL_MS);
  if (now - autoAssaultLastTick < cooldownMs) return;
  autoAssaultLastTick = now;
  try {
    ensureAutoAssaultScanFile();
    mainThreadFlyQueue.push(function () {
      const scanOk = doStringNow(AUTOASSAULT_SCAN_RUN_LUA);
      if (!scanOk) {
        log('autoassault scan run failed (DoString)');
      }
      const scanErr = readFileUtf8(AUTOASSAULT_SCAN_ERR_FILE, 4096);
      if (scanErr && scanErr.trim()) {
        log('autoassault scan lua error: ' + scanErr.trim().substring(0, 400));
        writeFileEmpty(AUTOASSAULT_SCAN_ERR_FILE);
      }
      const scanDiag = readFileUtf8(AUTOASSAULT_SCAN_DIAG_FILE, 8192);
      if (scanDiag && scanDiag.trim()) {
        log('autoassault scan diag:\n' + scanDiag.trim().substring(0, 1200));
      }
      pollAutoAssaultMatch();
    });
    log('autoassault scan queued');
  } catch (e) {
    log('autoassault scan error: ' + e);
  }
}

// Lua writes the latest matched rally to a file; read it and forward to the SquadRelay app
// (so the overlay shows a "recent auto-joins" log). Pure read of the game-private file.
function pollAutoAssaultMatch() {
  try {
    const text = readFileUtf8(AUTOASSAULT_MATCH_FILE);
    if (!text || !text.trim() || text === lastAutoAssaultMatchText) return;
    lastAutoAssaultMatchText = text;
    log('autoassault match: ' + text.trim());
    sendAssaultJoinBroadcast(text.trim());
    writeFileEmpty(AUTOASSAULT_MATCH_FILE);
    lastAutoAssaultMatchText = '';
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('autoassault match poll error: ' + e);
    }
  }
}

function sendAssaultJoinBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) return;
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) return;
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(ASSAULT_JOIN_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000); // FLAG_RECEIVER_FOREGROUND
      ctx.sendBroadcast(intent);
      log('assault-join broadcast -> ' + SHARE_APP_PKG);
    });
  } catch (e) {
    log('assault-join broadcast failed: ' + e);
  }
}

// Периодически снимает ростер альянса из игры и шлёт его в приложение (game → app),
// только при изменении содержимого, чтобы не спамить broadcast'ами.
function tickAllianceRoster() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeLuaStartupReady(now) || !bridgeAssaultReady(now)) return;
  if (now - allianceRosterLastTick < ALLIANCE_ROSTER_SCAN_INTERVAL_MS) return;
  allianceRosterLastTick = now;
  try {
    runLua(ALLIANCE_ROSTER_LUA);
  } catch (e) {
    log('alliance roster scan error: ' + e);
    return;
  }
  try {
    // Лимит по умолчанию у readFileUtf8 — 4096 байт. Ростер крупного альянса
    // (50+ участников) занимает ~8–16 КБ, поэтому читаем с большим запасом,
    // иначе функция вернёт '' и broadcast молча не уйдёт.
    const text = readFileUtf8(ALLIANCE_ROSTER_FILE, 512 * 1024);
    if (!text || !text.trim() || text.trim() === '[]') return;
    const changed = text !== lastAllianceRosterText;
    const due = now - allianceRosterLastBroadcastMs >= ALLIANCE_ROSTER_REBROADCAST_MS;
    if (!changed && !due) return;
    lastAllianceRosterText = text;
    allianceRosterLastBroadcastMs = now;
    sendAllianceRosterBroadcast(text.trim());
    pollAllianceRallyFile(now, false);
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('alliance roster poll error: ' + e);
    }
  }
}

function sendAllianceRosterBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) return;
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) return;
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(ALLIANCE_ROSTER_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000); // FLAG_RECEIVER_FOREGROUND
      ctx.sendBroadcast(intent);
      log('alliance-roster broadcast -> ' + SHARE_APP_PKG + ' (' + payload.length + 'b)');
    });
  } catch (e) {
    log('alliance-roster broadcast failed: ' + e);
  }
}

const ALLIANCE_RALLY_REBROADCAST_MS = 60000;

function pollAllianceRallyFile(nowMs, force) {
  try {
    const text = readFileUtf8(ALLIANCE_RALLY_FILE, 4096);
    if (!text || !text.trim()) return;
    const changed = text !== lastAllianceRallyText;
    const due = nowMs - allianceRallyLastBroadcastMs >= ALLIANCE_RALLY_REBROADCAST_MS;
    if (!changed && !due && !force) return;
    lastAllianceRallyText = text;
    allianceRallyLastBroadcastMs = nowMs;
    sendAllianceRallyBroadcast(text.trim());
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('alliance rally poll error: ' + e);
    }
  }
}

function sendAllianceRallyBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) return;
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) return;
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(ALLIANCE_RALLY_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000);
      ctx.sendBroadcast(intent);
      log('alliance-rally broadcast -> ' + SHARE_APP_PKG + ' (' + payload.length + 'b)');
    });
  } catch (e) {
    log('alliance-rally broadcast failed: ' + e);
  }
}

function maybeAllianceRallyPulse() {
  const paths = [ALLIANCE_RALLY_PULSE_FILE, ALLIANCE_RALLY_PULSE_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i], 256);
      if (!text || !text.trim()) continue;
      writeFileEmpty(paths[i]);
      lastAllianceRallyText = '';
      return true;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('alliance-rally pulse error: ' + e);
      }
    }
  }
  return false;
}

function tickAllianceRally() {
  const pulsed = maybeAllianceRallyPulse();
  if (!pulsed) return;
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeLuaStartupReady(now)) return;
  try {
    runLua(ALLIANCE_RALLY_LUA);
  } catch (e) {
    log('alliance-rally scan error: ' + e);
    return;
  }
  pollAllianceRallyFile(now, true);
}

function maybeRelocateItemsPulse() {
  const paths = [RELOCATE_ITEMS_PULSE_FILE, RELOCATE_ITEMS_PULSE_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i], 256);
      if (!text || !text.trim()) continue;
      writeFileEmpty(paths[i]);
      relocateItemsLastTick = 0;
      lastRelocateItemsText = '';
      return true;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('relocate-items pulse error: ' + e);
      }
    }
  }
  return false;
}

function tickRelocateItems() {
  const pulsed = maybeRelocateItemsPulse();
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  // ItemData доступен после логина, не ждём world-ready (иначе после VIP-покупки долго остаётся 0).
  if (!bridgeLuaStartupReady(now)) return;
  if (!pulsed && now - relocateItemsLastTick < RELOCATE_ITEMS_SCAN_INTERVAL_MS) return;
  relocateItemsLastTick = now;
  try {
    runLua(RELOCATE_ITEMS_LUA);
  } catch (e) {
    log('relocate-items scan error: ' + e);
    return;
  }
  try {
    const text = readFileUtf8(RELOCATE_ITEMS_FILE, 4096);
    if (!text || !text.trim()) return;
    const changed = text !== lastRelocateItemsText;
    const due = now - relocateItemsLastBroadcastMs >= RELOCATE_ITEMS_REBROADCAST_MS;
    if (!changed && !due && !pulsed) return;
    lastRelocateItemsText = text;
    relocateItemsLastBroadcastMs = now;
    sendRelocateItemsBroadcast(text.trim());
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('relocate-items poll error: ' + e);
    }
  }
}

function sendRelocateItemsBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) return;
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) return;
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(RELOCATE_ITEMS_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000);
      ctx.sendBroadcast(intent);
      log('relocate-items broadcast -> ' + SHARE_APP_PKG + ' (' + payload.length + 'b)');
    });
  } catch (e) {
    log('relocate-items broadcast failed: ' + e);
  }
}

function logIl2cppExportsOnce() {
  if (logIl2cppExportsOnce.done) return;
  logIl2cppExportsOnce.done = true;
  const names = [
    'il2cpp_object_get_class', 'il2cpp_class_get_name', 'il2cpp_class_get_fields',
    'il2cpp_field_get_offset', 'il2cpp_field_get_flags', 'il2cpp_class_get_field_from_name',
    'il2cpp_class_get_parent', 'il2cpp_class_get_static_field_data', 'il2cpp_class_from_name',
    'il2cpp_domain_get_assemblies', 'il2cpp_runtime_class_init',
  ];
  const res = names.map(function (n) { return n.replace('il2cpp_', '') + '=' + (findExport(n) ? '1' : '0'); });
  log('EXPORTS ' + res.join(' '));
}
logIl2cppExportsOnce.done = false;

function logLibStatusOnce() {
  if (libReadyLogged) return;
  const base = libBase();
  if (base) {
    libReadyLogged = true;
    log('libil2cpp ready @ ' + base);
    logIl2cppExportsOnce();
    installAppFrameCacheHook();
    installWorldMapHijack();
    installFlyWorldSetterHooks();
    installActionCatchHook();
    installDoStringHook();
    installRequireLuaHook();
    // Install the Unity main-thread queue drain at startup (not lazily on first fly).
    // runLua() pushes Lua work (share-panel hook install, autohelp) onto
    // mainThreadFlyQueue, which is ONLY drained by this ExecuteTasks hook. If a game
    // restart shares before any fly, the drain was never installed and the share hook
    // never armed ("В рейд" never appeared) even though liveLuaEnv was captured.
    installMainThreadFlyDrain();
    return;
  }
  const path = il2CppPathFromMaps();
  if (path) {
    log('libil2cpp in maps but not attached: ' + path);
  }
}

// Lua hook: wraps UIChatSharePanel OnEnter/OnExit; writes share payload + dialogTopPx for overlay layout.
const SHARE_HOOK_LUA = [
  "pcall(function()",
  "pcall(function() require('Eyu.Logic.UI.Panel.Chat.UIChatSharePanel') end)",
  "local pl=package.loaded",
  "local pcls=pl['Eyu.Logic.UI.Panel.Chat.UIChatSharePanel']",
  "if not pcls then return end",
  "local idx=(getmetatable(pcls) or {}).__index or pcls",
  "local F='/data/data/com.phs.global/files/squadrelay_share.json'",
  "local OK='/data/data/com.phs.global/files/squadrelay_share_hook.ok'",
  "local PREFAB='UI/UIModules/Chat/ChatSharePanel.prefab'",
  "local function esc(s) return (string.gsub(tostring(s),'\"',\"'\")) end",
  "local function wr(t) local f=io.open(F,'w') if f then f:write(t) f:close() end end",
  "local function isShare(pt) return type(pt)=='table' and pt.shareType~=nil and pt.withAll~=nil end",
  "local function calcDialogTopPx()",
  "local ok,y=pcall(function()",
  "local wm=WinsManager and WinsManager.instance",
  "if not wm then return nil end",
  "local win=wm:GetWin(PREFAB)",
  "if not win or not win.gameObject then return nil end",
  "local roots=CS.UIRoot.list",
  "if not roots or roots.Count<1 then return nil end",
  "local manual=roots[0].manualHeight",
  "local sh=CS.UnityEngine.Screen.height",
  "local py=win.gameObject.transform.position.y",
  "if manual and manual>0 and py then return math.floor((1-py/manual)*sh) end",
  "return nil end)",
  "return ok and y or nil end",
  "local function enrich(pt,p)",
  "local dn=nil local cat=nil local cfgrow=nil",
  "local function srdump(t) local s='' if type(t)=='table' then local n=0 for k,v in pairs(t) do local vv=tostring(v) if #vv>40 then vv=string.sub(vv,1,40) end s=s..tostring(k)..'='..vv..';' n=n+1 if n>80 then break end end end return (string.gsub(s,'[^%w%._=; -]',' ')) end",
  "local function usn(uid) uid=tonumber(uid) if not uid or uid==0 then return nil end local res=nil",
  "pcall(function() local ad=_G.Data and _G.Data.AllianceData if ad then local oid=ad.unionId or ad.id or ad.unionDbId if oid and tonumber(oid)==uid then res=ad.shortName or ad.alias or ad.simpleName end end end)",
  "if res and tostring(res)~='' then return res end",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local cands={'GetUnionSimpleInfoById','GetUnionSimpleInfo','GetUnionInfoById','GetUnionInfo','GetUnionShortNameById','GetUnionShortName','GetUnionDataById','GetUnionData'} for _,m in ipairs(cands) do if not (res and tostring(res)~='') then pcall(function() local f=wmm[m] if type(f)=='function' then local r=f(wmm,uid) if type(r)=='table' then res=r.shortName or r.unionShortName or r.alias or r.simpleName elseif type(r)=='string' then res=r end end end) end end end",
  "if res and tostring(res)~='' then return res end return nil end",
  "if pt.name then dn=pt.name cat='player'",
  "elseif pt.truckName then dn=pt.truckName cat='truck'",
  "elseif pt.nameKey then local nk=tostring(pt.nameKey)",
  "local tb,id,fl=string.match(nk,'Table@(%w+)#(%d+)#(%w+)')",
  "if tb and id then cat=tb local row=_G.Config and _G.Config[tb] and _G.Config[tb][tonumber(id)] if type(row)=='table' then dn=row[fl] or row.name or row.name2 cfgrow=row end end",
  "if not dn then dn=nk end end",
  "if dn then p[#p+1]='\"name\":\"'..esc(dn)..'\"' end",
  "if cat then p[#p+1]='\"cat\":\"'..esc(cat)..'\"' end",
  "local _recAb=cfgrow and tonumber(cfgrow.recAbility) if cat and cat~='player' and _recAb and _recAb>0 then p[#p+1]='\"power\":'..string.format('%d',_recAb) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end",
  "if pt.lv then p[#p+1]='\"lv\":'..tostring(pt.lv) end",
  "if pt.qualityType then p[#p+1]='\"qualityType\":'..tostring(pt.qualityType) end",
  "if pt.playerName then p[#p+1]='\"playerName\":\"'..esc(pt.playerName)..'\"' end",
  "if pt.secretTaskId then p[#p+1]='\"secretTaskId\":'..tostring(pt.secretTaskId)",
  "local C=_G.Config local st=C and C.SecretTask and C.SecretTask[pt.secretTaskId]",
  "if type(st)=='table' then",
  "if st.quality then p[#p+1]='\"grade\":'..tostring(st.quality) end",
  "if st.secretLevel then p[#p+1]='\"stars\":'..tostring(st.secretLevel) end",
  "end end",
  "local top=calcDialogTopPx()",
  "if top and top>0 then p[#p+1]='\"dialogTopPx\":'..tostring(top) end",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local ok,u=pcall(function() return wmm:GetDynamicUnitDataByCell(pt.x,pt.y) end)",
  "if ok and type(u)=='table' then",
  "if u.level and not pt.lv then p[#p+1]='\"lv\":'..tostring(u.level) end",
  "if u.playerPower and u.playerPower>0 then p[#p+1]='\"power\":'..tostring(u.playerPower) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end",
  "if u.killEnemyCount and u.killEnemyCount>0 then p[#p+1]='\"kills\":'..tostring(u.killEnemyCount) p[#p+1]='\"killsIcon\":\"pic_jisha\"' end",
  "local uun=nil if u.playerUnionShortName and tostring(u.playerUnionShortName)~='' then uun=u.playerUnionShortName elseif u.occupierUnionShortName and tostring(u.occupierUnionShortName)~='' then uun=u.occupierUnionShortName elseif u.occupierUnionId then uun=usn(u.occupierUnionId) end",
  "if uun and tostring(uun)~='' then p[#p+1]='\"union\":\"'..esc(uun)..'\"' end",
  "if cat and cat~='player' then local dg=srdump(u) local dc=srdump(cfgrow) if dg~='' or dc~='' then p[#p+1]='\"diag\":\"u{'..esc(dg)..'} cfg{'..esc(dc)..'}\"' end end",
  "end end",
  "end",
  "local function publish(pt,open)",
  "if not isShare(pt) then return end",
  "_G.__sr_seq=(_G.__sr_seq or 0)+1",
  "local seq=_G.__sr_seq",
  "wr('{\"seq\":'..seq..',\"open\":'..(open and 'true' or 'false')..',\"x\":'..tostring(pt.x or 0)..',\"y\":'..tostring(pt.y or 0)..',\"sid\":'..tostring(pt.sid or 0)..',\"shareType\":'..tostring(pt.shareType or 0)..'}')",
  "if not open then return end",
  "local p={}",
  "p[#p+1]='\"seq\":'..seq",
  "p[#p+1]='\"open\":true'",
  "p[#p+1]='\"x\":'..tostring(pt.x or 0)",
  "p[#p+1]='\"y\":'..tostring(pt.y or 0)",
  "p[#p+1]='\"sid\":'..tostring(pt.sid or 0)",
  "p[#p+1]='\"shareType\":'..tostring(pt.shareType or 0)",
  "enrich(pt,p)",
  "wr('{'..table.concat(p,',')..'}')",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local ok,u=pcall(function() return wmm:GetDynamicUnitDataByCell(pt.x,pt.y) end)",
  "if ok and type(u)=='table' and (u.playerPower or u.killEnemyCount) then",
  "_G.__sr_seq=_G.__sr_seq+1",
  "local p2={'\"seq\":'.._G.__sr_seq,'\"open\":true','\"x\":'..tostring(pt.x or 0),'\"y\":'..tostring(pt.y or 0),'\"sid\":'..tostring(pt.sid or 0),'\"shareType\":'..tostring(pt.shareType or 0)}",
  "enrich(pt,p2)",
  "wr('{'..table.concat(p2,',')..'}')",
  "end end",
  "end",
  // Per-idx guard (NOT a global): the game can recreate its Lua state at any time,
  // wiping globals and replacing this class table; re-running then re-wraps the new
  // idx. GetWin-based visibility sync is impossible here because the share Win is
  // pooled — GetWin keeps returning it with paramTable.shareType set even when the
  // panel is closed. So rely purely on OnEnter/OnExit, and re-publish the live open
  // panel (tracked via _G.__sr_sharePanel, which resets on state reset).
  "if rawget(idx,'__sr_oe')==nil then",
  "_G.__sr_seq=_G.__sr_seq or 0",
  "local oe=idx.OnEnter idx.__sr_oe=oe or false",
  "idx.OnEnter=function(self,...) local r if oe then r=oe(self,...) end pcall(function()",
  "local pt=self.paramTable",
  "if isShare(pt) then _G.__sr_sharePanel=self publish(pt,true) end",
  "end) return r end",
  "local ox=idx.OnExit idx.__sr_ox=ox or false",
  "idx.OnExit=function(self,...) pcall(function()",
  "if isShare(self.paramTable) then _G.__sr_sharePanel=nil _G.__sr_seq=(_G.__sr_seq or 0)+1 wr('{\"seq\":'.._G.__sr_seq..',\"open\":false}') end",
  "end) if ox then return ox(self,...) end end",
  "end",
  "local g=io.open(OK,'w') if g then g:write('ok') g:close() end",
  "end)",
].join(' ');

const SHARE_CLOSE_LUA = [
  'pcall(function()',
  "local prefab='UI/UIModules/Chat/ChatSharePanel.prefab'",
  'local wm=WinsManager and WinsManager.instance',
  'if wm then',
  'local win=wm:GetWin(prefab)',
  'if win then',
  'local lp=win._luaInstance',
  'if lp and lp.OnCloseHandler then pcall(function() lp:OnCloseHandler() end) end',
  'pcall(function() wm:CloseWin(prefab,true) end)',
  'pcall(function() wm:RealyCloseWin(prefab,true) end)',
  'end end',
  // Также закрываем игровое окно «Добавить тег» (SearchCollectPanel), если оно открыто:
  // вызываем это из onBookmarkAdd тем же каналом share-close.
  'pcall(function() local sp=_G.__bm_panel if sp then',
  'if sp.OnCloseHandler then pcall(function() sp:OnCloseHandler() end) end',
  'if sp.CloseSelf then pcall(function() sp:CloseSelf() end) end',
  'if sp.Close then pcall(function() sp:Close() end) end',
  'if sp.OnClose then pcall(function() sp:OnClose() end) end',
  'if sp.OnClickClose then pcall(function() sp:OnClickClose() end) end',
  'if sp.OnBtnCloseClick then pcall(function() sp:OnBtnCloseClick() end) end',
  '_G.__bm_panel=nil end end)',
  'end)',
].join(' ');

let shareHookOk = false;
let lastShareInstallAt = 0;
let lastShareText = '';

function maybeInstallShareHook() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeLuaStartupReady(now) || !bridgeBackgroundLuaReady(now)) return;
  // Re-arm continuously: the game recreates its Lua state at runtime, wiping the
  // OnEnter/OnExit wrappers. The per-idx guard in SHARE_HOOK_LUA makes re-runs
  // idempotent within a state and re-installs on the fresh idx after a reset.
  // Fast retry (100ms) until first confirmed install, then steady re-arm (2s).
  const interval = shareHookOk ? 2000 : 100;
  if (now - lastShareInstallAt < interval) return;
  lastShareInstallAt = now;
  runLua(SHARE_HOOK_LUA);
  if (!shareHookOk) {
    const ok = readFileUtf8(SHARE_OK_FILE);
    if (ok && ok.indexOf('ok') >= 0) {
      shareHookOk = true;
      log('share hook armed (re-arming every 2s)');
    }
  }
}

function pollShareCloseFile() {
  try {
    const text = readFileUtf8(SHARE_CLOSE_FILE);
    if (!text || !text.trim() || text.indexOf('close') < 0) return;
    log('share close trigger');
    writeFileEmpty(SHARE_CLOSE_FILE);
    runLua(SHARE_CLOSE_LUA);
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('share close poll error: ' + e);
    }
  }
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

let bookmarkHookOk = false;
let lastBookmarkInstallAt = 0;
let lastBookmarkText = '';

function maybeInstallBookmarkHook() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  if (!bridgeLuaStartupReady(now) || !bridgeBackgroundLuaReady(now)) return;
  // Re-arm continuously (see maybeInstallShareHook): survives Lua state resets.
  const interval = bookmarkHookOk ? 2000 : 100;
  if (now - lastBookmarkInstallAt < interval) return;
  lastBookmarkInstallAt = now;
  runLua(BOOKMARK_HOOK_LUA);
  if (!bookmarkHookOk) {
    const ok = readFileUtf8(BOOKMARK_OK_FILE);
    if (ok && ok.indexOf('ok') >= 0) {
      bookmarkHookOk = true;
      log('bookmark hook armed (re-arming every 2s)');
    }
  }
}

function sendBookmarkBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) {
    log('bookmark broadcast skipped: Java bridge unavailable');
    return;
  }
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) {
        log('bookmark broadcast skipped: no currentApplication');
        return;
      }
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(BOOKMARK_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000); // FLAG_RECEIVER_FOREGROUND
      ctx.sendBroadcast(intent);
      log('bookmark broadcast -> ' + SHARE_APP_PKG);
    });
  } catch (e) {
    log('bookmark broadcast failed: ' + e);
  }
}

function pollBookmarkFile() {
  try {
    const text = readFileUtf8(BOOKMARK_FILE);
    if (!text || !text.trim() || text === lastBookmarkText) return;
    lastBookmarkText = text;
    log('bookmark payload: ' + text.trim());
    sendBookmarkBroadcast(text.trim());
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('bookmark poll error: ' + e);
    }
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
  log('bridge started v' + BRIDGE_VERSION + ' pid=' + Process.id + ' proc=' + proc);
  resetAutoAssaultBridgeState();
  ensureAutoAssaultScanFile();
  clearTriggerFiles();
  writeFileEmpty(SHARE_FILE);
  writeFileEmpty(SHARE_OK_FILE);
  writeFileEmpty(SHARE_CLOSE_FILE);
  writeFileEmpty(BOOKMARK_FILE);
  writeFileEmpty(BOOKMARK_OK_FILE);
  writeFileEmpty(RELOC_PANEL_FILE);
  writeFileEmpty(RELOC_PANEL_OK_FILE);
  writeFileEmpty(ROUTE_MAP_CLICK_FILE);
  lastRelocPanelText = '';
  lastRouteMapClickText = '';
  relocPanelHookOk = false;
  shareHookOk = false;
  lastShareText = '';
  bookmarkHookOk = false;
  lastBookmarkText = '';
  mapsDiagOnce();
  setInterval(function () {
    const nowMs = Date.now();
    logLibStatusOnce();
    if (liveLuaEnvCapturedMs === 0 && liveLuaEnv && !liveLuaEnv.isNull()) {
      liveLuaEnvCapturedMs = nowMs;
    }
    tickWorldReadyProbe(nowMs);
    pollProbeFile();
    pollOpenChatFile();
    pollEvalFile();
    pollAutoHelpConfig();
    pollAutoAssaultConfig();
    tickAutoHelp();
    tickAutoAssault();
    tickAllianceRoster();
    tickAllianceRally();
    tickRelocateItems();
    tickRouteMarkerStartupPull(nowMs);
    maybeInstallShareHook();
    pollShareFile();
    pollShareCloseFile();
    maybeInstallBookmarkHook();
    pollBookmarkFile();
    maybeInstallRelocPanelHook();
    pollRelocPanelFile();
    pollRouteMapClickFile();
    pollRoutePlacementFile();
    pollRouteMarkersPulse();
    if (libBase() && pendingFlies.length) scheduleDrainPendingFlies();
  }, 400);
  setInterval(function () {
    pollTriggerFile();
    pollCityRelocateFile();
    pollRoutePlacementFile();
    pollRouteMarkersPulse();
    maybeInstallShareHook();
    pollShareFile();
    pollShareCloseFile();
    maybeInstallBookmarkHook();
    pollBookmarkFile();
    maybeInstallRelocPanelHook();
    pollRelocPanelFile();
    pollRouteMapClickFile();
    if (libBase() && pendingFlies.length) scheduleDrainPendingFlies();
  }, 100);
});

setInterval(function () {}, 5000);
