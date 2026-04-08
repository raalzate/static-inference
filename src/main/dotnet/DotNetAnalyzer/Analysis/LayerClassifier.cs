using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

/// <summary>
/// Classifies .NET components into architectural layers based on attributes and naming conventions.
/// Mirrors the Java LayerClassifier categories: Controlador, Negocio, Datos/Persistencia, Compartida.
/// </summary>
public class LayerClassifier
{
    private static readonly string[] ControllerAnnotations =
    {
        "ApiController", "Controller", "Route",
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

        // Priority 1: Check annotations for controller attributes
        if (HasAnyAnnotation(annotations, ControllerAnnotations))
        {
            return "Controlador";
        }

        // Priority 2: Check name suffixes for controller
        if (EndsWithAny(simpleName, ControllerNameSuffixes))
        {
            return "Controlador";
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

    private static string ExtractSimpleName(string fullyQualifiedName)
    {
        var lastDot = fullyQualifiedName.LastIndexOf('.');
        return lastDot >= 0 && lastDot < fullyQualifiedName.Length - 1
            ? fullyQualifiedName.Substring(lastDot + 1)
            : fullyQualifiedName;
    }
}
