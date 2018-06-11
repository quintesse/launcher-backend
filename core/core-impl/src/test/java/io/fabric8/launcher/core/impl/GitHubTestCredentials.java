package io.fabric8.launcher.core.impl;


import io.fabric8.launcher.base.identity.Identity;
import io.fabric8.launcher.base.identity.TokenIdentity;
import io.fabric8.launcher.service.git.github.api.GitHubEnvVarSysPropNames;

/**
 * Used to obtain the GitHub credentials from the environment
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
public class GitHubTestCredentials {

    private GitHubTestCredentials() {
        // No instances
    }

    /**
     * @return the GitHub username
     */
    public static String getUsername() {
        return GitHubEnvVarSysPropNames.LAUNCHER_MISSIONCONTROL_GITHUB_USERNAME.valueRequired();
    }

    /**
     * @return the GitHub token
     */
    public static Identity getToken() {
        return TokenIdentity.of(GitHubEnvVarSysPropNames.LAUNCHER_MISSIONCONTROL_GITHUB_TOKEN.valueRequired());
    }
}
