package argelbargel.jenkins.plugins.gitlab_branch_source;


import argelbargel.jenkins.plugins.gitlab_branch_source.api.GitLabAPIException;
import argelbargel.jenkins.plugins.gitlab_branch_source.api.GitLabProject;
import argelbargel.jenkins.plugins.gitlab_branch_source.api.filters.GitLabMergeRequestFilter;
import argelbargel.jenkins.plugins.gitlab_branch_source.hooks.GitLabSCMWebHook;
import argelbargel.jenkins.plugins.gitlab_branch_source.hooks.GitLabSCMWebHookListener;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.GitLab;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabHelper.gitLabAPI;
import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabSCMIcons.ICON_GITLAB;
import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabSCMRefSpec.BRANCHES;
import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabSCMRefSpec.MERGE_REQUESTS;
import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabSCMRefSpec.TAGS;


// TODO: extract common interface for GitLabSCMSource, GitLabSCMNavigator and SourceSettings
@SuppressWarnings({"unused", "WeakerAccess"})
public class GitLabSCMSource extends AbstractGitSCMSource {
    private static final Logger LOGGER = Logger.getLogger(GitLabSCMSource.class.getName());

    private final SourceSettings settings;
    private final SourceHeads heads;
    private final SourceActions actions;
    private final GitLabSCMWebHookListener hookListener;
    private GitLabProject project;

    GitLabSCMSource(@Nonnull SourceSettings settings, GitLabProject project) {
        super(null);
        this.settings = settings;
        this.project = project;
        this.hookListener = GitLabSCMWebHook.createListener(this);
        this.actions = new SourceActions(this);
        this.heads = new SourceHeads(this);
    }

    public String getConnectionName() {
        return settings.getConnectionName();
    }

    @Override
    public String getCredentialsId() {
        return settings.getCredentialsId();
    }

    @Override
    public final String getRemote() {
        return project.getRemote(this);
    }

    @Override
    public String getIncludes() {
        return settings.getIncludes();
    }

    @Override
    public String getExcludes() {
        return settings.getExcludes();
    }

    public boolean getMonitorAndBuildBranches() {
        return settings.branchMonitorStrategy().monitored();
    }

    public boolean getBuildBranchesWithMergeRequests() {
        return settings.originMonitorStrategy().monitored() && settings.getBuildBranchesWithMergeRequests();
    }

    public boolean getMonitorAndBuildMergeRequestsFromOrigin() {
        return settings.originMonitorStrategy().monitored();
    }

    public boolean getBuildMergeRequestsFromOriginMerged() {
        return settings.originMonitorStrategy().buildMerged();
    }

    public boolean getBuildOnlyMergeableRequestsFromOriginMerged() {
        return settings.originMonitorStrategy().buildOnlyMergeableRequestsMerged();
    }

    public boolean getBuildMergeRequestsFromOriginUnmerged() {
        return settings.originMonitorStrategy().buildUnmerged();
    }

    public boolean getIgnoreWorkInProgressFromOrigin() {
        return settings.originMonitorStrategy().ignoreWorkInProgress();
    }

    public boolean getMonitorAndBuildMergeRequestsFromForks() {
        return settings.forksMonitorStrategy().monitored();
    }

    public boolean getBuildMergeRequestsFromForksMerged() {
        return settings.forksMonitorStrategy().buildMerged();
    }

    public boolean getBuildOnlyMergeableRequestsFromForksMerged() {
        return settings.forksMonitorStrategy().buildOnlyMergeableRequestsMerged();
    }

    public boolean getBuildMergeRequestsFromForksUnmerged() {
        return settings.forksMonitorStrategy().buildUnmerged();
    }

    public boolean getIgnoreWorkInProgressFromForks() {
        return settings.forksMonitorStrategy().ignoreWorkInProgress();
    }

    public boolean getMonitorTags() {
        return settings.tagMonitorStrategy().monitored();
    }

    public boolean getBuildTags() {
        return settings.tagMonitorStrategy().buildUnmerged();
    }

    public boolean getRegisterWebHooks() {
        return settings.getRegisterWebHooks();
    }

    public boolean getUpdateBuildDescription() {
        return settings.getUpdateBuildDescription();
    }

    public boolean getPublishUnstableBuildsAsSuccess() {
        return settings.getPublishUnstableBuildsAsSuccess();
    }

    public boolean getAcceptMergeRequestsFromOrigin() {
        return settings.originMonitorStrategy().getAcceptMergeRequests();
    }

    public boolean getAcceptMergeRequestsFromForks() {
        return settings.forksMonitorStrategy().getAcceptMergeRequests();
    }

    public boolean getRemoveSourceBranchFromOrigin() {
        return settings.originMonitorStrategy().getRemoveSourceBranch();
    }

    public BuildStatusPublishMode getBranchBuildStatusPublishMode() {
        return settings.branchMonitorStrategy().getBuildStatusPublishMode();
    }

    public BuildStatusPublishMode getOriginBuildStatusPublishMode() {
        return settings.originMonitorStrategy().getBuildStatusPublishMode();
    }

    public BuildStatusPublishMode getForkBuildStatusPublishMode() {
        return settings.forksMonitorStrategy().getBuildStatusPublishMode();
    }

    public BuildStatusPublishMode getTagBuildStatusPublishMode() {
        return settings.tagMonitorStrategy().getBuildStatusPublishMode();
    }

    GitLabProject getProject() {
        return project;
    }

    public int getProjectId() {
        return (project != null) ? project.getId() : -1;
    }

    public String getProjectPath() {
        return (project != null) ? project.getPathWithNamespace() : "";
    }

    public String getHookUrl() {
        return hookListener.url().toString();
    }

    public GitLabSCMWebHookListener getHookListener() {
        return hookListener;
    }

    @Override
    public GitRepositoryBrowser getBrowser() {
        try {
            return new GitLab(project.getWebUrl(), getGitLabVersion());
        } catch (GitLabAPIException e) {
            LOGGER.warning("could not determine gitlab-version:" + e.getMessage());
            return super.getBrowser();
        }
    }

    public boolean buildMerged(GitLabSCMMergeRequestHead head) {
        return settings.determineMergeRequestStrategyValue(head, settings.originMonitorStrategy().buildMerged(), settings.forksMonitorStrategy().buildMerged());

    }

    public boolean buildUnmerged(GitLabSCMMergeRequestHead head) {
        return settings.determineMergeRequestStrategyValue(head, settings.originMonitorStrategy().buildUnmerged(), settings.forksMonitorStrategy().buildUnmerged());
    }

    public String getMergeCommitMessage() {
        return settings.getMergeCommitMessage();
    }


    final String getGitLabVersion() throws GitLabAPIException {
        return gitLabAPI(getConnectionName()).getVersion().toString();
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @Nonnull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().format(Messages.GitLabSCMSource_retrievingHeadsForProject(project.getPathWithNamespace()) + "\n");
        heads.retrieve(criteria, observer, event, listener);
    }

    @Override
    @CheckForNull
    protected SCMRevision retrieve(@Nonnull SCMHead head, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        return heads.retrieve(head, listener);
    }

    @Nonnull
    @Override
    protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event, @Nonnull TaskListener listener) throws IOException {
        return actions.retrieve(event, listener);
    }

    @Nonnull
    @Override
    protected List<Action> retrieveActions(@Nonnull SCMHead head, @CheckForNull SCMHeadEvent event, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        return actions.retrieve(head, event, listener);
    }

    @Nonnull
    @Override
    protected List<Action> retrieveActions(@Nonnull SCMRevision revision, @CheckForNull SCMHeadEvent event, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        return actions.retrieve(revision, event, listener);
    }

    @Override
    protected List<RefSpec> getRefSpecs() {
        List<RefSpec> refSpecs = new LinkedList<>();

        if (settings.branchMonitorStrategy().monitored()) {
            refSpecs.add(BRANCHES.delegate());
        }
        if (settings.tagMonitorStrategy().monitored()) {
            refSpecs.add(TAGS.delegate());
        }
        if (settings.originMonitorStrategy().monitored() || settings.forksMonitorStrategy().monitored()) {
            refSpecs.add(MERGE_REQUESTS.delegate());
        }

        return refSpecs;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    protected boolean isCategoryEnabled(@Nonnull SCMHeadCategory category) {
        if (!super.isCategoryEnabled(category)) {
            return false;
        }

        if (category instanceof ChangeRequestSCMHeadCategory) {
            return getMonitorAndBuildMergeRequestsFromOrigin() || getMonitorAndBuildMergeRequestsFromForks();
        }

        if (category instanceof TagSCMHeadCategory) {
            return getMonitorTags();
        }

        return true;
    }

    String getDescription() {
        return project.getDescription();
    }

    @Nonnull
    @Override
    public SCM build(@Nonnull SCMHead head, @CheckForNull SCMRevision revision) {
        GitSCM scm;

        if (head instanceof GitLabSCMHead) {
            scm = ((GitLabSCMHead) head).createSCM(this);
            if (revision instanceof SCMRevisionImpl) {
                scm.getExtensions().add(new BuildChooserSetting(new SpecificRevisionBuildChooser((SCMRevisionImpl) revision)));
            }
        } else {
            scm = (GitSCM) super.build(head, revision);
            scm.setBrowser(getBrowser());
        }

        return scm;
    }

    GitLabMergeRequestFilter createMergeRequestFilter(TaskListener listener) {
        return settings.createMergeRequestFilter(listener);
    }

    boolean determineMergeRequestStrategyValue(GitLabSCMMergeRequestHead head, boolean originValue, boolean forksValue) {
        return settings.determineMergeRequestStrategyValue(head, originValue, forksValue);
    }

    @Override
    protected boolean isExcluded(String branchName) {
        return super.isExcluded(branchName);
    }

    @Nonnull
    @Override
    protected SCMProbe createProbe(@Nonnull SCMHead head, @CheckForNull SCMRevision revision) {
        return GitLabSCMProbe.create(this, head, revision);
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor implements IconSpec {
        @Nonnull
        public String getDisplayName() {
            return Messages.GitLabSCMSource_DisplayName();
        }

        @Override
        public String getPronoun() {
            return Messages.GitLabSCMSource_Pronoun();
        }

        @Override
        public String getIconClassName() {
            return ICON_GITLAB;
        }

        @Override
        public boolean isApplicable(Class<? extends SCMSourceOwner> owner) {
            return false; // GitLabSCMSource can only be created via GitLabSCMNavigator for now...
        }

        @Nonnull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return GitLabSCMHeadCategory.ALL;
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckConnectionName(@AncestorInPath SCMSourceOwner context, @QueryParameter String connectionName) {
            return DescriptorHelper.doCheckConnectionName(connectionName);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckIncludes(@QueryParameter String includes) {
            return DescriptorHelper.doCheckIncludes(includes);
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillConnectionNameItems() {
            return DescriptorHelper.doFillConnectionNameItems();
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String connectionName, @QueryParameter String credentialsId) {
            return DescriptorHelper.doFillCheckoutCredentialsIdItems(context, connectionName, credentialsId);
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillBranchBuildStatusPublishModeItems() {
            return DescriptorHelper.doBuildStatusPublishModeItems();
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillOriginBuildStatusPublishModeItems() {
            return DescriptorHelper.doBuildStatusPublishModeItems();
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillForkBuildStatusPublishModeItems() {
            return DescriptorHelper.doBuildStatusPublishModeItems();
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillTagBuildStatusPublishModeItems() {
            return DescriptorHelper.doBuildStatusPublishModeItems();
        }
    }
}
