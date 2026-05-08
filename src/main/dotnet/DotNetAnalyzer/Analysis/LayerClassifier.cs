using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

/// <summary>
/// Classifies .NET components into architectural layers based on attributes and naming conventions.
/// Mirrors the Java LayerClassifier categories: Controlador, Negocio, Datos/Persistencia, Compartida.
/// </summary>
public class LayerClassifier
{
    /// <summary>
    /// Annotations that mark a component as a real HTTP controller.
    /// Names align with the canonical .NET attribute simple-names emitted
    /// by TypeAnalyzer.ExtractAnnotations (sin sufijo "Attribute").
    /// </summary>
    private static readonly string[] HttpControllerAnnotations =
    {
        "ApiController",
        "Route",
        "HttpGet", "HttpPost", "HttpPut", "HttpDelete", "HttpPatch",
    };

    /// <summary>
    /// Base class simple-names that mark a component as an HTTP controller
    /// via inheritance (ASP.NET Core).
    /// </summary>
    private static readonly string[] HttpControllerBaseTypes =
    {
        "ControllerBase",
        "Controller",
    };

    private static readonly string[] ControllerNameSuffixes =
    {
        "Controller",
    };

    private static readonly string[] BusinessNameSuffixes =
    {
        "Service", "Manager", "Handler", "Processor", "UseCase", "Facade",
    };

    private static readonly string[] DataNameSuffixes =
    {
        "Repository", "Data", "Dao",
        // VB legacy AmigosPlus-like data access objects (FR-021 acceptance #3).
        "Adapter",
    };

    private static readonly string[] DataBaseTypes =
    {
        "DbContext",
    };

    private static readonly string[] SharedNameSuffixes =
    {
        "Helper", "Extensions", "Middleware", "Util", "Utils", "Config",
        "Configuration", "Constants", "Filter", "Validator", "Factory",
    };

    /// <summary>
    /// Assigns a Layer to each component based on attributes, base type, and naming conventions.
    /// </summary>
    public void Classify(List<Component> components)
    {
        foreach (var component in components)
        {
            component.Layer = ClassifyComponent(component);
        }
    }

    private static string ClassifyComponent(Component component)
    {
        var simpleName = ExtractSimpleName(component.Id);
        var annotations = component.Annotations ?? new List<string>();

        // Priority 1: HTTP controller — confirmed by attribute or by extending
        // ControllerBase/Controller. The Controller suffix alone is NOT enough:
        // VB legacy projects (AmigosPlus-like) name business facades "*Controller"
        // without any HTTP surface area (FR-020, FR-021).
        bool isHttpController =
            HasAnyAnnotation(annotations, HttpControllerAnnotations)
            || ExtendsAny(component.Extends, HttpControllerBaseTypes);

        if (isHttpController)
        {
            return "Controlador";
        }

        // Priority 2: Controller suffix without HTTP markers → Negocio.
        // Falls through to standard business / data / shared rules so that
        // a class named "OrderController" with no HTTP markers and no other
        // matching suffix lands in "Negocio" (the closest semantic match).
        if (EndsWithAny(simpleName, ControllerNameSuffixes))
        {
            return "Negocio";
        }

        // Priority 3: Check for data layer — DbContext base type
        if (!string.IsNullOrEmpty(component.Extends) &&
            DataBaseTypes.Any(bt => component.Extends.Contains(bt)))
        {
            return "Datos";
        }

        // Priority 4: Check name suffixes for data layer
        if (EndsWithAny(simpleName, DataNameSuffixes))
        {
            return "Datos";
        }

        // Priority 5: Check name suffixes for business layer
        if (EndsWithAny(simpleName, BusinessNameSuffixes))
        {
            return "Negocio";
        }

        // Priority 6: Check name suffixes/contains for shared layer
        if (EndsWithAny(simpleName, SharedNameSuffixes) || ContainsAny(simpleName, SharedNameSuffixes))
        {
            return "Compartida";
        }

        // Default: Compartida (shared/utility)
        return "Compartida";
    }

    private static bool HasAnyAnnotation(List<string> annotations, string[] targets)
    {
        foreach (var target in targets)
        {
            foreach (var annotation in annotations)
            {
                if (string.Equals(annotation, target, StringComparison.OrdinalIgnoreCase))
                    return true;
            }
        }
        return false;
    }

    private static bool EndsWithAny(string name, string[] suffixes)
    {
        foreach (var suffix in suffixes)
        {
            if (name.EndsWith(suffix, StringComparison.OrdinalIgnoreCase))
                return true;
        }
        return false;
    }

    private static bool ContainsAny(string name, string[] patterns)
    {
        foreach (var pattern in patterns)
        {
            if (name.Contains(pattern, StringComparison.OrdinalIgnoreCase))
                return true;
        }
        return false;
    }

    /// <summary>
    /// Returns true if <paramref name="extends"/> is non-empty and matches one
    /// of the target simple-names (case-insensitive). The match is permissive:
    /// it accepts both bare simple-names ("ControllerBase") and qualified names
    /// ("Microsoft.AspNetCore.Mvc.ControllerBase").
    /// </summary>
    private static bool ExtendsAny(string? extends, string[] targets)
    {
        if (string.IsNullOrEmpty(extends))
            return false;

        var simpleName = extends!;
        var lastDot = simpleName.LastIndexOf('.');
        if (lastDot >= 0 && lastDot < simpleName.Length - 1)
            simpleName = simpleName.Substring(lastDot + 1);

        foreach (var t in targets)
        {
            if (string.Equals(simpleName, t, StringComparison.OrdinalIgnoreCase))
                return true;
        }
        return false;
    }

    private static string ExtractSimpleName(string fullyQualifiedName)
    {
        var lastDot = fullyQualifiedName.LastIndexOf('.');
        return lastDot >= 0 && lastDot < fullyQualifiedName.Length - 1
            ? fullyQualifiedName.Substring(lastDot + 1)
            : fullyQualifiedName;
    }
}
