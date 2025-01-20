package com.apwide.jenkins.golive

import com.apwide.jenkins.issue.ChangeLogIssueKeyExtractor
import com.apwide.jenkins.jira.Issue
import com.apwide.jenkins.util.Parameters
import com.apwide.jenkins.util.RestClient
import com.apwide.jenkins.util.ScriptWrapper
import com.apwide.jenkins.util.auth.GoliveAuthenticator

import static com.apwide.jenkins.util.RestClient.checkUrl
import static com.apwide.jenkins.util.Utilities.removeUndefined
import static com.apwide.jenkins.util.Utilities.urlEncode

class Environment implements Serializable {
    private final ScriptWrapper script
    private final RestClient golive
    private final Issue issue

    Environment(ScriptWrapper script, Parameters parameters) {
        this.script = script
        this.golive = new RestClient(script, parameters.getConfig(), new GoliveAuthenticator(script, parameters), parameters.getGoliveBaseUrl())
        this.issue = new Issue(script, parameters)
    }

    def update(id, body) {
        golive.put("/environment/${urlEncode(id)}", body)
    }

    def update(applicationName, categoryName, body) {
        def env = get(applicationName, categoryName)
        update(env.id, body)
    }

    def create(applicationName, categoryName, permissionSchemeName, body = null) {
        golive.post("/environment", [
                application                : [
                        name: applicationName
                ],
                category                   : [
                        name: categoryName
                ],
                environmentPermissionScheme: [
                        name: permissionSchemeName
                ]
        ] << (body ?: [:]))
    }

    def get(applicationName, categoryName) {
        golive.get("/environment?application=${urlEncode(applicationName)}&category=${urlEncode(categoryName)}", '200:304,404')
    }

    def get(environmentId) {
        golive.get("/environment/${environmentId}", '200:304,404')
    }

    def delete(environmentId) {
        golive.delete("/environment/${environmentId}", '200:304,404')
    }

    def getStatus(environmentId) {
        golive.get("/status-change?environmentId=${environmentId}", '200:304,404')
    }

    def setStatus(environmentId, statusName) {
        golive.put("/status-change?environmentId=${environmentId}", [name: statusName])
    }

    /*def setDeployedVersion(applicationName, categoryName, deployedVersion, buildNumber, description, attributes) {
        golive.put("/deployment?application=${urlEncode(applicationName)}&category=${urlEncode(categoryName)}", [
                versionName: deployedVersion,
                buildNumber: buildNumber,
                description: description,
                attributes : attributes
        ])
    }*/

    def checkAndUpdateStatus(environmentId, unavailableStatus, availableStatus, String dontTouchStatus = null, Closure checkStatusOperation = null) {
        def env = get(environmentId)
        def applicationName = env.application.name
        def categoryName = env.category.name
        if (!checkStatusOperation && !env.url) {
            script.debug("No check nor url provided for environment ${env.application.name}-${env.category.name}, status won't be updated")
            return
        }
        if (dontTouchStatus != null && env.status*.name == dontTouchStatus) {
            script.debug("Environment ${applicationName} ${categoryName} is in dont touch state ${dontTouchStatus} and so, it's status won't be modified")
            return
        }
        def status = [:]
        try {
            status = getStatus(environmentId)
        } catch (err) {
            // no fail on status if not exist
        }
        def checkStatus = checkStatusOperation
        if (!checkStatus) {
            checkStatus = { environment ->
                checkUrl url: environment.url,
                        nbRetry: 3,
                        httpMode: 'GET',
                        this.script
            }
            script.debug("Status is going to be checked with default check status (environment URL)")
        } else {
            script.debug("status is going to be checked with custom check status body")
        }
        try {
            script.debug("check status")
            checkStatus(env)
            if (!availableStatus.equals(status?.statusName)) {
                script.debug("set status to ${availableStatus}")
                return setStatus(environmentId, availableStatus)
            }
        } catch (err) {
            if (!unavailableStatus.equals(status?.statusName)) {
                script.debug("set status to ${unavailableStatus}")
                return setStatus(environmentId, unavailableStatus)
            } else {
                script.debug("unexpected error on checking status")
            }
        }
        return status
    }

  def sendInfo(Parameters params) {
    golive.post("/environment/information", removeUndefined([
        environmentSelector: [
            environment: [
                id: params.params.targetEnvironmentId ?: params.getEnvironmentId() as Integer,
                name: params.params.targetEnvironmentName ?: params.params.environmentName as String,
                autoCreate: params.params.targetEnvironmentAutoCreate as Boolean,
            ],
            application: [
                id: params.params.targetApplicationId as Integer,
                name: params.params.targetApplicationName ?: params.getApplication(),
                autoCreate: params.params.targetApplicationAutoCreate as Boolean,
            ],
            category: [
                id: params.params.targetCategoryId as Integer,
                name: params.params.targetCategoryName ?: params.getCategory(),
                autoCreate: params.params.targetCategoryAutoCreate as Boolean,
            ]
        ],
        environment: toEnvironment(params.params),
        status: toStatus(params.params),
        deployment: toDeployment(params.params)
    ]))
  }

  def sendReleaseInfo(Parameters params) {
    golive.post("/version", removeUndefined([
        application: [
            id: params.params.targetApplicationId as Integer,
            name: params.params.targetApplicationName as String,
            autoCreate: params.params.targetAutoCreate as Boolean,
        ],
        versionName: params.params.versionName as String,
        versionDescription: params.params.versionDescription as String,
        startDate: params.params.versionStartDate as String,
        releaseDate: params.params.versionReleaseDate as String,
        released: params.params.versionReleased as Boolean,
        issues: [
          issueKeys: toIssueKeys(params),
          jql: params.params.issuesFromJql as String,
          sendJiraNotification: params.params.sendJiraNotification as Boolean,
        ]
    ]))
  }

  private Set<String> toIssueKeys(Parameters params) {
    String[] manualIssueKeys = params.params.issueKeys as String[] ?: []
    String[] computedIssueKeys = params.params.issueKeysFromCommitHistory ? new ChangeLogIssueKeyExtractor(script).extract() as String[] : []
    return (manualIssueKeys + computedIssueKeys) as Set<String>
  }

  def toStatus(Map params = [:]) {
    def id = params.statusId as Integer
    def name = params.statusName as String
    if (id != null || !name?.isEmpty()) {
      return [id: id, name: name]
    }
  }

  def toEnvironment(Map params = [:]) {
    def url = params.environmentUrl as String
    def attributes = params.environmentAttributes as Map<String, String>
    if (!url?.isEmpty() || attributes?.size() > 0) {
      return [url: url, attributes: attributes]
    }
    // with() not working, try to access Environment class property with environmentUrl field name
//    return params.with {
//      def url = environmentUrl as String
//      def attributes = environmentAttributes as Map<String, String>
//      if (!url?.isEmpty() || attributes?.size() > 0) {
//        return [url: url, attributes: attributes]
//      }
//    }
  }

  def toDeployment(Map params = [:]) {
    def directIssueKeys = params.deploymentIssueKeys ?: []
    def commitIssueKeys = new ChangeLogIssueKeyExtractor(script).extract()
    def issueKeys = (directIssueKeys + commitIssueKeys) as Set<String>
    def versionName = params.deploymentVersionName as String
    def attributes = params.deploymentAttributes
    def buildNumber = params.deploymentBuildNumber as String
    def description = params.deploymentDescription as String
    if (versionName?.isEmpty()
        && attributes == null
        && buildNumber?.isEmpty()
        && description?.isEmpty()
        && issueKeys.isEmpty()) {
      return null
    }
    return [
        versionName: versionName,
        description: description,
        buildNumber: buildNumber,
        deployedDate: params.deploymentDeployedDate,
        attributes: attributes,
        issues: [
            issueKeys: issueKeys,
            jql: params.deploymentIssuesFromJql,
            noFixVersionUpdate: params.deploymentNoFixVersionUpdate,
            addDoneIssuesFixedInVersion: params.deploymentAddDoneIssuesOfJiraVersion,
            sendJiraNotification: params.deploymentSendJiraNotification
        ]
    ]
  }
}

