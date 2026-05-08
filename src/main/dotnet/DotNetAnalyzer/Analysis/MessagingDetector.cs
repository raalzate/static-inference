using Microsoft.CodeAnalysis;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

/// <summary>
/// Pass 9: detect messaging usage on each component (MassTransit, Azure Service Bus).
///
/// Refactored to be language-agnostic by walking <see cref="Compilation.GlobalNamespace"/>
/// and inspecting <see cref="INamedTypeSymbol"/> members. Works for both C# and
/// VB compilations because all detection is done at the symbol level (interface
/// names, constructor parameter types, field types).
///
/// For the VB legacy desktop scope (AmigosPlus-like), this typically yields no
/// hits — those projects do not use MassTransit or Azure Service Bus. The walker
/// must still complete cleanly so that no spurious errors leak into the output.
/// </summary>
public class MessagingDetector
{
    // MassTransit interface names that indicate consumer role.
    private static readonly HashSet<string> MassTransitConsumerInterfaces = new(StringComparer.Ordinal)
    {
        "IConsumer",
    };

    // MassTransit types that indicate publisher role (used as ctor params or fields).
    private static readonly HashSet<string> MassTransitPublisherTypes = new(StringComparer.Ordinal)
    {
        "IPublishEndpoint",
        "IBus",
    };

    // Azure Service Bus sender / consumer types.
    private static readonly HashSet<string> AzureServiceBusSenderTypes = new(StringComparer.Ordinal)
    {
        "ServiceBusClient",
        "ServiceBusSender",
    };

    private static readonly HashSet<string> AzureServiceBusProcessorTypes = new(StringComparer.Ordinal)
    {
        "ServiceBusProcessor",
    };

    public void Detect(Compilation compilation, List<Component> components)
    {
        var componentMap = components.ToDictionary(c => c.Id, c => c);

        foreach (var typeSymbol in EnumerateNamedTypes(compilation.Assembly.GlobalNamespace))
        {
            if (typeSymbol.TypeKind != TypeKind.Class)
                continue;

            // Skip synthetic types injected by VB compiler (My.* etc.).
            if (typeSymbol.DeclaringSyntaxReferences.IsDefaultOrEmpty)
                continue;

            var typeName = GetFullyQualifiedName(typeSymbol);
            if (!componentMap.TryGetValue(typeName, out var component))
                continue;

            DetectMassTransit(typeSymbol, component);
            DetectAzureServiceBus(typeSymbol, component);
        }
    }

    private static void DetectMassTransit(INamedTypeSymbol typeSymbol, Component component)
    {
        // Consumer: implements MassTransit.IConsumer<T>.
        foreach (var iface in typeSymbol.AllInterfaces)
        {
            if (MassTransitConsumerInterfaces.Contains(iface.Name))
            {
                component.MessagingType = "MassTransit";
                component.MessagingRole = "consumer";
                return;
            }
        }

        // Publisher: ctor param or field of type IPublishEndpoint or IBus.
        if (HasMemberOrParameterOfType(typeSymbol, MassTransitPublisherTypes))
        {
            component.MessagingType = "MassTransit";
            component.MessagingRole = "publisher";
        }
    }

    private static void DetectAzureServiceBus(INamedTypeSymbol typeSymbol, Component component)
    {
        if (component.MessagingType != null)
            return;

        bool hasSender = HasMemberOrParameterOfType(typeSymbol, AzureServiceBusSenderTypes);
        bool hasProcessor = HasMemberOrParameterOfType(typeSymbol, AzureServiceBusProcessorTypes);

        if (hasProcessor)
        {
            component.MessagingType = "AzureServiceBus";
            component.MessagingRole = "consumer";
        }
        else if (hasSender)
        {
            component.MessagingType = "AzureServiceBus";
            component.MessagingRole = "publisher";
        }
    }

    private static bool HasMemberOrParameterOfType(INamedTypeSymbol typeSymbol, HashSet<string> targetNames)
    {
        // Fields
        foreach (var member in typeSymbol.GetMembers())
        {
            if (member is IFieldSymbol field
                && field.Type is INamedTypeSymbol fieldType
                && targetNames.Contains(fieldType.Name))
            {
                return true;
            }

            if (member is IPropertySymbol prop
                && prop.Type is INamedTypeSymbol propType
                && targetNames.Contains(propType.Name))
            {
                return true;
            }

            // Constructors — check parameter types
            if (member is IMethodSymbol method && method.MethodKind == MethodKind.Constructor)
            {
                foreach (var param in method.Parameters)
                {
                    if (param.Type is INamedTypeSymbol paramType
                        && targetNames.Contains(paramType.Name))
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static IEnumerable<INamedTypeSymbol> EnumerateNamedTypes(INamespaceSymbol ns)
    {
        foreach (var type in ns.GetTypeMembers())
        {
            yield return type;
            foreach (var nested in type.GetTypeMembers())
                yield return nested;
        }
        foreach (var sub in ns.GetNamespaceMembers())
        foreach (var t in EnumerateNamedTypes(sub))
            yield return t;
    }

    private static string GetFullyQualifiedName(INamedTypeSymbol symbol)
    {
        var parts = new List<string>();
        var current = symbol.ContainingNamespace;
        while (current != null && !current.IsGlobalNamespace)
        {
            parts.Insert(0, current.Name);
            current = current.ContainingNamespace;
        }
        parts.Add(symbol.Name);
        return string.Join(".", parts);
    }
}
