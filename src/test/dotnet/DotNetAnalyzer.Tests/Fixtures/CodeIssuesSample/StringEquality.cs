namespace CodeIssuesSample
{
    public class StringEqualityService
    {
        public bool CompareWrong(string a, string b)
        {
            return ReferenceEquals(a, b);
        }

        public bool CompareCorrect(string a, string b)
        {
            return a == b;
        }
    }
}
