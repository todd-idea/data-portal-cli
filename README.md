# data-portal-cli
An example project that uses the IDEA Data Portal REST API to submit and retrieve data. This example uses Maven as the build tool.

There are currently three example command line interfaces (CLI) in this project. The first is org.ideaedu.Main and it generates sample data
and submits it to the IDEA Data Portal using the IDEA REST API.

The second CLI is org.ideaedu.GetReportModel that gets all the report data for a given report ID from the IDEA Data Portal using the IDEA
REST API.

The third CLI is org.ideaedu.WaitForReports that waits for reports to be available for a specific survey from the IDEA Data Portal using the
IDEA REST API.