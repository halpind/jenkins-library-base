package com.ibm.oip.jenkins.steps.java.gradle.release

import com.ibm.oip.jenkins.BuildContext
import com.ibm.oip.jenkins.steps.java.gradle.AbstractGradleStep

class PrepareRelease extends AbstractGradleStep {
    private BuildContext buildContext;

    def bumpMapping = [
        "patch": "incrementPatch",
        "minor": "incrementMinor",
        "major": " incrementMajor"
    ]

    void doStep(BuildContext buildContext) {
        this.buildContext = buildContext;
        prepareRelease(buildContext, determineVersionDump());

        def nextVersion = retrieveNextVersion();
        buildContext.getScriptEngine().currentBuild.displayName = nextVersion;
        buildContext.setVersion(nextVersion);
    }

    public String determineVersionDump() {
        def prNumber = retrievePrId()
        if (!prNumber) {
            return "Patch"
        }

        def bump = "patch";
        buildContext.getScriptEngine() withCredentials([[$class: 'StringBinding', credentialsId: "${buildContext.getGroup()}-sonarqube-github-reporter", variable: 'GITHUB_OAUTH_TOKEN']])  {
            def labels = buildContext.getScriptEngine().sh(returnStdout: true, script: "curl -X GET -H 'Authorization: token ${buildContext.getScriptEngine().env.GITHUB_OAUTH_TOKEN}' \$GITHUB_API_URL/repos/${buildContext.getGroup()}/${buildContext.getProject()}/issues/${prNumber}/labels | jq -r '[.[].name]'")
            buildContext.getScriptEngine().sh "echo ${labels}"
            labels.each { label ->
                buildContext.getScriptEngine().sh "echo ${label}"
                if (label == "major" || label == "minor") {
                    buildContext.getScriptEngine().sh "echo 'found major or minor'"
                    bump = label;
                    return true;
                }

                return false;
            }
        }

        return bumpMapping[bump];
    }


    @NonCPS
    String retrievePrId() {
        def pr = buildContext.getCommitMessage() =~ ".*Merge pull request #(\\d+).*"
        if (!pr.hasGroup()) {
            return null;
        }
        return pr[0][1];
    }

    private void prepareRelease(buildContext, versionBump) {
        buildContext.changeStage('Create release');
        doGradleStep(buildContext, "createRelease -Prelease.disableRemoteCheck -Prelease.disableUncommittedCheck -Prelease.versionIncrementer=${versionBump} ");
    }

    private String retrieveNextVersion() {
        return buildContext.getScriptEngine().sh(returnStdout: true, script: "git describe --tags --match 'v-[0-9\\.]*'").trim();
    }

}