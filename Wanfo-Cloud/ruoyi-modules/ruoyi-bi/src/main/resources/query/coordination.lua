-- One bounded state key makes all quota transitions atomic, including Redis Cluster.
local action=ARGV[1]
local p=cjson.decode(ARGV[2])
local raw=redis.call('GET',KEYS[1])
local s=raw and cjson.decode(raw) or {active={},waiting={},cancelled={},history={}}
local time=redis.call('TIME')
local now=tonumber(time[1])*1000+math.floor(tonumber(time[2])/1000)
local function count(t) local n=0 for _ in pairs(t) do n=n+1 end return n end
local function history(event) table.insert(s.history,1,event) while #s.history>200 do table.remove(s.history) end end
for id,e in pairs(s.active) do
 if e.untilAt<=now then e.event.status='lease_expired';e.event.durationMs=math.max(0,now-e.startedAt);history(e.event);s.active[id]=nil end
end
for id,e in pairs(s.waiting) do if e.untilAt<=now then s.waiting[id]=nil end end
for token,expiry in pairs(s.cancelled) do if expiry<=now then s.cancelled[token]=nil end end
local function cancel(token)
 s.cancelled[token]=now+60000
 if count(s.cancelled)>4096 then local first=nil;local expiry=math.huge for t,v in pairs(s.cancelled) do if t~=token and v<expiry then first=t;expiry=v end end if first then s.cancelled[first]=nil end end
end
local result={code=1}
if action=='admit' then
 if s.cancelled[p.token] then result.code=-2
 elseif s.config and s.config~=p.config and (count(s.active)>0 or count(s.waiting)>0) then result.code=-4
 else
  s.config=p.config
  local duplicate=false
  for id,e in pairs(s.active) do if id~=p.id and e.token==p.token then duplicate=true end end
  for id,e in pairs(s.waiting) do if id~=p.id and e.token==p.token then duplicate=true end end
  if duplicate then result.code=-3
  elseif s.active[p.id] then result.code=1
  else
   local user=0;local source={}
   for _,e in pairs(s.active) do
    if e.owner==p.owner then user=user+1 end
    for _,id in ipairs(e.sources) do source[id]=(source[id] or 0)+1 end
   end
   local full=count(s.active)>=p.limits.maxActive or user>=p.limits.perUser
   for _,id in ipairs(p.sources) do if (source[id] or 0)>=p.limits.perSource then full=true end end
   if full then
    if not s.waiting[p.id] and count(s.waiting)>=p.limits.maxWaiting then result.code=-1
    else s.waiting[p.id]=s.waiting[p.id] or {token=p.token,untilAt=now+p.waitMs+2000};result.code=0 end
   else
    p.event.startedAt=now
    s.active[p.id]={owner=p.owner,token=p.token,sources=p.sources,startedAt=now,untilAt=now+p.leaseMs,event=p.event}
    s.waiting[p.id]=nil
   end
  end
 end
elseif action=='pulse' then
 local e=s.active[p.id]
 if not e or e.token~=p.token then result.code=-3
 elseif s.cancelled[p.token] then result.code=-2
 else e.untilAt=now+p.leaseMs;p.event.startedAt=e.startedAt;e.event=p.event end
elseif action=='release' then
 local e=s.active[p.id]
 if e and e.token==p.token then if p.event then p.event.startedAt=e.startedAt;history(p.event) end s.active[p.id]=nil end
 s.waiting[p.id]=nil
elseif action=='outcome' then
 for i,e in ipairs(s.history) do if e.requestId==p.id then p.event.startedAt=e.startedAt;s.history[i]=p.event;break end end
elseif action=='cancel' then cancel(p.token)
elseif action=='adminCancel' then
 local e=s.active[p.id]
 if e then cancel(e.token) else result.code=0 end
elseif action=='monitor' then
 local running={}
 for _,e in pairs(s.active) do local event=e.event;event.durationMs=math.max(0,now-e.startedAt);if event.durationMs>=p.limits.slowMillis then event.slow=true end table.insert(running,event) end
 result={scope='cluster',active=count(s.active),waiting=count(s.waiting),running=running,recent=s.history,limits=p.limits}
else return redis.error_reply('Unknown query coordination operation') end
redis.call('SET',KEYS[1],cjson.encode(s),'EX',86400)
return cjson.encode(result)
