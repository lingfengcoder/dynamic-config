-- 参数说明：
-- KEYS[1]：令牌桶的标识
-- ARGV[1]：请求的令牌数量
-- ARGV[2]：令牌桶的容量
-- ARGV[3]：令牌桶的填充速率（单位：令牌/秒）

-- 检查令牌桶是否存在
if redis.call("exists", KEYS[1]) == 0 then
    -- 初始化令牌桶
    redis.call("hset", KEYS[1], "tokens", ARGV[2])
    redis.call("hset", KEYS[1], "last_update", "0")
end

-- 获取当前时间戳
local current_time = tonumber(redis.call("time")[1])
-- 获取上次令牌生成时间戳和当前令牌数量
local last_update = tonumber(redis.call("hget", KEYS[1], "last_update"))
local tokens = tonumber(redis.call("hget", KEYS[1], "tokens"))

-- 计算令牌的增加数量
local delta = math.max(0, ARGV[3] * (current_time - last_update))
-- 更新上次令牌生成时间戳
redis.call("hset", KEYS[1], "last_update", tostring(current_time))
-- 生成新的令牌
tokens = math.min(ARGV[2], tokens + delta)

-- 检查是否有足够的令牌
if tokens >= tonumber(ARGV[1]) then
    -- 消耗令牌
    redis.call("hset", KEYS[1], "tokens", tokens - tonumber(ARGV[1]))
    return 1  -- 通过限流
else
    return 0  -- 被限流
end