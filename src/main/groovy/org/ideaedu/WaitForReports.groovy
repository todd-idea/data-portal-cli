package org.ideaedu

import idea.data.rest.*
import java.util.*
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import groovy.json.JsonBuilder

/**
 * The WaitForReports class provides a way to test the REST API by repeatedly sending a query for reports
 * related to a specific survey ID. Once the reports are all available, this will exit. It has some optional
 * command line arguments that control the behavior. The arguments include:
 * <ul>
 * <li>h (host) - the hostname of the IDEA REST Server</li>
 * <li>p (port) - the port that is open on the IDEA REST Server</li>
 * <li>b (basePath) - the base path within the IDEA REST Server</li>
 * <li>sid (surveyID) - the survey ID</li>
 * <li>v (verbose) - provide more output on the command line</li>
 * <li>a (app) - the client application name</li>
 * <li>k (key) - the client application key</li>
 * <li>? (help) - show the usage of this</li>
 * </ul>
 *
 * @author Todd Wallentine todd AT theideacenter org
 */
public class WaitForReports {

	private static final int DEFAULT_SURVEY_ID = 1
	private static final String DEFAULT_HOSTNAME = "localhost"
	private static final int DEFAULT_PORT = 8091
	private static final String DEFAULT_BASE_PATH = "IDEA-REST-SERVER/v1/"
	private static final def DEFAULT_AUTH_HEADERS = [ "X-IDEA-APPNAME": "", "X-IDEA-KEY": "" ]
	private static final def MAX_REQUESTS = 10
	private static final def WAIT_TIME_IN_SEC = 1

	private static String hostname = DEFAULT_HOSTNAME
	private static int port = DEFAULT_PORT
	private static String basePath = DEFAULT_BASE_PATH
	private static int surveyID = DEFAULT_SURVEY_ID
	private static def authHeaders = DEFAULT_AUTH_HEADERS

	private static boolean verboseOutput = false

	private static RESTClient restClient

	private static Random random = new Random() // TODO Should we seed it? -todd 11Jun2013

	public static void main(String[] args) {

		/*
		 * TODO Other command line options that might be useful:
		 * 1) app name (to include in header)
		 * 2) app key (to include in header)
		 */
		def cli = new CliBuilder( usage: 'WaitForReports -v -h host -p port -b basePath -sid surveyID -c iol3' )
		cli.with {
			v longOpt: 'verbose', 'verbose output'
			h longOpt: 'host', 'host name (default: localhost)', args:1
			p longOpt: 'port', 'port number (default: 8091)', args:1
			b longOpt: 'basePath', 'base REST path (default: IDEA-REST-SERVER/v1/', args:1
			sid longOpt: 'surveyID', 'survey ID', args:1
			a longOpt: 'app', 'client application name', args:1
			k longOpt: 'key', 'client application key', args:1
			'?' longOpt: 'help', 'help'
		}
		def options = cli.parse(args)
		if(options.'?') {
			cli.usage()
			return
		}
		if(options.v) {
			verboseOutput = true
		}
		if(options.h) {
			hostname = options.h
		}
		if(options.p) {
			port = options.p.toInteger()
		}
		if(options.b) {
			basePath = options.b
		}
		if(options.sid) {
			surveyID = options.sid.toInteger()
		}
		if(options.a) {
			authHeaders['X-IDEA-APPNAME'] = options.a
		}
		if(options.k) {
			authHeaders['X-IDEA-KEY'] = options.k
		}

		List<Integer> reportIDs = getReportIDsWhenReady(surveyID)

		println "All reports for survey ${surveyID} are available: ${reportIDs}"
	}

	private static List<Integer> getReportIDsWhenReady(surveyID) {
		def reportIDs = []

		boolean allReportsReady = false
		def requests = 0
		while(!allReportsReady && requests < MAX_REQUESTS) {

			if(verboseOutput) println "Sending a request (${requests}) for survey ${surveyID} ..."
			def reports = getReportStatusForSurvey(surveyID)
			requests++
			if(reports) {
				allReportsReady = true
				reports.each { report ->
					if(!("available".equals(report.status))) {
						allReportsReady = false
					} else {
						reportIDs.add(report.id)
					}
				}
			}

			if(!allReportsReady) {
				reportIDs.clear()
				if(verboseOutput) println "Waiting ${WAIT_TIME_IN_SEC} before requesting again ..."
				sleep(WAIT_TIME_IN_SEC * 1000)
			}
		}

		if(requests == MAX_REQUESTS) {
			println "Not all reports are available in the time and number of requests specified."
		}

		return reportIDs
	}

	private static Object getReportStatusForSurvey(surveyID) {
		def reports

		def client = getRESTClient()
		def response = client.get(
			path: "${basePath}/reports",
			query: [survey_id: surveyID],
			requestContentType: ContentType.JSON,
			headers: authHeaders)
		if(response.status == 200) {
			if(verboseOutput) println "Response.data: ${response.data}"
			reports = response.data.data
		} else {
			println "An error occurred while getting the reports for survey ${surveyID}: ${response.status}"
		}

		return reports
	}

	/**
	 * Get an instance of the RESTClient that can be used to access the REST API.
	 *
	 * @return RESTClient An instance that can be used to access the REST API.
	 */
	private static RESTClient getRESTClient() {
		if(restClient == null) {
			if(verboseOutput) println "REST requests will be sent to ${hostname} on port ${port}"
			restClient = new RESTClient("http://${hostname}:${port}/")
		}
		return restClient
	}
}