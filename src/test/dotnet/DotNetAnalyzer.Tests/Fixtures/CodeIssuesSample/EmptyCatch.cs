namespace CodeIssuesSample
{
    public class EmptyCatchService
    {
        public void DoWork()
        {
            try
            {
                var x = 1 + 1;
            }
            catch (Exception e)
            {
                // intentionally empty
            }
        }
    }
}
