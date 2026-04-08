namespace CodeIssuesSample
{
    public class GenericExceptionService
    {
        public void DoWork()
        {
            try
            {
                var x = 1 + 1;
            }
            catch (Exception e)
            {
                throw;
            }
        }
    }
}
