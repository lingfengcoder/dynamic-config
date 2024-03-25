-- 获取zset的key
-- local key = KEYS[1]

-- 脚本传入的限流大小
local limit = tonumber(ARGV[1])

-- 脚本传入的 窗口左边
local start = tonumber(ARGV[2])

-- 脚本传入的 窗口右边
local now = tonumber(ARGV[3])

local uuid = ARGV[4]

-- 获取当前流量总数
local count = tonumber(redis.call('zcount',KEYS[1], start, now))

--是否超出限流值
if count + 1 >limit then
    return false
-- 不需要限流
else
    -- 添加当前访问时间戳到zset
    redis.call('zadd', KEYS[1], now, uuid)
    -- 移除时间区间以外不用的数据，不然会导致zset过大
    redis.call('zremrangebyscore',KEYS[1], 0, start)
    return true
end