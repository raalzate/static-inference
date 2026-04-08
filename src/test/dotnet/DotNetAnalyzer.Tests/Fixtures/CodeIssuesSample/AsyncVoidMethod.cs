namespace CodeIssuesSample
{
    public class NotificationService
    {
        public async void SendNotification(string message)
        {
            await Task.Delay(100);
        }

        public async Task SendNotificationAsync(string message)
        {
            await Task.Delay(100);
        }
    }
}
