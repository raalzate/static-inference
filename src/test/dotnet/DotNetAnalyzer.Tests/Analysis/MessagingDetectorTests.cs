using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class MessagingDetectorTests
{
    private static CSharpCompilation CreateCompilation(params string[] sources)
    {
        var syntaxTrees = sources.Select(s => CSharpSyntaxTree.ParseText(s)).ToArray();

        var references = new[]
        {
            MetadataReference.CreateFromFile(typeof(object).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Enumerable).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Task).Assembly.Location),
        };

        var runtimeDir = Path.GetDirectoryName(typeof(object).Assembly.Location)!;
        var runtimeRef = MetadataReference.CreateFromFile(Path.Combine(runtimeDir, "System.Runtime.dll"));

        return CSharpCompilation.Create(
            "TestAssembly",
            syntaxTrees,
            references.Append(runtimeRef),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }

    #region MassTransit Consumer Detection

    private static readonly string MassTransitConsumerSource = @"
using System.Threading.Tasks;

namespace MassTransit
{
    public interface IConsumer<T> where T : class
    {
        Task Consume(ConsumeContext<T> context);
    }
    public interface ConsumeContext<T> { T Message { get; } }
}

namespace SampleProject.Consumers
{
    public class OrderCreatedEvent
    {
        public int OrderId { get; set; }
    }

    public class OrderCreatedConsumer : MassTransit.IConsumer<OrderCreatedEvent>
    {
        public Task Consume(MassTransit.ConsumeContext<OrderCreatedEvent> context)
        {
            return Task.CompletedTask;
        }
    }
}";

    [Fact]
    public void Detect_MassTransitConsumer_SetsMessagingTypeMassTransit()
    {
        // Arrange
        var compilation = CreateCompilation(MassTransitConsumerSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var consumer = components.FirstOrDefault(c => c.Id.Contains("OrderCreatedConsumer"));
        consumer.Should().NotBeNull();
        consumer!.MessagingType.Should().Be("MassTransit");
    }

    [Fact]
    public void Detect_MassTransitConsumer_SetsMessagingRoleConsumer()
    {
        // Arrange
        var compilation = CreateCompilation(MassTransitConsumerSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var consumer = components.FirstOrDefault(c => c.Id.Contains("OrderCreatedConsumer"));
        consumer.Should().NotBeNull();
        consumer!.MessagingRole.Should().Be("consumer");
    }

    #endregion

    #region MassTransit Publisher Detection

    private static readonly string MassTransitPublisherSource = @"
using System.Threading.Tasks;

namespace MassTransit
{
    public interface IPublishEndpoint
    {
        Task Publish<T>(T message) where T : class;
    }
    public interface IBus : IPublishEndpoint { }
}

namespace SampleProject.Services
{
    public class OrderCreatedEvent
    {
        public int OrderId { get; set; }
    }

    public class OrderService
    {
        private readonly MassTransit.IPublishEndpoint _publishEndpoint;

        public OrderService(MassTransit.IPublishEndpoint publishEndpoint)
        {
            _publishEndpoint = publishEndpoint;
        }

        public async Task CreateOrder()
        {
            await _publishEndpoint.Publish(new OrderCreatedEvent { OrderId = 1 });
        }
    }
}";

    [Fact]
    public void Detect_MassTransitPublisher_SetsMessagingTypeMassTransit()
    {
        // Arrange
        var compilation = CreateCompilation(MassTransitPublisherSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("OrderService"));
        service.Should().NotBeNull();
        service!.MessagingType.Should().Be("MassTransit");
    }

    [Fact]
    public void Detect_MassTransitPublisher_SetsMessagingRolePublisher()
    {
        // Arrange
        var compilation = CreateCompilation(MassTransitPublisherSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("OrderService"));
        service.Should().NotBeNull();
        service!.MessagingRole.Should().Be("publisher");
    }

    private static readonly string MassTransitBusPublisherSource = @"
using System.Threading.Tasks;

namespace MassTransit
{
    public interface IPublishEndpoint
    {
        Task Publish<T>(T message) where T : class;
    }
    public interface IBus : IPublishEndpoint { }
}

namespace SampleProject.Services
{
    public class NotificationEvent { public string Message { get; set; } }

    public class NotificationService
    {
        private readonly MassTransit.IBus _bus;

        public NotificationService(MassTransit.IBus bus)
        {
            _bus = bus;
        }

        public async Task SendNotification()
        {
            await _bus.Publish(new NotificationEvent { Message = ""Hello"" });
        }
    }
}";

    [Fact]
    public void Detect_MassTransitIBusPublisher_SetsMessagingRolePublisher()
    {
        // Arrange
        var compilation = CreateCompilation(MassTransitBusPublisherSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("NotificationService"));
        service.Should().NotBeNull();
        service!.MessagingType.Should().Be("MassTransit");
        service.MessagingRole.Should().Be("publisher");
    }

    #endregion

    #region Azure Service Bus Detection

    private static readonly string AzureServiceBusClientSource = @"
using System.Threading.Tasks;

namespace Azure.Messaging.ServiceBus
{
    public class ServiceBusClient
    {
        public ServiceBusClient(string connectionString) { }
        public ServiceBusSender CreateSender(string queueName) => new ServiceBusSender();
        public ServiceBusProcessor CreateProcessor(string queueName) => new ServiceBusProcessor();
    }
    public class ServiceBusSender
    {
        public Task SendMessageAsync(object message) => Task.CompletedTask;
    }
    public class ServiceBusProcessor
    {
        public event System.Func<object, Task> ProcessMessageAsync;
        public Task StartProcessingAsync() => Task.CompletedTask;
    }
}

namespace SampleProject.Services
{
    public class MessageSenderService
    {
        private readonly Azure.Messaging.ServiceBus.ServiceBusClient _client;
        private readonly Azure.Messaging.ServiceBus.ServiceBusSender _sender;

        public MessageSenderService(Azure.Messaging.ServiceBus.ServiceBusClient client)
        {
            _client = client;
            _sender = client.CreateSender(""my-queue"");
        }

        public async Task SendAsync(string message)
        {
            await _sender.SendMessageAsync(message);
        }
    }
}";

    [Fact]
    public void Detect_AzureServiceBusClient_SetsMessagingTypeAzureServiceBus()
    {
        // Arrange
        var compilation = CreateCompilation(AzureServiceBusClientSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("MessageSenderService"));
        service.Should().NotBeNull();
        service!.MessagingType.Should().Be("AzureServiceBus");
    }

    [Fact]
    public void Detect_AzureServiceBusSender_SetsMessagingRolePublisher()
    {
        // Arrange
        var compilation = CreateCompilation(AzureServiceBusClientSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("MessageSenderService"));
        service.Should().NotBeNull();
        service!.MessagingRole.Should().Be("publisher");
    }

    private static readonly string AzureServiceBusProcessorSource = @"
using System;
using System.Threading.Tasks;

namespace Azure.Messaging.ServiceBus
{
    public class ServiceBusClient
    {
        public ServiceBusClient(string connectionString) { }
        public ServiceBusProcessor CreateProcessor(string queueName) => new ServiceBusProcessor();
    }
    public class ServiceBusProcessor
    {
        public event Func<object, Task> ProcessMessageAsync;
        public Task StartProcessingAsync() => Task.CompletedTask;
    }
}

namespace SampleProject.Services
{
    public class MessageListenerService
    {
        private readonly Azure.Messaging.ServiceBus.ServiceBusProcessor _processor;

        public MessageListenerService(Azure.Messaging.ServiceBus.ServiceBusClient client)
        {
            _processor = client.CreateProcessor(""my-queue"");
        }

        public async Task StartListening()
        {
            await _processor.StartProcessingAsync();
        }
    }
}";

    [Fact]
    public void Detect_AzureServiceBusProcessor_SetsMessagingRoleConsumer()
    {
        // Arrange
        var compilation = CreateCompilation(AzureServiceBusProcessorSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("MessageListenerService"));
        service.Should().NotBeNull();
        service!.MessagingType.Should().Be("AzureServiceBus");
        service.MessagingRole.Should().Be("consumer");
    }

    #endregion

    #region No Messaging Pattern

    private static readonly string NoMessagingSource = @"
namespace SampleProject.Services
{
    public class PlainService
    {
        public string DoWork() => ""done"";
    }
}";

    [Fact]
    public void Detect_NoMessagingPattern_LeavesFieldsNull()
    {
        // Arrange
        var compilation = CreateCompilation(NoMessagingSource);
        var detector = new MessagingDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("PlainService"));
        service.Should().NotBeNull();
        service!.MessagingType.Should().BeNull();
        service.MessagingRole.Should().BeNull();
    }

    #endregion
}
