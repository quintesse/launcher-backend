package io.fabric8.launcher.core.impl;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.fabric8.launcher.booster.catalog.rhoar.RhoarBooster;
import io.fabric8.launcher.core.api.Boom;
import io.fabric8.launcher.core.api.MissionControl;
import io.fabric8.launcher.core.api.projectiles.CreateProjectile;
import io.fabric8.launcher.core.api.projectiles.ImmutableLauncherCreateProjectile;
import io.fabric8.launcher.core.api.projectiles.LauncherCreateProjectile;
import io.fabric8.launcher.core.api.projectiles.context.CreateProjectileContext;
import io.fabric8.launcher.core.impl.catalog.RhoarBoosterCatalogFactory;
import io.fabric8.launcher.service.git.api.GitRepository;
import io.fabric8.launcher.service.git.api.GitService;
import io.fabric8.launcher.service.git.api.GitServiceFactory;
import io.fabric8.launcher.service.git.api.NoSuchRepositoryException;
import io.fabric8.launcher.service.git.spi.GitProvider;
import io.fabric8.launcher.service.git.spi.GitServiceSpi;
import io.fabric8.launcher.service.openshift.api.OpenShiftProject;
import io.fabric8.launcher.service.openshift.api.OpenShiftResource;
import io.fabric8.launcher.service.openshift.api.OpenShiftService;
import io.fabric8.launcher.service.openshift.api.OpenShiftServiceFactory;
import io.fabric8.launcher.service.openshift.spi.OpenShiftServiceSpi;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.fabric8.launcher.service.git.spi.GitProvider.GitProviderType.GITHUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test cases for the {@link MissionControl}
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 * @author <a href="mailto:xcoulon@redhat.com">Xavier Coulon</a>
 */
@RunWith(Arquillian.class)
public class MissionControlIT {

    private static final Logger log = Logger.getLogger(MissionControlIT.class.getName());

    private static final String PREFIX_NAME_PROJECT = "test-project-";

    private final Collection<String> openshiftProjectsToDelete = new ArrayList<>();

    private final Collection<String> githubReposToDelete = new ArrayList<>();

    @Inject
    private OpenShiftServiceFactory openShiftServiceFactory;

    @Inject
    @GitProvider(GITHUB)
    private GitServiceFactory gitServiceFactory;

    @Inject
    private MissionControl<CreateProjectileContext, CreateProjectile> missionControl;

    @Inject
    private RhoarBoosterCatalogFactory catalogFactory;

    /**
     * @return a war file containing all the required classes and dependencies
     * to test the {@link MissionControl}
     */
    @Deployment
    public static WebArchive createDeployment() {
        return Deployments.createDeployment().addClass(MockServiceProducers.class);
    }

    @Before
    @After
    public void cleanupGitHubProjects() {
        final GitService gitHubService = gitServiceFactory.create(GitHubTestCredentials.getToken());
        githubReposToDelete.forEach(repoName -> {
            final String fullRepoName = GitHubTestCredentials.getUsername() + '/' + repoName;
            try {
                ((GitServiceSpi) gitHubService).deleteRepository(fullRepoName);
                log.info("Deleted GitHub repository: " + fullRepoName);
            } catch (final NoSuchRepositoryException nsre) {
                log.severe("Could not remove GitHub repo " + fullRepoName + ": " + nsre.getMessage());
            }

        });
        githubReposToDelete.clear();
    }

    @Before
    public void loadCatalogIndex() throws Exception {
        catalogFactory.waitForIndex();
    }

    @Before
    @After
    public void cleanupOpenShiftProjects() {
        OpenShiftService openShiftService = openShiftServiceFactory.create();
        openshiftProjectsToDelete.forEach(projectName -> {
            final boolean deleted = ((OpenShiftServiceSpi) openShiftService).deleteProject(projectName);
            if (deleted) {
                log.info("Deleted OpenShift project: " + projectName);
            }
        });
        openshiftProjectsToDelete.clear();
    }

    @Test
    public void launchCreateProjectile() throws Exception {
        // Define the projectile with a custom, unique OpenShift project name.
        final String expectedName = getUniqueProjectName();

        File tempDir = Files.createTempDirectory("mc").toFile();

        // Use any booster
        RhoarBooster booster = catalogFactory.getBoosterCatalog().getBoosters().iterator().next();

        final LauncherCreateProjectile projectile = ImmutableLauncherCreateProjectile.builder()
                .booster(booster)
                .gitRepositoryName(expectedName)
                .openShiftProjectName(expectedName)
                .projectLocation(tempDir.toPath())
                .build();

        // Mark GitHub repo for deletion
        githubReposToDelete.add(expectedName);

        // Fling
        final Boom boom = missionControl.launch(projectile);

        // Assertions
        assertions(expectedName, boom);
    }

    private void assertions(String expectedName, Boom boom) {
        /*
           Can't really assert on any of the properties of the
           new repo because they could change in GitHub and
           break our tests
         */
        final GitRepository createdRepo = boom.getCreatedRepository();
        assertNotNull("repo can not be null", createdRepo);
        final OpenShiftProject createdProject = boom.getCreatedProject();
        assertNotNull("project can not be null", createdProject);
        final String foundName = createdProject.getName();
        log.info("Created OpenShift project: " + foundName);
        openshiftProjectsToDelete.add(foundName);
        assertEquals(expectedName, foundName);
        // checking that the Build Config and the ImageStream were created.
        List<OpenShiftResource> resources = createdProject.getResources();

        assertThat(resources).isNotNull();
        assertThat(resources.stream().map(OpenShiftResource::getKind)).contains("ImageStream", "BuildConfig");
    }

    private String getUniqueProjectName() {
        return PREFIX_NAME_PROJECT + System.currentTimeMillis();
    }
}
