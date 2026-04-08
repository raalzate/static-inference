namespace LegacyClinic.Utils;

public class CacheManager
{
    private readonly Dictionary<string, object> _cache = new();

    // HARDCODED_SECRET_IN_CODE
    private readonly string _token = "redis-auth-token-clinic-2021";

    public void Set(string key, object value)
    {
        // EMPTY_CATCH_BLOCK + CATCH_GENERIC_EXCEPTION
        try
        {
            _cache[key] = value;
            Console.WriteLine($"Cache SET: {key}");
        }
        catch (Exception)
        {
        }
    }

    public object? Get(string key)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var hitCount = 0;

        if (_cache.TryGetValue(key, out var value))
        {
            Console.WriteLine($"Cache HIT: {key}");
            return value;
        }

        Console.WriteLine($"Cache MISS: {key}");
        return null;
    }

    public void Clear()
    {
        _cache.Clear();
        // CONSOLE_LOGGING
        Console.WriteLine("Cache cleared");
    }

    // DEPRECATED_THREAD_USAGE
    public void StopCacheCleanup(Thread cleanupThread)
    {
        Console.WriteLine("Stopping cache cleanup thread");
        cleanupThread.Abort();
    }

    // ASYNC_VOID_METHOD
    public async void WarmUpCacheAsync()
    {
        Console.WriteLine("Warming up cache...");
        await Task.Delay(300);
        Console.WriteLine("Cache warm-up complete");
    }
}
