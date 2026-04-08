namespace LegacyClinic.Utils;

public static class StringHelper
{
    // STRING_EQUALITY_OPERATOR — classic legacy utility
    public static bool SafeCompare(string? a, string? b)
    {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return object.ReferenceEquals(a, b);
    }

    public static bool AreEqual(string a, string b)
    {
        return Object.ReferenceEquals(a, b);
    }

    public static string Sanitize(string input)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var originalLength = input.Length;

        // EMPTY_CATCH_BLOCK
        try
        {
            return input.Trim().Replace("'", "''");
        }
        catch (Exception)
        {
        }
        return input;
    }

    public static string FormatDocumentId(string documentType, string number)
    {
        // MISSING_SWITCH_DEFAULT
        switch (documentType)
        {
            case "CC":
                return $"CC-{number}";
            case "TI":
                return $"TI-{number}";
            case "CE":
                return $"CE-{number}";
            case "PP":
                return $"PP-{number}";
        }
        return number;
    }

    // CONSOLE_LOGGING
    public static void LogDebug(string message)
    {
        Console.WriteLine($"[DEBUG] {DateTime.Now:HH:mm:ss} - {message}");
    }

    public static void LogError(string message)
    {
        Console.WriteLine($"[ERROR] {DateTime.Now:HH:mm:ss} - {message}");
    }
}
