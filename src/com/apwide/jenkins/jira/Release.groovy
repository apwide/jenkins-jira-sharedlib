package com.apwide.jenkins.jira

import com.apwide.jenkins.issue.ChangeLogIssueKeyExtractor
import com.apwide.jenkins.util.*
import com.apwide.jenkins.util.auth.JiraAuthenticator

class Release implements Serializable {
    private final ScriptWrapper script
    private final RestClient jira
    private final Issue issue
    private final Parameters parameters
    private final Version version
    private final Project project

    Release(ScriptWrapper script, Parameters parameters) {
        this.script = script
        this.jira = new RestClient(script, parameters.getConfig(), new JiraAuthenticator(script, parameters), parameters.getJiraAPIUrl('/'))
        this.issue = new Issue(script, parameters)
        this.version = new Version(script, parameters)
        this.project = new Project(script, parameters)
        this.parameters = parameters
    }

    def sendReleaseInfo(versionName, versionDescription, projectIdOrKey, startDate, Collection<String> issueKeys, released, releaseDate) {
        script.debug("apwSendReleaseInfo to Jira...")
        try {
            def computedIssueKeys = issueKeys ?: new ChangeLogIssueKeyExtractor(script).extract() as String[]
            script.debug("""
              versionName=${versionName},
              versionDescription=${versionDescription},
              projectIdOrKey=${projectIdOrKey},
              startDate=${startDate},
              issueKeys=${computedIssueKeys},
              released=${released},
              releaseDate=${releaseDate}
            """.stripIndent())

            def projectVersions = project.versions(projectIdOrKey)
            def targetVersion
            def existingVersion = projectVersions.find({ it -> versionName.equalsIgnoreCase(it.name) })
            if (existingVersion) {
                targetVersion = existingVersion
            } else {
                def project = project.get(projectIdOrKey)
                targetVersion = version.create([
                        name       : versionName,
                        description: versionDescription ?: 'Created by Apwide Golive Jenkins shared lib',
                        projectId  : project.id,
                        startDate  : startDate ?: new Date()
                ])
            }

            def payload = [:]
            if (released) {
                payload.released = released
            }
            if (versionDescription) {
                payload.description = versionDescription
            }
            if (releaseDate || released) {
                payload.releaseDate = releaseDate ?: new Date()
            }
            if (startDate) {
                payload.startDate = startDate
            }

            if (!payload.isEmpty()) {
                targetVersion = version.update(targetVersion.id, payload)
            }
            for (String issueKey : computedIssueKeys) {
                try {
                    issue.addFixVersion(issueKey, targetVersion.name)
                }
                catch (Throwable e) {
                    script.debug("Could not set fixVersion for this issueKey: ${issueKey} (${e})")
                }
            }

            return targetVersion


        }

        catch (Throwable e) {
            script.debug("Unexpected error in apwSendReleaseInfo to Jira: ${e}")
            script.debug("Error message: ${e.getMessage()}")
            throw e
        }
    }

}
