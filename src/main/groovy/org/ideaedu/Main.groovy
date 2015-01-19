package org.theideacenter.apps.survey

import idea.data.rest.*
import java.util.*
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import groovy.json.JsonBuilder
import java.util.Random

/**
 * The Main class provides a way to test the diagnostic survey/report by generating test data and submitting it to
 * the IDEA REST Server through the REST API. It has some optional command line arguments that control the behavior.
 * The arguments include:
 * <ul>
 * <li>h (host) - the hostname of the IDEA REST Server</li>
 * <li>p (port) - the port that is open on the IDEA REST Server</li>
 * <li>b (basePath) - the base path within the IDEA REST Server</li>
 * <li>sid (srcID) - the survey source ID</li>
 * <li>sgid (srcGroupID) - the survey source group ID</li>
 * <li>iid (institutionID) - the institution ID to use for this survey (which institution this survey is associated with)</li>
 * <li>v (verbose) - provide more output on the command line</li>
 * <li>a (app) - the client application name</li>
 * <li>k (key) - the client application key</li>
 * <li>t (type) - the type of survey to submit (chair, admin, or diag)</li>
 * <li>? (help) - show the usage of this</li>
 * </ul>
 *
 * @author Todd Wallentine todd AT theideacenter org
 */
public class Main {

	private static final String DEFAULT_SRC_ID = "1"
	private static final String DEFAULT_SRC_GROUP_ID = "2"
	private static final int DEFAULT_INSTITUTION_ID = 3019 // ID_INSTITUTION in Combo for The IDEA Center
	private static final String DEFAULT_HOSTNAME = "localhost"
	private static final int DEFAULT_PORT = 8091
	private static final String DEFAULT_BASE_PATH = "IDEA-REST-SERVER/v1/"
	private static final int DIAG_INFO_FORM_ID = 1
	private static final int DIAG_RATER_FORM_ID = 9
	private static final int SHORT_INFO_FORM_ID = 1
	private static final int SHORT_RATER_FORM_ID = 10
	private static final int ADMIN_INFO_FORM_ID = 16
	private static final int ADMIN_RATER_FORM_ID = 18
	private static final int CHAIR_INFO_FORM_ID = 13
	private static final int CHAIR_RATER_FORM_ID = 14
	private static final Integer DISCIPLINE_CODE = new Integer(1107)
	private static final String PROGRAM_CODE = "11.0701"
	private static final def DEFAULT_AUTH_HEADERS = [ "X-IDEA-APPNAME": "", "X-IDEA-KEY": "" ]
	private static final def DEFAULT_TYPE = "diag"

	private static String hostname = DEFAULT_HOSTNAME
	private static int port = DEFAULT_PORT
	private static String basePath = DEFAULT_BASE_PATH
	private static String srcID = DEFAULT_SRC_ID
	private static String srcGroupID = DEFAULT_SRC_GROUP_ID
	private static int institutionID = DEFAULT_INSTITUTION_ID
	private static def authHeaders = DEFAULT_AUTH_HEADERS
	private static def type = DEFAULT_TYPE

	private static boolean verboseOutput = false

	private static RESTClient restClient

	private static Random random = new Random() // TODO Should we seed it? -todd 11Jun2013

	public static void main(String[] args) {

		/*
		 * TODO Other command line options that might be useful:
		 * 1) app name (to include in header)
		 * 2) app key (to include in header)
		 * 3) survey type (chair, admin, short, ...) - currently hard-coded to diagnostic
		 * 4) data file - contents define the answers to info form and rater form questions
		 * 5) year, term, start/end date, gap analysis flag
		 */
		def cli = new CliBuilder( usage: 'Main -v -h host -p port -b basePath -sid srcID -sgid srcGroupID -iid instID -a "TestClient" -k "ABCDEFG123456"' )
		cli.with {
			v longOpt: 'verbose', 'verbose output'
			h longOpt: 'host', 'host name (default: localhost)', args:1
			p longOpt: 'port', 'port number (default: 8091)', args:1
			b longOpt: 'basePath', 'base REST path (default: IDEA-REST-SERVER/v1/', args:1
			sid longOpt: 'srcID', 'source ID', args:1
			sgid longOpt: 'srcGroupID', 'source Group ID', args:1
			iid longOpt: 'institutionID', 'institution ID', args:1
			a longOpt: 'app', 'client application name', args:1
			k longOpt: 'key', 'client application key', args:1
			t longOpt: 'type', 'survey type', args: 1
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
			srcID = options.sid
		}
		if(options.sgid) {
			srcGroupID = options.sgid
		}
		if(options.iid) {
			institutionID = options.iid.toInteger()
		}
		if(options.a) {
			authHeaders['X-IDEA-APPNAME'] = options.a
		}
		if(options.k) {
			authHeaders['X-IDEA-KEY'] = options.k
		}
		if(options.t) {
			if("diag".equals(options.t)) {
				type = "diag"
			} else if("short".equals(options.t)) {
				type = "short"
			} else if("admin".equals(options.t)) {
				type = "admin"
			} else if("chair".equals(options.t)) {
				type = "chair"
			} else {
				type = DEFAULT_TYPE
			}

		}

		int year = 2013
		String term = "Spring"
		boolean includesGapAnalysis = false

		Date today = new Date()
		Date yesterday = today - 1
		Date creationDate = today - 10 // 10 days ago
		Date infoFormStartDate = today - 9 // 9 days ago
		Date infoFormEndDate = yesterday
		Date raterFormStartDate = today - 5 // 5 days ago
		Date raterFormEndDate = yesterday

		def infoFormID
		def raterFormID
		if(type == "diag") {
			infoFormID = DIAG_INFO_FORM_ID
			raterFormID = DIAG_RATER_FORM_ID
		} else if(type == "short") {
			infoFormID = SHORT_INFO_FORM_ID
			raterFormID = SHORT_RATER_FORM_ID
		} else if(type == "admin") {
			infoFormID = ADMIN_INFO_FORM_ID
			raterFormID = ADMIN_RATER_FORM_ID
		} else if(type == "chair") {
			infoFormID = CHAIR_INFO_FORM_ID
			raterFormID = CHAIR_RATER_FORM_ID
		}

		RESTForm restInfoForm = buildRESTInfoForm(infoFormStartDate, infoFormEndDate, infoFormID)
		RESTForm restRaterForm = buildRESTRaterForm(raterFormStartDate, raterFormEndDate, 10, raterFormID)
		RESTCourse course
		if((type == "diag") || (type == "short")) {
			// course is only valid for Diagnostic and Short reports
			course = buildRESTCourse()
		}
		RESTSurvey restSurvey = new RESTSurvey(srcId: srcID, srcGroupId: srcGroupID, institutionId: institutionID,
			year: year, term: term, includesGapAnalysis: includesGapAnalysis, creationDate: creationDate,
			infoForm: restInfoForm, raterForm: restRaterForm, course: course)

		submitSurveyData(restSurvey)
	}

	/**
	 * Build an instance of RESTCourse.
	 *
	 * @return A new RESTCourse that can be used in a RESTSurvey.
	 */
	private static RESTCourse buildRESTCourse() {
		def restCourse = new RESTCourse(title: "Intro to IDEA", number: "IDEA 101", localCode: "0 234 67", time: "MTWUF", days: "08:00")
		return restCourse
	}

	/**
	 * Build an instance of RESTForm that is a rater form that has the given number of respondents (numberAsked),
	 * starts on the date given (startDate) and ends on the date given (endDate).
	 *
	 * @param Date startDate The date that this rater form will open.
	 * @param Date endDate The date that this rater form will close.
	 * @param int numberAsked The number of respondents that are asked to respond to this survey.
	 * @param raterFormID The ID of the rater/response form.
	 * @return RESTForm A new RESTForm instance that is populated with test data.
	 */
	private static RESTForm buildRESTRaterForm(Date startDate, Date endDate, int numberAsked, int raterFormID) {

		List<RESTQuestionGroup> questionGroups = getQuestionGroups(raterFormID)

		RESTForm restRaterForm = new RESTForm(id: raterFormID, numberAsked: numberAsked, startDate: startDate, endDate: endDate)
		Set<RESTRespondent> respondents = new HashSet<RESTRespondent>()
		for(int i = 0; i < numberAsked; i++) {
			RESTRespondent rater = new RESTRespondent()
			rater.setType("rater")
			Set<RESTResponse> responses = buildRESTResponses(questionGroups)
			rater.setResponses(responses)
			respondents.add(rater)
		}
		restRaterForm.setRespondents(respondents)

		return restRaterForm
	}

	/**
	 * Build an instance of RESTForm that is an information form that starts on the date given (startDate) and ends on the
	 * date given (endDate).
	 *
	 * @param Date startDate The date that this information form will open.
	 * @param Date endDate The date that this information form will close.
	 * @return RESTForm A new RESTForm instance that is populated with test data.
	 */
	private static RESTForm buildRESTInfoForm(Date startDate, Date endDate, int infoFormID) {

		List<RESTQuestionGroup> questionGroups = getQuestionGroups(infoFormID);

		def title
		def disciplineCode
		def programCode
		if(infoFormID == DIAG_INFO_FORM_ID) {
			// this is a diagnostic survey so we add in a discipline code and program code
			disciplineCode = DISCIPLINE_CODE
			programCode = PROGRAM_CODE
			title = "Assistant Professor"
		} else if(infoFormID == SHORT_INFO_FORM_ID) {
			// this is a short survey so we add in a discipline code and program code
			disciplineCode = DISCIPLINE_CODE
			programCode = PROGRAM_CODE
			title = "Associate Professor"
		} else if(infoFormID == CHAIR_INFO_FORM_ID) {
			title = "Chair"
		} else if(infoFormID == ADMIN_INFO_FORM_ID) {
			title = "Vice Provost"
		}

		RESTForm restInfoForm = new RESTForm(id: infoFormID, numberAsked: 1, startDate: startDate, endDate: endDate,
			disciplineCode: disciplineCode, programCode: programCode)
		Set<RESTRespondent> respondents = new HashSet<RESTRespondent>()
		def firstName = "Test"
		def lastName = "Subject" + random.nextInt()
		RESTRespondent surveySubject = new RESTRespondent(type: "subject", firstName: firstName, lastName: lastName, title: title)
		Set<RESTResponse> responses = buildRESTResponses(questionGroups)
		surveySubject.setResponses(responses)
		respondents.add(surveySubject)
		restInfoForm.setRespondents(respondents)

		return restInfoForm
	}

	/**
	 * Build a Set of RESTResponse instances that answer all the questions in the given question groups.
	 *
	 * @param questionGroups The List of RESTQuestionGroup instances to answer.
	 * @return Set<RESTResponse> The Set of RESTResponse instances that hold answers to the given questions.
	 */
	private static Set<RESTResponse> buildRESTResponses(List<RESTQuestionGroup> questionGroups) {
		Set<RESTResponse> responses = new HashSet<RESTResponse>()

		for(RESTQuestionGroup questionGroup : questionGroups) {
			for(RESTQuestion question : questionGroup.questions) {
				def answer = getRandomAnswer(question, questionGroup)
				RESTResponse response = new RESTResponse(groupType: "standard", questionId: question.id, answer: answer)
				responses.add(response)
			}
		}

		return responses
	}

	/**
	 * Get an answer to the given question. This will select a valid response option if it exists (as an answer to a scaled question)
	 * but otherwise it will simply generate a random String (as an answer to an open question).
	 *
	 * Note: To deal with the "optimization" of response options, we need to pass in the RESTQuestionGroup so that
	 * we have access to response options for this question. The optimization was to allow response options to be
	 * defined at the question group level if all response options are the same for a set of questions.
	 *
	 * @param RESTQuestion question The question to create the answer for.
	 * @param RESTQuestionGroup questionGroup The question group this question is associated with.
	 * @return An answer to the question.
	 */
	private static def getRandomAnswer(RESTQuestion question, RESTQuestionGroup questionGroup) {
		def answer

		// Priority is given to question response options. If not defined, use the question group response options.
		if(question.responseOptions != null && question.responseOptions.size > 0) {
			def index = random.nextInt(question.responseOptions.size)
			answer = question.responseOptions[index].value
		} else if(questionGroup.responseOptions != null && questionGroup.responseOptions.size > 0) {
			def index = random.nextInt(questionGroup.responseOptions.size)
			answer = questionGroup.responseOptions[index].value
		} else {
			answer = "Test Answer ${random.nextInt()}"
		}

		return answer
	}

	/**
	 * Get the List of RESTQuestionGroup instances that are associated with the given formID. This will query the
	 * REST API.
	 *
	 * @param int formID The form to get the question groups for.
	 * @return List<RESTQuestionGroup> The List of RESTQuestionGroup instances that are associated with the given form(formID).
	 */
	private static List<RESTQuestionGroup> getQuestionGroups(int formID) {
		List<RESTQuestionGroup> questionGroups = new ArrayList<RESTQuestionGroup>()

		def client = getRESTClient()
		if(verboseOutput) println "Retrieving questions for form ${formID}..."
		def r = client.get(path: "${basePath}/forms/${formID}/questions", headers: authHeaders)

		r.data.data.each { qg ->
			// TODO Is there a better way to convert it from the Map (qg) to RESTQuestionGroup? -todd 11Jun2013
			def jsonBuilder = new JsonBuilder(qg)
			def qgJSON = jsonBuilder.toString()
			def restQG = RESTQuestionGroup.fromJSON(qgJSON)
			questionGroups.add(restQG)
		}

		if(verboseOutput) println "Retrieved ${questionGroups.size} question groups for form ${formID}."

		return questionGroups
	}

	/**
	 * Submit the survey data to the REST API.
	 *
	 * @param RESTSurvey restSurvey The survey data to submit to the REST API.
	 */
	private static void submitSurveyData(RESTSurvey restSurvey) {

		def json = restSurvey.toJSON()
		if(verboseOutput) println "JSON: ${json}"
		def client = getRESTClient()
		try {
			def response = client.post(
				path: "${basePath}/services/survey",
				body: json,
				requestContentType: ContentType.JSON,
				headers: authHeaders)

			if(verboseOutput) println "Status: ${response.status}"
			println response.data
		} catch (ex) {
			println "Caught an exception while submitting the survey data:"
			println "Status: ${ex.response.status}"
			println "Status-Line: ${ex.response.statusLine}"
			println "Content-type: ${ex.response.contentType}"
			println "Data: ${ex.response.data}"
		}
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