package com.kiwigrid.k8s.helm;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.internal.os.OperatingSystem;

public class HelmPluginExtension implements HelmSpec {

	private static final HelmRepository LOCAL_REPO = new HelmRepository("local");
	private static final HelmRepository STABLE_REPO = new HelmRepository("stable");
	public static final String AMD_64 = "amd64";
	public static final String I_386 = "386";
	public static final String II_386 = "i386";
	public static final Set<String> ARCHITECTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(I_386,
			AMD_64)));

	private File helmExecutableDirectory; // "${project.build.directory}/helm/bin"

	private File outputDirectory; // "${project.build.directory}/helm/repo"

	private String version = "2.17.0";

	private String architecture;

	private final String operatingSystem;

	private String helmDownloadUrl;

	private File helmHomeDirectory;

	private NamedDomainObjectContainer<HelmRepository> repositories;

	private HelmRepository deployTo;

	private MapProperty<String, Object> expansions;

	public HelmPluginExtension(Project project) {
		repositories = project.container(HelmRepository.class, HelmRepository::new);
		expansions = project.getObjects().mapProperty(String.class, Object.class);
		expansions.convention(Collections.emptyMap());
		helmExecutableDirectory = new File(project.getBuildDir(), "helm/bin");
		helmHomeDirectory = new File(project.getBuildDir(), "helm/home");
		outputDirectory = new File(project.getBuildDir(), "helm/repo");
		// detect OS
		OperatingSystem currentOperatingSystem = OperatingSystem.current();
		if (currentOperatingSystem.isWindows()) {
			this.operatingSystem = "windows";
		} else if (currentOperatingSystem.isMacOsX()) {
			this.operatingSystem = "darwin";
		} else if (currentOperatingSystem.isLinux()) {
			this.operatingSystem = "linux";
		} else {
			this.operatingSystem = null;
			project.getLogger()
					.warn("Unable to detect OS for helm download (found {}), please specify 'helm.operatingSystem' or 'helm.helmDownloadURL'",
							currentOperatingSystem.getName());
		}
		// detect arch: either 386 or amd64
		String arch = System.getProperty("os.arch");
		switch (arch) {
		case "x86":
		case II_386:
			arch = I_386;
			break;
		case "x86_64":
			arch = AMD_64;
			break;
		}
		if (ARCHITECTURES.contains(arch)) {
			architecture = arch;
		} else {
			project.getLogger()
					.warn("Unable to detect architecture (found: '{}'), please specify helm.architecture or 'helm.helmDownloadURL'",
							arch);
		}
	}

	@Override
	public String getHelmDownloadUrl() {
		if (helmDownloadUrl != null) {
			return helmDownloadUrl;
		} else {
			String pathVersion = HelmPlugin.isCanaryVersion(version) ? version : "v" + version;
			return "https://get.helm.sh/helm-"
					+ pathVersion
					+ "-"
					+ operatingSystem
					+ "-"
					+ architecture
					+ ".tar.gz";
		}
	}

	HelmRepository stableRepo() {
		return STABLE_REPO;
	}

	HelmRepository localRepo() {
		return LOCAL_REPO;
	}

	public void deployTo(HelmRepository deployTo) {
		this.deployTo = deployTo;
	}

	// some feature testers

	@Override
	public File getHelmExecutableDirectory() {
		return helmExecutableDirectory;
	}

	@Override
	public File getOutputDirectory() {
		return outputDirectory;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String getArchitecture() {
		return architecture;
	}

	public String getOperatingSystem() {
		return operatingSystem;
	}

	@Override
	public File getHelmHomeDirectory() {
		return helmHomeDirectory;
	}

	public NamedDomainObjectContainer<HelmRepository> getRepositories() {
		return repositories;
	}

	public HelmRepository getDeployTo() {
		return deployTo;
	}

	public MapProperty<String, Object> getExpansions() {
		return expansions;
	}

	public HelmPluginExtension setExpansions(Map<String, Object> expansions) {
		this.expansions.set(expansions);
		return this;
	}

	@Override
	public void setHelmExecutableDirectory(File helmExecutableDirectory) {
		this.helmExecutableDirectory = helmExecutableDirectory;
	}

	@Override
	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	@Override
	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public void setArchitecture(String architecture) {
		this.architecture = architecture;
	}

	@Override
	public void setHelmDownloadUrl(String helmDownloadUrl) {
		this.helmDownloadUrl = helmDownloadUrl;
	}

	@Override
	public void setHelmHomeDirectory(File helmHomeDirectory) {
		this.helmHomeDirectory = helmHomeDirectory;
	}

}