package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.kiwigrid.k8s.helm.HelmPlugin;
import javax.inject.Inject;
import org.apache.tools.ant.filters.ReplaceTokens;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class HelmBuildTask extends AbstractHelmTask {

	private Property<File> source;

	private final MapProperty<String, Object> expansions;
	private final Logger logger;

	@Inject
	public HelmBuildTask(ObjectFactory objectFactory) {
		source = objectFactory.property(File.class);
		source.convention(getProject().provider(() -> getProject().file("src/main/helm")));
		expansions = objectFactory.mapProperty(String.class, Object.class);
		setGroup(BasePlugin.BUILD_GROUP);
		setDescription("Builds a Helm Chart");
		logger = getLogger();
	}

	@TaskAction
	public void helmBuild() throws FileNotFoundException {
		File temporaryDir = renderExpansions();
		logger.lifecycle("Rendered helm chart with expansions into {}", temporaryDir);
		String chartName = readChartName(temporaryDir);
		logger.lifecycle("Read chart name to be {}", chartName);
		File chartFolder = new File(getOutputDirectory(), chartName);
		cleanCopyChart(temporaryDir, chartFolder);
		logger.lifecycle("Created chart in {}, running dependency build for {}", chartFolder);
		helmDependencyBuild(chartFolder);
		logger.lifecycle("Running helm package for {}", chartFolder);
		helmPackage(chartFolder);
	}

	// for this task output dir is an output
	@OutputDirectory
	@Override
	public File getOutputDirectory() {
		return super.getOutputDirectory();
	}

	// for this task helm home is an input
	@InputDirectory
	@Override
	public File getHelmHomeDirectory() {
		File helmHomeDirectory = super.getHelmHomeDirectory();
		// an input directory needs to exist otherwise gradle fails with an error.
		if (!helmHomeDirectory.exists()) {
			helmHomeDirectory.mkdirs();
		}
		return helmHomeDirectory;
	}

	private void helmPackage(File chartFolder) {
		// package
		boolean is30OrNewer = HelmPlugin.isVersion3OrNewer(getVersion());

		Object [] arguments;
		if(is30OrNewer) {
			arguments = new Object[]{
					"package",
					"--destination",
					getOutputDirectory().getAbsolutePath(),
					chartFolder.getAbsolutePath()
			};
		} else {
			arguments = new Object[]{
					"package",
					"--save=false",
					"--destination",
					getOutputDirectory().getAbsolutePath(),
					chartFolder.getAbsolutePath()
			};
		}

		HelmPlugin.helmExecSuccess(getProject(), this, arguments);
		getLogger().lifecycle("packaged chart to : " + getOutputDirectory().getAbsolutePath());
	}

	private void helmDependencyBuild(File chartFolder) {
		// build deps
		Project project = getProject();
		HelmPlugin.helmExec(
				project,
				this,
				"dependency",
				"build",
				chartFolder.getAbsolutePath());
	}

	private File renderExpansions() {
		File destination = getTemporaryDir();
		// expand into tmpDir
		Project project = getProject();
		project.copy(copySpec -> {
			copySpec.from(source);
			copySpec.into(destination);

			Map<String, String> tokens = flattenMap(expansions.get());
			tokens.forEach((k, v) -> project.getLogger().info("Discovered token: {} = {}", k, v));
			Map<String, Object> props = new LinkedHashMap<>();
			props.put("tokens", tokens);
			props.put("beginToken", "${");
			props.put("endToken", "}");
			copySpec.filter(props, ReplaceTokens.class);
		});
		return destination;
	}

	private void cleanCopyChart(File from, File to) {
		Project project = getProject();
		// start anew
		project.delete(to);
		// put in final dir
		project.copy(copySpec -> {
			copySpec.from(from);
			copySpec.into(to);
		});
	}

	@InputDirectory
	public Property<File> getSource() {
		return source;
	}

	public HelmBuildTask setSource(File source) {
		this.source.set(source);
		return this;
	}

	public HelmBuildTask setSource(Provider<File> source) {
		this.source.set(source);
		return this;
	}

	@Input
	public MapProperty<String, Object> getExpansions() {
		return expansions;
	}

	public HelmBuildTask setExpansions(Provider<? extends Map<String, Object>> expansions) {
		this.expansions.set(expansions);
		return this;
	}

	public HelmBuildTask setExpansions(Map expansions) {
		this.expansions.set(expansions);
		return this;
	}

	private static Map<String, String> flattenMap(Map<?, ?> map) {
		return map
				.entrySet()
				.stream()
				.flatMap(entry -> {
					if (entry.getValue() instanceof Map) {
						return flattenMap((Map<?, ?>) entry.getValue())
								.entrySet()
								.stream()
								.map(stringStringEntry -> new AbstractMap.SimpleEntry<>(entry.getKey().toString()
										+ "."
										+ stringStringEntry.getKey(), stringStringEntry.getValue()));
					} else {
						return Stream.of(new AbstractMap.SimpleEntry<>(entry.getKey().toString(),
								entry.getValue().toString()));
					}
				})
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private static String readChartName(File chartDir) throws FileNotFoundException {
		Map contents = HelmPlugin.YAML.load(new FileInputStream(new File(chartDir, "Chart.yaml")));
		return (String) contents.get("name");
	}

}
