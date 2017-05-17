package argelbargel.jenkins.plugins.gitlab_branch_source.actions;


import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabApi;
import com.dabsquared.gitlabjenkins.gitlab.api.model.BuildState;
import hudson.init.Terminator;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;


/**
 * Publishes Build-Status to GitLab using separate threads so it does not block while sending messages
 * TODO: Multi-Threading is easy to get wrong and wreak havoc. Check if there is no better way to do this built into Jenkins
 */
public final class GitLabSCMBuildStatusPublisher {
    private static final Logger LOGGER = Logger.getLogger(GitLabSCMBuildStatusPublisher.class.getName());

    private static final Object instanceLock = new Object();
    private static volatile GitLabSCMBuildStatusPublisher instance;

    static GitLabSCMBuildStatusPublisher instance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new GitLabSCMBuildStatusPublisher();
                }
            }
        }

        return instance;
    }

    @Terminator
    public static void terminate() throws InterruptedException {
        if (instance != null) {
            instance.shutdown();
        }
    }

    private final ExecutorService executorService;

    private GitLabSCMBuildStatusPublisher() {
        executorService = Executors.newSingleThreadExecutor();
    }

    void publish(Run<?, ?> run, int projectId, String hash, BuildState state, String ref, String context, String description) {
        executorService.execute(new Message(run, projectId, hash, state, ref, context, description));
    }

    private void shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);
    }


    private static final class Message implements Runnable {
        private final Run<?, ?> run;
        private final int projectId;
        private final String hash;
        private final BuildState state;
        private final String ref;
        private final String context;
        private final String description;


        private Message(Run<?, ?> run, int projectId, String hash, BuildState state, String ref, String context, String description) {
            this.run = run;
            this.projectId = projectId;
            this.ref = ref;
            this.hash = hash;
            this.state = state;
            this.context = context;
            this.description = description;
        }

        @Override
        public void run() {
            GitLabApi client = GitLabConnectionProperty.getClient(run);
            if (client == null) {
                LOGGER.log(WARNING, "cannot publish build-status pending as no gitlab-connection is configured!");
            } else {
                try {
                    client.changeBuildStatus(projectId, hash, state, ref, context, Jenkins.getInstance().getRootUrl() + run.getUrl(), description);
                } catch (Exception e) {
                    LOGGER.log(SEVERE, "failed to set build-status of '" + context + "' for project " + projectId + " to " + state, e);
                }
            }
        }
    }
}
