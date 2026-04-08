namespace CodeIssuesSample
{
    public class DeprecatedThreadService
    {
        public void StopThread(System.Threading.Thread t)
        {
            t.Abort();
        }

        public void StartThread(System.Threading.Thread t)
        {
            t.Start();
        }
    }
}
