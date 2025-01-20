package com.apwide.jenkins.issue

import spock.lang.Specification

import static com.apwide.jenkins.issue.IssueKeyStringExtractor.extractIssueKeys

class IssueKeyStringExtractorTest extends Specification {

    def "should find 1 issueKey"() {
        given:
        def comment = "TEM-123 my comment"

        when:
        def issueKeys = extractIssueKeys(comment)

        then:
        issueKeys.size() == 1
    }

    def "should find 2 issueKeys"() {
        given:
        def comment = "TEM-123 my comment CS-234"

        when:
        def issueKeys = extractIssueKeys(comment)

        then:
        issueKeys.size() == 2
    }

}
