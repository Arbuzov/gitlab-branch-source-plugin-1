package argelbargel.jenkins.plugins.gitlab_branch_source.actions;


import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabApi;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;


public final class GitLabSCMAcceptMergeRequestAction extends InvisibleAction implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(GitLabSCMAcceptMergeRequestAction.class.getName());

    private final int projectId;
    private final int mergeRequestId;
    private final int mergeRequestScopedId;
    private final String commitMessage;
    private final boolean removeSourceBranch;

    public GitLabSCMAcceptMergeRequestAction(int projectId, int mergeRequestId, int mergeRequestScopedId, String commitMessage, boolean removeSourceBranch) {
        this.projectId = projectId;
        this.mergeRequestId = mergeRequestId;
        this.mergeRequestScopedId = mergeRequestScopedId;
        this.commitMessage = commitMessage;
        this.removeSourceBranch = removeSourceBranch;
    }

    public void acceptMergeRequest(Run<?, ?> build, TaskListener listener) {
        GitLabApi client = GitLabConnectionProperty.getClient(build);
        if (client == null) {
            listener.getLogger().format("cannot publish build-status pending as no gitlab-connection is configured!");
        } else {
            try {
                client.acceptMergeRequest(projectId, mergeRequestId, MessageFormat.format(commitMessage, mergeRequestScopedId, build.getFullDisplayName()), removeSourceBranch);
            } catch (Exception e) {
                listener.getLogger().format("failed to accept merge-request: " + e.getMessage());
                LOGGER.log(SEVERE, "failed to accept merge-request", e);
            }
        }
    }
}
