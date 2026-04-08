namespace CodeIssuesSample
{
    public class MissingSwitchDefaultService
    {
        public string DescribeNoDefault(int x)
        {
            switch (x)
            {
                case 1: return "one";
                case 2: return "two";
            }
            return "unknown";
        }

        public string DescribeWithDefault(int x)
        {
            switch (x)
            {
                case 1: return "one";
                default: return "other";
            }
        }
    }
}
