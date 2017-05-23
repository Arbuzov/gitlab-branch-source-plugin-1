package argelbargel.jenkins.plugins.gitlab_branch_source;


import argelbargel.jenkins.plugins.gitlab_branch_source.api.GitLabProject;
import jenkins.scm.api.SCMSourceObserver;

import java.io.IOException;
import java.io.PrintStream;

import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabHelper.gitLabAPI;


class SourceVisitor {
    private final SCMSourceObserver observer;
    private final GitLabSCMNavigator navigator;
    private final PrintStream log;

    SourceVisitor(GitLabSCMNavigator navigator, SCMSourceObserver observer) {
        this.navigator = navigator;
        this.observer = observer;
        this.log = observer.getListener().getLogger();
    }

    void visitSources() throws IOException, InterruptedException {
        visitSources(navigator.getSourceSettings().getConnectionName(), ProjectQuery.create(navigator));
    }

    private void visitSources(String connectionName, ProjectQuery projectQuery) throws InterruptedException, IOException {
        log(Messages.GitLabSCMNavigator_visitSources(connectionName));

        try {
            for (GitLabProject project : projectQuery.execute(connectionName)) {
                checkInterrupt();
                visitProject(project);
            }
        } catch (Exception e) {
            log(Messages.GitLabSCMNavigator_visitSources_error(connectionName, e));
            throw e;
        }
    }

    void visitProject(String sourceName) throws IOException, InterruptedException {
        try {
            GitLabProject project = gitLabAPI(navigator.getSourceSettings().getConnectionName()).getProject(sourceName);
            if (project != null) {
                visitProject(project);
            }
        } catch (Exception e) {
            log(Messages.GitLabSCMNavigator_visitSource_error(sourceName, e));
            throw e;
        }
    }

    private void visitProject(GitLabProject project) throws IOException, InterruptedException {
        if (!observer.isObserving()) {
            return;
        }

        checkInterrupt();

        log(Messages.GitLabSCMNavigator_visitProject(project.getPathWithNamespace()));
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(project.getPathWithNamespace());
        GitLabSCMSource source = new GitLabSCMSource(navigator.getSourceSettings(), project);
        projectObserver.addSource(source);
        projectObserver.complete();
        source.afterSave();
    }

    private void log(String message, Object... data) {
        log.format(message + "\n", data);
    }

    private void checkInterrupt() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }
}
