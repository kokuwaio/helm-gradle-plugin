package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.jayway.jsonpath.JsonPath;
import com.kiwigrid.k8s.helm.HelmPlugin;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.process.internal.ExecException;

public class HelmTestTask extends AbstractHelmTask {

	public static final String VALUES_OPTION = "--values";
	private final DirectoryProperty tests;

	private final ConfigurableFileCollection charts;

	private File testOutputs;

	private String testPattern = ".*";

	@Inject
	public HelmTestTask(ObjectFactory objectFactory) {
		setDescription("Tests Helm Chart via \"helm lint\" and assert definitions");
		setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
		onlyIf(element -> getTestSourceDir().getAsFile().exists());
		testOutputs = new File(getProject().getBuildDir(), "helm/test");
		tests = objectFactory.directoryProperty();
		tests.convention(getProject().provider(this::getTestSourceDir));
		charts = objectFactory.fileCollection();
	}

	private Directory getTestSourceDir() {
		return getProject().getLayout().getProjectDirectory().dir("src/test/helm");
	}

	@TaskAction
	public void runTests() {
		File[] chartFolders = getOutputDirectory().listFiles(File::isDirectory);
		if (chartFolders == null || chartFolders.length == 0) {
			return;
		}
		boolean lintWithValuesSupported = HelmPlugin.lintWithValuesSupported(getVersion());
		if (!lintWithValuesSupported) {
			getLogger().warn(
					"Linting with different value files not supported prior to helm v{} (your version: v{})",
					HelmPlugin.LINT_WITH_VALUES_VERSION,
					this.getVersion());
		}
		boolean templateWithOutputSupported = HelmPlugin.templateWithOutputSupported(getVersion());
		if (!templateWithOutputSupported) {
			getLogger().warn(
					"Rendering chart with test values not supported prior to helm v{} (your version: v{})",
					HelmPlugin.TEMPLATE_WITH_OUTPUT_VERSION,
					this.getVersion());
		}
		String commonTestPathPrefix = tests.get().getAsFile().getAbsolutePath() + File.separator;
		List<HelmTestCase> testCases = tests
				.getAsFileTree()
				.filter(element -> element.getName().endsWith(".yml") || element.getName().endsWith(".yaml"))
				.getFiles()
				.stream()
				.map(file -> fromYamlFile(commonTestPathPrefix, file))
				.collect(Collectors.toList());
		List<AssertionError> failures = new ArrayList<>();
		Arrays.stream(chartFolders)
				.forEach(chartFolder -> {
					runTestsForChart(lintWithValuesSupported,
							templateWithOutputSupported,
							chartFolder,
							testCases,
							failures);
				});
		if (!failures.isEmpty()) {
			Logger logger = getLogger();
			logger.error("There have been test failures.");
			failures.forEach(assertionError -> logger.info("Test Failure", assertionError));
			throw new DefaultMultiCauseException("Failing tests.", failures);
		}
	}

	private void runTestsForChart(boolean lintWithValuesSupported, boolean templateWithOutputSupported, File chartFolder, List<HelmTestCase> testCases, List<AssertionError> failures) {
		getLogger().info("Linting {} with default values...", chartFolder.getName());
		HelmPlugin.helmExecSuccess(getProject(), this, "lint", chartFolder.getAbsolutePath());
		getProject().delete(testOutputs);
		Pattern pattern = Pattern.compile(testPattern);
		testCases
				.stream()
				.filter(helmTestCase -> {
					boolean match = pattern.matcher(helmTestCase.name).matches();
					if (!match) {
						getLogger().info("Skipping '{}', not matching '{}'", helmTestCase.name, testPattern);
					}
					return match;
				})
				.forEach(helmTestCase -> runSingleTestCase(
						helmTestCase,
						lintWithValuesSupported,
						templateWithOutputSupported,
						chartFolder,
						failures));
	}

	private void runSingleTestCase(HelmTestCase helmTestCase, boolean lintWithValuesSupported, boolean templateWithOutputSupported, File chartFolder, List<AssertionError> failures) {
		getLogger().info("Running test case {}: {}", helmTestCase.name, helmTestCase.title);
		if (helmTestCase.succeed) {
			if (lintWithValuesSupported) {
				helmLint(chartFolder, helmTestCase, failures);
			}
			if (templateWithOutputSupported) {
				getLogger().debug("Templating and asserting ...");
				File testCaseOutputFolder = new File(testOutputs, helmTestCase.name);
				testCaseOutputFolder.mkdirs();
				if (helmTemplate(chartFolder, helmTestCase, testCaseOutputFolder, failures)) {
					return;
				}
				runAssertions(helmTestCase, testCaseOutputFolder, chartFolder.getName(), failures);
			}
		} else {
			try {
				HelmPlugin.helmExecFail(
						getProject(),
						this,
						"template",
						VALUES_OPTION,
						helmTestCase.valueFile.getAbsolutePath(),
						chartFolder.getAbsolutePath());
				// not throwing an exception is unexpected, the test shall not succeed
			} catch (ExecException e) {
				failures.add(new AssertionError("Helm template for test '"
						+ helmTestCase.name
						+ "' succeeded when it shall not", e));
			}
		}
	}

	private void runAssertions(HelmTestCase helmTestCase, File testCaseOutputFolder, String chartName, List<AssertionError> failures) {
		getLogger().info("running assertions...");
		helmTestCase.assertions.forEach(helmTestAssertion -> {
			File input = new File(testCaseOutputFolder, chartName + File.separator + helmTestAssertion.file);
			if (!input.exists() || !input.isFile()) {
				failures.add(new AssertionError("Test '"
						+ helmTestCase.title
						+ " requires file  "
						+ input.getAbsolutePath()
						+ " which does not exist or is no file."));
				return;
			}
			try {
				helmTestAssertion.statement.execute(input);
			} catch (AssertionError e) {
				failures.add(e);
			}
		});
	}

	private boolean helmTemplate(File chartFolder, HelmTestCase helmTestCase, File testCaseOutputFolder, List<AssertionError> failures) {
		try {
			HelmPlugin.helmExecSuccess(
					getProject(),
					this,
					"template",
					"--values",
					helmTestCase.valueFile.getAbsolutePath(),
					"--output-dir",
					testCaseOutputFolder.getAbsolutePath(),
					chartFolder.getAbsolutePath());
		} catch (ExecException e) {
			failures.add(new AssertionError("Templating failed for " + helmTestCase.name, e));
			return true;
		}
		return false;
	}

	private void helmLint(File chartFolder, HelmTestCase helmTestCase, List<AssertionError> failures) {
		getLogger().debug("Linting ...");
		try {
			String[] output = HelmPlugin.helmExecSuccess(
					getProject(),
					this,
					"lint",
					"--values",
					helmTestCase.valueFile.getAbsolutePath(),
					chartFolder.getAbsolutePath());
		} catch (ExecException e) {
			failures.add(new AssertionError("Linting failed for " + helmTestCase.name, e));
		}
	}

	@Input
	public String getTestPattern() {
		return testPattern;
	}

	@Option(option = "tests", description = "only tests matching this regular expression are run.")
	public HelmTestTask setTestPattern(String testPattern) {
		this.testPattern = testPattern;
		return this;
	}

	@InputDirectory
	public DirectoryProperty getTests() {
		return tests;
	}

	@OutputDirectory
	public File getTestOutputs() {
		return testOutputs;
	}

	@InputFiles
	public ConfigurableFileCollection getCharts() {
		return charts;
	}

	private static class HelmTestCase {
		private final boolean succeed;
		private final String name;
		private final String title;
		private final File valueFile;
		private final List<HelmTestAssertion> assertions;

		private HelmTestCase(boolean succeed, String name, String title, File valueFile, List<HelmTestAssertion> assertions) {
			this.succeed = succeed;
			this.name = name;
			this.title = title;
			this.valueFile = valueFile;
			this.assertions = assertions;
		}
	}

	private static class HelmTestAssertion {
		private final String file;
		private final Action<File> statement;

		private HelmTestAssertion(String file, Action<File> statement) {
			this.file = file;
			this.statement = statement;
		}
	}

	HelmTestCase fromYamlFile(String commonPathPrefix, File yaml) {
		Object content = HelmPlugin.loadYamlSilently(yaml);
		if (content instanceof Map) {
			Map test = (Map) content;
			if (test.containsKey("title") && test.containsKey("values")) {
				try {
					return testCaseFromMap(createTestName(commonPathPrefix, yaml), test);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				return fromFile(commonPathPrefix, yaml);
			}
		} else {
			// everything is values
			return fromFile(commonPathPrefix, yaml);
		}
	}

	private HelmTestCase fromFile(String commonPathPrefix, File yaml) {
		return new HelmTestCase(
				true,
				createTestName(commonPathPrefix, yaml),
				yaml.getName(),
				yaml,
				Collections.emptyList());
	}

	private static String createTestName(String commonPath, File file) {
		return file.getAbsolutePath()
				.replaceFirst("^" + Pattern.quote(commonPath), "")
				.replaceFirst("\\.(yaml|yml)$", "");
	}

	HelmTestCase testCaseFromMap(String name, Map test) throws IOException {
		String title = (String) test.get("title");
		String description = (String) test.get("description");
		boolean succeed = Optional.ofNullable((Boolean) test.get("succeed")).orElse(true);
		Object values = test.get("values");
		File tempFile = File.createTempFile("values_extract", ".yaml", getTemporaryDir());
		HelmPlugin.YAML.dump(values, new FileWriter(tempFile));
		List<Object> assertions = Optional.ofNullable((List<Object>) test.get("assert"))
				.orElse(Collections.emptyList());
		List<HelmTestAssertion> asserts = assertions.stream()
				.map(o -> testAssertionFrom((Map) o))
				.collect(Collectors.toList());
		return new HelmTestCase(succeed, name, title, tempFile, asserts);
	}

	HelmTestAssertion testAssertionFrom(Map test) {
		String file = (String) test.get("file");
		String operation = (String) test.get("test");
		switch (operation) {
		case "eq":
			return eqAssertionFrom(file, test);
		case "match":
			return matchAssertionFrom(file, test);
		default:
			throw new IllegalArgumentException("unknown test type, use either 'eq' or 'match', found: " + operation);
		}
	}

	private HelmTestAssertion eqAssertionFrom(String fileName, Map test) {
		String path = (String) test.get("path");
		Object expectedValue = test.get("value");
		return new HelmTestAssertion(fileName, file -> {
			List<Object> documents = StreamSupport.stream(HelmPlugin.loadYamlsSilently(file).spliterator(), false)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			getLogger().debug("Loaded {}: {}", file, documents);
			Object fragment = JsonPath.read(documents, path);
			if (!Objects.equals(fragment, expectedValue)) {
				throw new AssertionError("File "
						+ fileName
						+ " at path '"
						+ path
						+ "' does not contain a document matching '"
						+ expectedValue
						+ "'");
			}
		});
	}

	private HelmTestAssertion matchAssertionFrom(String fileName, Map test) {
		String path = (String) test.get("path");
		String pattern = (String) test.get("pattern");
		Pattern compiledPattern = Pattern.compile(pattern);
		return new HelmTestAssertion(fileName, file -> {
			List<Object> documents = StreamSupport.stream(HelmPlugin.loadYamlsSilently(file).spliterator(), false)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			getLogger().debug("Loaded {}: {}", file, documents);
			Object fragment = JsonPath.read(documents, path);
			String fragmentString = HelmPlugin.YAML.dump(fragment);
			getLogger().debug("dumped JSON-PATH {} resolved to '{}'", path, fragmentString);
			if (!compiledPattern.matcher(fragmentString).matches()) {
				throw new AssertionError("File "
						+ fileName
						+ " does not structure documents matching regular expression '"
						+ pattern
						+ "' at path '"
						+ path
						+ "'.");
			}
		});
	}
}
