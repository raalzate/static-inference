namespace CodeIssuesSample
{
    public class OrderProcessingService
    {
        private readonly Dictionary<string, string> _config = new();

        public void ProcessOrder()
        {
            var ordersTopic = _config["topic"];
            var orderId = _config["orderId"];
            System.Console.WriteLine(orderId);
        }
    }
}
