package com.apwide.jenkins.issue

import com.apwide.jenkins.util.ScriptWrapper
import hudson.model.Result
import hudson.plugins.git.GitChangeSet
import hudson.scm.ChangeLogSet

import static com.apwide.jenkins.issue.IssueKeyStringExtractor.extractIssueKeys

/**
 * Parses the change log from the current build and extracts the issue keys from the commit
 * messages. It also tries to extract from squashed commits.
 */
class ChangeLogIssueKeyExtractor {
  private static final Integer ISSUE_KEY_MAX_LIMIT = 100;

  private final ScriptWrapper script;

  ChangeLogIssueKeyExtractor(ScriptWrapper script) {
    this.script = script;
  }

  Collection<String> extract() {

    final Collection<String> issueKeys = new LinkedHashSet<>()
    issueKeys.addAll(extractFromChangeSets(script.getChangeSets()))

    def previousBuild = script.getPreviousBuild()
    while (Objects.nonNull(previousBuild) && !isBuildSuccessful(previousBuild) && issueKeys.size() < ISSUE_KEY_MAX_LIMIT) {
      script.debug("Look for ChangeSet in previous unsuccessful build ${previousBuild} ${previousBuild.getClass()}")
      issueKeys.addAll(extractFromChangeSets(previousBuild.getChangeSets()))
      previousBuild = previousBuild.getPreviousBuild()
    }

    script.debug("String issue Keys: ${issueKeys}")
    return issueKeys
  }

  private Collection<String> extractFromChangeSets(List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = new ArrayList<>()) {
    Collection<String> issueKeys = new ArrayList<>()
    for (def changeSet : changeSets) {
      issueKeys.addAll(extractFromChangeSet(changeSet))
    }
    return issueKeys
  }

  private Collection<String> extractFromChangeSet(ChangeLogSet<? extends ChangeLogSet.Entry> changeSet) {
    final Collection<String> issueKeys = new LinkedHashSet<>()
    def iterator = changeSet.iterator()
    while (iterator.hasNext() && issueKeys.size() < ISSUE_KEY_MAX_LIMIT) {
      def entry = iterator.next()
      script.debug("changeSetEntry: ${entry}")

      String changeContent = "Message: " + entry.getMsg() + "\n"
      if (entry instanceof GitChangeSet) {
        changeContent += "Comment: " + ((GitChangeSet) entry).getComment()
      }
      def contentIssueKeys = extractIssueKeys(changeContent)
      script.debug("Change item content:\n${changeContent}")
      script.debug("IssueKeys in content: ${contentIssueKeys}")

      issueKeys.addAll(contentIssueKeys)
    }
    return issueKeys;
  }

  private boolean isBuildSuccessful(final build) {
    return Result.SUCCESS.toString() == build.getResult().toString()
  }
}
