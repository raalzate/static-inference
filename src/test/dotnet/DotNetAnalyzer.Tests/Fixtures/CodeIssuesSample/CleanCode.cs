namespace CodeIssuesSample
{
    public class CleanService
    {
        private readonly string _name;

        public CleanService(string name)
        {
            _name = name;
        }

        public string GetName() => _name;
    }
}
