-- KEYS: IP 日计数、全站日计数；ARGV: IP 上限、全站上限、日开始、日结束 Unix 秒。
-- 返回 1 成功，0 IP 耗尽，-1 全站耗尽，-2 数据损坏，-3 日期失效。
-- Lua 原子执行不等于出错自动回滚：所有可预见的校验放到写入之前。
local now = tonumber(redis.call('TIME')[1])
local startAt, resetAt = tonumber(ARGV[3]), tonumber(ARGV[4])
if now < startAt or now >= resetAt then return -3 end

local function count(key)
    local kind = redis.call('TYPE', key).ok
    if kind == 'none' then return 0 end
    if kind ~= 'string' then return nil end
    local raw = redis.call('GET', key)
    -- Java 配置上限为正 int。超大计数保守拒绝，避免 Lua 浮点数精度问题。
    if not (raw == '0' or string.match(raw, '^[1-9]%d*$')) or #raw > 10 then return nil end
    local n = tonumber(raw)
    if n > 2147483647 then return nil end
    -- 存在的日计数必须保留到本日结束，提前过期会重置限额；异常时禁止识别。
    if redis.call('EXPIRETIME', key) ~= resetAt then return nil end
    return n
end

local ip, total = count(KEYS[1]), count(KEYS[2])
if ip == nil or total == nil or total < ip then return -2 end
if ip >= tonumber(ARGV[1]) then return 0 end
if total >= tonumber(ARGV[2]) then return -1 end

redis.call('SET', KEYS[1], string.format('%.0f', ip + 1), 'EXAT', resetAt)
redis.call('SET', KEYS[2], string.format('%.0f', total + 1), 'EXAT', resetAt)
return 1
