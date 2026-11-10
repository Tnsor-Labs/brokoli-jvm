package dev.brokoli.maven;

import dev.brokoli.bundle.BundleException;
import dev.brokoli.bundle.TaskBundles;
import dev.brokoli.maven.AttachedBundles.Written;
import javax.inject.Inject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Packages each {@code @Task} method of the project into a task bundle a Brokoli server can run, and attaches it
 * to the build as {@code <finalName>-<method>.tar.gz}, with the method name as its classifier, so {@code install}
 * and {@code deploy} publish it next to the jar.
 *
 * <p>Every bundle holds the project's compiled classes and resources, except {@code META-INF/brokoli/}, and each
 * jar of a {@code compile} or {@code runtime} dependency under {@code lib/}, which includes {@code brokoli-task}.
 * A {@code provided} dependency is left out. The manifest's {@code name} is the task's display name, and its
 * entrypoint is the entry method the task processor generated. The goal fails when the project has no task file,
 * when two dependencies have the same jar file name, or when two tasks have the same method name.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class BundleMojo extends AbstractMojo {

    private static final String BUILDER = "brokoli-jvm/";
    private static final Set<String> PACKAGED_SCOPES = Set.of(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor plugin;

    @Parameter(defaultValue = "${project.build.finalName}", readonly = true, required = true)
    private String finalName;

    /**
     * The compiler's output directory, holding the classes, the resources and the task files the task processor
     * wrote under {@code META-INF/brokoli/tasks/}.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    /**
     * The directory the bundles are written to.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    /**
     * Skips the goal, so no bundle is written or attached.
     */
    @Parameter(property = "brokoli.bundle.skip", defaultValue = "false")
    private boolean skip;

    private final MavenProjectHelper projectHelper;

    /**
     * Creates the goal. Maven creates it and injects the helper that attaches the bundles.
     *
     * @param projectHelper attaches each bundle to the project
     */
    @Inject
    public BundleMojo(MavenProjectHelper projectHelper) {
        this.projectHelper = projectHelper;
    }

    /**
     * Writes and attaches one bundle per task.
     *
     * @throws MojoFailureException if the project's tasks cannot be packaged, with a message naming why
     * @throws MojoExecutionException if a bundle cannot be written
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping task bundles: brokoli.bundle.skip is set");
            return;
        }
        try {
            List<Written> bundles = AttachedBundles.write(
                    TaskBundles.of(classesDirectory.toPath(), packagedJars(), BUILDER + plugin.getVersion()),
                    outputDirectory.toPath(), finalName);
            for (Written written : bundles) {
                projectHelper.attachArtifact(project, AttachedBundles.TYPE, written.classifier(),
                        written.file().toFile());
                getLog().info("Task bundle " + written.bundle().name() + " (" + written.bundle().entrypoint() + "): "
                        + written.file() + " " + written.digest());
            }
        } catch (BundleException refused) {
            throw new MojoFailureException(refused.getMessage(), refused);
        } catch (IOException unwritable) {
            throw new MojoExecutionException("could not write a task bundle to " + outputDirectory + ": "
                    + unwritable.getMessage(), unwritable);
        }
    }

    private List<Path> packagedJars() throws MojoFailureException {
        List<Path> jars = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            if (!PACKAGED_SCOPES.contains(artifact.getScope()) || !artifact.getArtifactHandler().isAddedToClasspath()) {
                continue;
            }
            if (artifact.getFile() == null) {
                throw new MojoFailureException("dependency " + artifact.getId() + " is not resolved");
            }
            jars.add(artifact.getFile().toPath());
        }
        return jars;
    }
}
