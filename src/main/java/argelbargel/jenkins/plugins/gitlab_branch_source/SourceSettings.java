package argelbargel.jenkins.plugins.gitlab_branch_source;


import argelbargel.jenkins.plugins.gitlab_branch_source.api.filters.AllowMergeRequestsFromForks;
import argelbargel.jenkins.plugins.gitlab_branch_source.api.filters.AllowMergeRequestsFromOrigin;
import argelbargel.jenkins.plugins.gitlab_branch_source.api.filters.FilterWorkInProgress;
import argelbargel.jenkins.plugins.gitlab_branch_source.api.filters.GitLabMergeRequestFilter;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;

import static argelbargel.jenkins.plugins.gitlab_branch_source.BuildStatusPublishMode.no;
import static argelbargel.jenkins.plugins.gitlab_branch_source.BuildStatusPublishMode.stages;
import static argelbargel.jenkins.plugins.gitlab_branch_source.DescriptorHelper.CHECKOUT_CREDENTIALS_ANONYMOUS;


// TODO: extract common interface for GitLabSCMSource, GitLabSCMNavigator and SourceSettings
class SourceSettings {
    private static final String DEFAULT_INCLUDES = "*";
    private static final String DEFAULT_EXCLUDES = "";
    private static final String DEFAULT_MERGE_COMMIT_MESSAGE = "Accepted Merge-Request #{0} after build {1} succeeded";

    private final String connectionName;
    private String credentialsId;
    private String includes;
    private String excludes;
    private final MonitorStrategy branchMonitorStrategy;
    private final MonitorStrategy originMonitorStrategy;
    private final MonitorStrategy forksMonitorStrategy;
    private final MonitorStrategy tagMonitorStrategy;
    private boolean buildBranchesWithMergeRequests;
    private boolean listenToWebHooks;
    private boolean registerWebHooks;
    private boolean updateBuildDescription;
    private boolean publishUnstableBuildsAsSuccess;
    private String mergeCommitMessage;


    SourceSettings(String connectionName) {
        this.connectionName = connectionName;
        this.credentialsId = CHECKOUT_CREDENTIALS_ANONYMOUS;
        this.includes = DEFAULT_INCLUDES;
        this.excludes = DEFAULT_EXCLUDES;
        this.branchMonitorStrategy = new MonitorStrategy(true, false, stages);
        this.buildBranchesWithMergeRequests = false;
        this.originMonitorStrategy = new MonitorStrategy(true, true, stages);
        this.forksMonitorStrategy = new MonitorStrategy(false, true, stages);
        this.tagMonitorStrategy = new MonitorStrategy(false, false, no);
        this.listenToWebHooks = true;
        this.registerWebHooks = true;
        this.updateBuildDescription = true;
        this.publishUnstableBuildsAsSuccess = false;
        this.mergeCommitMessage = DEFAULT_MERGE_COMMIT_MESSAGE;
    }

    @Nonnull
    String getConnectionName() {
        return connectionName;
    }

    String getCredentialsId() {
        return !CHECKOUT_CREDENTIALS_ANONYMOUS.equals(credentialsId) ? credentialsId : null;
    }
    
    void setCredentialsId(String credentialsId){
        this.credentialsId = credentialsId;
    }

    String getIncludes() {
        return includes;
    }

    void setIncludes(String includes) {
        this.includes = (includes != null) ? includes : DEFAULT_INCLUDES;
    }

    String getExcludes() {
        return excludes;
    }

    void setExcludes(String excludes) {
        this.excludes = (excludes != null) ? excludes : DEFAULT_EXCLUDES;
    }

    boolean getBuildBranchesWithMergeRequests() {
        return buildBranchesWithMergeRequests;
    }

    void setBuildBranchesWithMergeRequests(boolean value) {
        buildBranchesWithMergeRequests = value;
    }

    MonitorStrategy branchMonitorStrategy() {
        return branchMonitorStrategy;
    }

    MonitorStrategy tagMonitorStrategy() {
        return tagMonitorStrategy;
    }

    MonitorStrategy originMonitorStrategy() {
        return originMonitorStrategy;
    }

    MonitorStrategy forksMonitorStrategy() {
        return forksMonitorStrategy;
    }

    boolean getListenToWebHooks() {
        return listenToWebHooks;
    }

    void setListenToWebHooks(boolean value) {
        listenToWebHooks = value;
    }

    boolean getRegisterWebHooks() {
        return registerWebHooks;
    }

    void setRegisterWebHooks(boolean value) {
        registerWebHooks = value;
    }

    void setUpdateBuildDescription(boolean value) {
        updateBuildDescription = value;
    }

    boolean getUpdateBuildDescription() {
        return updateBuildDescription;
    }

    void setPublishUnstableBuildsAsSuccess(boolean publishUnstableBuildsAsSuccess) {
        this.publishUnstableBuildsAsSuccess = publishUnstableBuildsAsSuccess;
    }

    boolean getPublishUnstableBuildsAsSuccess() {
        return publishUnstableBuildsAsSuccess;
    }

    String getMergeCommitMessage() {
        return mergeCommitMessage;
    }

    void setMergeCommitMessage(String value) {
        this.mergeCommitMessage = value;
    }

    GitLabMergeRequestFilter createMergeRequestFilter(TaskListener listener) {
        GitLabMergeRequestFilter filter = GitLabMergeRequestFilter.ALLOW_NONE;
        if (originMonitorStrategy.monitored()) {
            GitLabMergeRequestFilter originFilter = new AllowMergeRequestsFromOrigin(listener);
            if (originMonitorStrategy.ignoreWorkInProgress()) {
                originFilter = originFilter.and(new FilterWorkInProgress(listener));
            }

            filter = filter.or(originFilter);
        }

        if (forksMonitorStrategy.monitored()) {
            GitLabMergeRequestFilter forkFilter = new AllowMergeRequestsFromForks(listener);
            if (forksMonitorStrategy.ignoreWorkInProgress()) {
                forkFilter = forkFilter.and(new FilterWorkInProgress(listener));
            }

            filter = filter.or(forkFilter);
        }

        return filter;
    }

    boolean determineMergeRequestStrategyValue(GitLabSCMMergeRequestHead head, boolean originStrategy, boolean forksStrategy) {
        boolean fromOrigin = head.fromOrigin();
        return (fromOrigin && originStrategy) || (!fromOrigin && forksStrategy);
    }
}
