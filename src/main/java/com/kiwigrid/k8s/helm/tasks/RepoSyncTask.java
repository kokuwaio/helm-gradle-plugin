package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.kiwigrid.k8s.helm.HelmPlugin;
import com.kiwigrid.k8s.helm.HelmPluginExtension;
import com.kiwigrid.k8s.helm.HelmRepository;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecException;

import static com.kiwigrid.k8s.helm.HelmPlugin.helmExec;

public class RepoSyncTask extends AbstractHelmTask {

	private static final Pattern REPO_PATTERN = Pattern.compile("(\\w+)\\s+(.+)");

	private NamedDomainObjectContainer<HelmRepository> repositories;

	private final Logger logger;

	private final File repositoryYamlOutput;

	public RepoSyncTask() {
		logger = getLogger();
		repositoryYamlOutput = new File(getProject().getBuildDir(), "helm/out/repocopy.yaml");
		getOutputs().upToDateWhen(element -> yamlFilesEqual(repositoryYamlOutput, getRepositoryYamlFromHelmHome()));
	}

	@TaskAction
	public void syncRepos() throws IOException {
		Map<String, HelmRepository> knownRepos = getKnownHelmRepositories();
		Set<String> knownRepositoryNames = new HashSet<>(knownRepos.keySet());
		Set<String> configuredRepositoryNames = repositories.getNames();
		Set<String> reposToRemove = new HashSet<>(knownRepositoryNames);
		reposToRemove.removeAll(configuredRepositoryNames);
		// never remove 'stable' and 'local'
		reposToRemove.remove("stable");
		reposToRemove.remove("local");

		Set<String> reposToAdd = new HashSet<>(configuredRepositoryNames);
		reposToAdd.removeAll(knownRepositoryNames);

		Set<String> reposToSyncAuthentication = repositories
				.stream()
				.filter(HelmRepository::isAuthenticated)
				.map(HelmRepository::getName)
				.collect(Collectors.toSet());
		reposToSyncAuthentication.removeAll(reposToAdd);

		removeRepos(reposToRemove);
		addRepos(reposToAdd);
		syncAuth(reposToSyncAuthentication);

		// update output
		getProject().copy(copySpec -> {
			copySpec.from(getRepositoryYamlFromHelmHome());
			copySpec.into(repositoryYamlOutput.getParent());
			copySpec.rename(s -> repositoryYamlOutput.getName());
		});
	}

	private void syncAuth(Set<String> reposToSyncAuthentication) throws IOException {
		// deser reposities.yaml
		File repoFile = getRepositoryYamlFromHelmHome();
		Map repoYaml = HelmPlugin.YAML.loadAs(new FileInputStream(repoFile), Map.class);
		List<Object> repos = (List<Object>) repoYaml.get("repositories");
		Map<String, Object> repoMap = repos.stream()
				.collect(Collectors.toMap(r -> (String) ((Map) r).get("name"), Function.identity()));

		Optional<Boolean> changed = reposToSyncAuthentication
				.stream()
				.map(repoName -> repositories.getAt(repoName))
				.map(helmRepository -> {
					Map repo = (Map) repoMap.get(helmRepository.getName());
					String user = (String) repo.get("username");
					String pwd = (String) repo.get("password");
					if (!Objects.equals(pwd, helmRepository.getPassword()) || !Objects.equals(user,
							helmRepository.getUser()))
					{
						repo.put("username", helmRepository.getUser());
						repo.put("password", helmRepository.getPassword());
						return true;
					} else {
						return false;
					}
				})
				.reduce((aBoolean, aBoolean2) -> aBoolean || aBoolean2);
		if (changed.isPresent() && changed.get()) {
			// persist
			HelmPlugin.YAML.dump(repoYaml, new FileWriter(repoFile));
		}
	}

	private void addRepos(Set<String> reposToAdd) {
		reposToAdd.forEach(repoName -> addRepo(repositories.getAt(repoName)));
	}

	private void removeRepos(Set<String> reposToRemove) {
		reposToRemove.forEach(this::removeRepo);
	}

	private Map<String, HelmRepository> getKnownHelmRepositories() {
		HelmPlugin.HelmExecResult execResult = helmExec(getProject(), this, "repo", "list");
		if (execResult.execResult.getExitValue() == 1
				&& execResult.output.length >= 1
				&& String.join(" ", execResult.output).contains("no repositories to show")) {
			return Collections.emptyMap();
		}
		if (execResult.execResult.getExitValue() != 0) {
			throw new ExecException("Unexpected failed execution:\n" + String.join("\n", execResult.output));
		}
		return Arrays.stream(execResult.output)
				// first line is table header
				.skip(1L)
				.map(REPO_PATTERN::matcher)
				.filter(Matcher::matches)
				.map(matcher -> new HelmRepository(matcher.group(1), matcher.group(2)))
				.collect(Collectors.toMap(HelmRepository::getName, Function.identity()));
	}

	private void removeRepo(String repoName) {
		helmExec(getProject(), this, "repo", "remove", repoName);
	}

	private void addRepo(HelmRepository repo) {
		if (repo.isAuthenticated() && !HelmPlugin.authenticatedReposSupported(getVersion())) {
			getProject().getLogger().warn(
					"Cannot add authenticated repository '{}', authentication supported with '{}', your version: '{}'.",
					repo.getName(),
					HelmPlugin.REPO_AUTHENTICATION_VERSION,
					getVersion());
			return;
		}
		if (repo.isAuthenticated()) {
			helmExec(getProject(), this,
					"repo",
					"add",
					repo.getName(),
					repo.getUrl(),
					"--username=" + repo.getUser(),
					"--password=" + repo.getPassword()
			);
		} else {
			helmExec(getProject(), this,
					"repo",
					"add",
					repo.getName(),
					repo.getUrl()
			);
		}
	}

	private boolean yamlFilesEqual(File left, File right) {
		return Objects.equals(
				HelmPlugin.loadYamlSilently(left, true),
				HelmPlugin.loadYamlSilently(right, true)
		);
	}

	private File getRepositoryYamlFromHelmHome() {
		File helmHomeDirectory = getHelmHomeDirectory();
		File repositoriesFile;
		if(OperatingSystem.current().isMacOsX()) {
			if(HelmPlugin.isVersion3OrNewer(getVersion())) {
				repositoriesFile = new File(System.getenv("HOME") + "/Library/Preferences/helm/repositories.yaml");
			} else {
				repositoriesFile = new File(System.getenv("HOME") + "/.helm/repository/repositories.yaml");
			}

		} else if (HelmPlugin.isVersion3OrNewer(getVersion())) {
			repositoriesFile = new File(helmHomeDirectory, "config/helm/repositories.yaml");
		} else {
			repositoriesFile = new File(helmHomeDirectory, "repository/repositories.yaml");
		}

		logger.debug("will load repositories.yaml from : " + repositoriesFile.getAbsolutePath());

		if(!repositoriesFile.exists()) {
			logger.error("can not find repositories.yaml file. Need to set 'helmHomeDirectory' ?");
			logger.error("if you are running this on Mac, check : /Users/<yourUser>/Library/Preferences/helm/repositories.yaml");
		}
		return repositoriesFile;
	}

	@OutputFile
	public File getRepositoryYamlOutput() {
		return repositoryYamlOutput;
	}

	@Input
	public NamedDomainObjectContainer<HelmRepository> getRepositories() {
		return repositories;
	}

	public RepoSyncTask setRepositories(NamedDomainObjectContainer<HelmRepository> repositories) {
		this.repositories = repositories;
		return this;
	}
}