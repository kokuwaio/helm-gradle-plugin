package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

@SuppressWarnings("UnstableApiUsage")
public class HelmTestTask extends AbstractHelmTask implements VerificationTask {

	public static final String VALUES_OPTION = "--values";
	public static final String FAILURE_END_TAG = "</failure>";
	private DirectoryProperty tests;
	private Property<String> chartName;

	private final ConfigurableFileCollection charts;

	private final File testOutputs;

	private String testPattern = ".*";

	private boolean ignoreFailures;

	@Inject
	public HelmTestTask(ObjectFactory objectFactory) {
		setDescription("Tests Helm Chart via \"helm lint\" and assert definitions");
		setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
		onlyIf(element -> tests.getOrElse(getTestSourceDir()).getAsFile().exists());
		testOutputs = new File(getProject().getBuildDir(), "helm/test");
		tests = objectFactory.directoryProperty();
		tests.convention(getProject().provider(this::getTestSourceDir));
		charts = objectFactory.fileCollection();
		chartName = objectFactory.property(String.class);
	}

	private Directory getTestSourceDir() {
		return getProject().getLayout().getProjectDirectory().dir("src/test/helm");
	}

	@TaskAction
	public void runTests() throws IOException {
		File[] chartFolders = getOutputDirectory().listFiles(file -> file.isDirectory() && matchesChartName(file));
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
		File testJunitReportFile = new File(testOutputs,  getChartNamePath() + "helm-junit-report.xml");
		getLogger().info("Writing junit xml to {}", testJunitReportFile.getAbsolutePath());
		StringBuilder junitReport = new StringBuilder();
		junitReport.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		junitReport.append("<testsuites>");
		Arrays.stream(chartFolders)
				.forEach(chartFolder -> {
					junitReport
							.append("<testsuite name=\"")
							.append(chartFolder.getName())
							.append("\" tests=\"")
							.append(testCases.size() + 1)
							.append("\">");
					runTestsForChart(lintWithValuesSupported,
							templateWithOutputSupported,
							chartFolder,
							testCases,
							failures,
							junitReport);
					junitReport.append("</testsuite>");
				});
		junitReport.append("</testsuites>");
		try (PrintWriter writer = new PrintWriter(new FileWriter(testJunitReportFile))) {
			writer.write(junitReport.toString());
		}
		getLogger().debug("Test results:\n{}", junitReport);
		if (!failures.isEmpty()) {
			Logger logger = getLogger();
			logger.error("There have been test failures.");
			failures.forEach(assertionError -> logger.info("Test Failure", assertionError));
			if (!ignoreFailures) {
				throw new DefaultMultiCauseException("Failing tests.", failures);
			}
		}
	}

	private boolean matchesChartName(File file) {
		String name = chartName.getOrNull();
		return name == null || name.equals(file.getName());
	}

	private String getChartNamePath() {
		return chartName.getOrNull() == null ? "" : chartName.getOrNull() + File.separator;
	}

	private void runTestsForChart(boolean lintWithValuesSupported, boolean templateWithOutputSupported, File chartFolder, List<HelmTestCase> testCases, List<AssertionError> failures, StringBuilder junitReport) {
		getLogger().info("Linting {} with default values...", chartFolder.getName());
		junitReport.append("<testcase name=\"defaultValueLinting\" classname=\"HelmPlugin\">");
		HelmPlugin.HelmExecResult lintExecResult = HelmPlugin.helmExec(getProject(),
				this,
				"lint",
				chartFolder.getAbsolutePath());
		if (lintExecResult.execResult.getExitValue() != 0) {
			junitReport
					.append("<failure message\"Linting with default values failed\">")
					.append(String.join("\n", lintExecResult.output))
					.append(FAILURE_END_TAG);
			failures.add(new AssertionError("Linting with default values failed"));
		}
		junitReport.append("</testcase>");
		getProject().delete(new File(testOutputs, chartName.getOrElse("")));
		Pattern pattern = Pattern.compile(testPattern);
		testCases
				.stream()
				.filter(helmTestCase -> {
					boolean match = pattern.matcher(helmTestCase.name).matches();
					if (!match) {
						getLogger().info("Skipping '{}', not matching '{}'", helmTestCase.name, testPattern);
						junitReport
								.append("<testcase name=\"")
								.append(helmTestCase.title)
								.append("\"")
								.append(" classname=\"")
								.append(helmTestCase.name)
								.append("\"><skipped message=\"name does not match pattern")
								.append(testPattern)
								.append("\" /></testcase>");
					}
					return match;
				})
				.forEach(helmTestCase -> runSingleTestCase(
						helmTestCase,
						lintWithValuesSupported,
						templateWithOutputSupported,
						chartFolder,
						junitReport,
						failures));
	}

	private void runSingleTestCase(HelmTestCase helmTestCase, boolean lintWithValuesSupported, boolean templateWithOutputSupported, File chartFolder, StringBuilder junitReport, List<AssertionError> failures) {
		getLogger().info("Running test case {}: {}", helmTestCase.name, helmTestCase.title);
		junitReport
				.append("<testcase name=\"")
				.append(helmTestCase.title)
				.append("\"")
				.append(" classname=\"")
				.append(helmTestCase.name)
				.append("\">");

		if (helmTestCase.succeed) {
			if (lintWithValuesSupported) {
				HelmPlugin.HelmExecResult lintExecResult = helmLint(chartFolder, helmTestCase);
				if (lintExecResult.failed()) {
					junitReport
							.append("<failure message=\"Linting with test values failed\">")
							.append(String.join("\n", lintExecResult.output))
							.append(FAILURE_END_TAG);
					failures.add(new AssertionError("Linting with test values failed"));
				}
			}
			if (templateWithOutputSupported) {
				getLogger().debug("Templating and asserting ...");
				File testCaseOutputFolder = new File(testOutputs, getChartNamePath() + helmTestCase.name);
				testCaseOutputFolder.mkdirs();
				HelmPlugin.HelmExecResult helmExecResult = helmTemplate(chartFolder,
						helmTestCase,
						testCaseOutputFolder
				);
				if (helmExecResult.succeeded()) {
					runAssertions(helmTestCase, testCaseOutputFolder, chartFolder.getName(), junitReport, failures);
				} else {
					junitReport.append("<failure message=\"failed to template chart\">")
							.append(String.join("\n", helmExecResult.output))
							.append(FAILURE_END_TAG);
					failures.add(new AssertionError("failed to template chart"));
				}
			}
		} else {
			HelmPlugin.HelmExecResult template = HelmPlugin.helmExec(
					getProject(),
					this,
					"template",
					VALUES_OPTION,
					helmTestCase.valueFile.getAbsolutePath(),
					chartFolder.getAbsolutePath());
			if (template.succeeded()) {
				junitReport.append("<failure message=\"failed to fail template\">")
						.append("Templating should fail, but succeeded.")
						.append(FAILURE_END_TAG);
				failures.add(new AssertionError("Helm template for test '"
						+ helmTestCase.name
						+ "' succeeded when it shall not"));
			}
		}
		junitReport.append("</testcase>");
	}

	private void runAssertions(HelmTestCase helmTestCase, File testCaseOutputFolder, String chartName, StringBuilder junitReport, List<AssertionError> failures) {
		getLogger().info("running assertions...");
		helmTestCase.assertions.forEach(helmTestAssertion -> {
			File input = new File(testCaseOutputFolder, chartName + File.separator + helmTestAssertion.file);
			if (!input.exists() || !input.isFile()) {
				junitReport.append("<failure message=\"failed to find file ")
						.append(input.getAbsolutePath())
						.append("\" />");
				failures.add(new AssertionError("Test '"
						+ helmTestCase.title
						+ "' requires file  "
						+ input.getAbsolutePath()
						+ " which does not exist or is no file."));
				return;
			}
			try {
				helmTestAssertion.statement.execute(input);
			} catch (AssertionError e) {
				junitReport.append("<failure message=\"")
						.append(e.getMessage())
						.append("\" />");
				failures.add(e);
			}
		});
	}

	private HelmPlugin.HelmExecResult helmTemplate(File chartFolder, HelmTestCase helmTestCase, File testCaseOutputFolder) {
		return HelmPlugin.helmExec(
				getProject(),
				this,
				"template",
				VALUES_OPTION,
				helmTestCase.valueFile.getAbsolutePath(),
				"--output-dir",
				testCaseOutputFolder.getAbsolutePath(),
				chartFolder.getAbsolutePath());
	}

	private HelmPlugin.HelmExecResult helmLint(File chartFolder, HelmTestCase helmTestCase) {
		getLogger().debug("Linting ...");
		return HelmPlugin.helmExec(
				getProject(),
				this,
				"lint",
				VALUES_OPTION,
				helmTestCase.valueFile.getAbsolutePath(),
				chartFolder.getAbsolutePath());

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

	public HelmTestTask setTests(Directory tests) {
		this.tests.set(tests);
		return this;
	}

	public HelmTestTask setTests(Provider<Directory> tests) {
		this.tests.set(tests);
		return this;
	}

	@Input
	@org.gradle.api.tasks.Optional
	public Property<String> getChartName() {
		return chartName;
	}

	public void setChartName(String chartName) {
		this.chartName.set(chartName);
	}

	@OutputDirectory
	public File getTestOutputs() {
		return testOutputs;
	}

	@InputFiles
	public ConfigurableFileCollection getCharts() {
		return charts;
	}

	@Override
	public void setIgnoreFailures(boolean ignoreFailures) {
		this.ignoreFailures = ignoreFailures;
	}

	@Override
	public boolean getIgnoreFailures() {
		return ignoreFailures;
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
			List<Object> documents = HelmPlugin.loadYamlsSilently(file);
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
			List<Object> documents = HelmPlugin.loadYamlsSilently(file);
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
