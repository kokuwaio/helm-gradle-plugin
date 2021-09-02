package com.kiwigrid.k8s.helm

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.UrlPattern
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class HelmPluginTest extends Specification {

	static final String PROJECT_NAME = "hello-world"

	@Rule WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.options().dynamicPort(), true);
	@Rule TemporaryFolder testProjectDir = new TemporaryFolder()
	File settingsFile
	File buildFile
	File propertiesFile

	def setup() {
		settingsFile = testProjectDir.newFile('settings.gradle')
		buildFile = testProjectDir.newFile('build.gradle')
		propertiesFile = testProjectDir.newFile('gradle.properties')

		settingsFile << "rootProject.name = '${PROJECT_NAME}'"
		buildFile << """\
            plugins {
              id "com.kiwigrid.helm"
            }
            version = "\$version"
        """.stripIndent()
		propertiesFile << "version=1.0.0"
	}

	def "'build' and 'test' tasks are visible in standard task list"() {
		given:
		buildFile << """
            helm {}
        """

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('tasks')
				.withPluginClasspath()
				.build()

		then:
		result.output.contains('helmChartBuild')
		result.output.contains('helmChartTest')
		result.task(":tasks").outcome == SUCCESS
	}

	@Unroll
	def "simple v1 chart can be build, tested and deployed by #deployMethod with helm #helmVersion"() {
		given:
		TestProjects.createSimpleChartProject(
				testProjectDir,
				buildFile,
				helmVersion,
				wireMockRule.baseUrl(),
				deployMethod)
		TestHelmRepositories.emptyHelmRepoAcceptingPostOrPut(wireMockRule, deployMethod)

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withPluginClasspath()
				.withArguments(":helmChartBuild", ":helmChartTest", ":helmDeploy", "--info", "--stacktrace")
				.build()

		then:
		result.task(":helmChartBuild").outcome == SUCCESS
		result.task(":helmChartTest").outcome == SUCCESS
		result.task(":helmDeploy").outcome == SUCCESS
		new File(testProjectDir.root, "/build/helm/repo/${PROJECT_NAME}-1.0.0.tgz").exists()
		new File(testProjectDir.root, "/build/helm/test/${PROJECT_NAME}/helm-junit-report.xml").exists()
		def publications = wireMockRule.findAll(RequestPatternBuilder.newRequestPattern(RequestMethod.fromString(deployMethod), UrlPattern.ANY))
		publications.size() == 1

		where:
		[helmVersion, deployMethod] << [["2.17.0", "3.0.0"], [ "PUT", "POST" ]].combinations()
	}

	def "simple chart can be build and tested with helm #helmVersion and source for charts and sources for tests are custom"() {
		given:
		TestProjects.createChartProjectWithCustomPaths(
				testProjectDir,
				buildFile,
				helmVersion
		)

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withPluginClasspath()
				.withArguments(":helmChartBuild", ":helmChartTest", "--info", "--stacktrace")
				.build()

		then:
		result.task(":helmChartBuild").outcome == SUCCESS
		result.task(":helmChartTest").outcome == SUCCESS
		new File(testProjectDir.root, "/build/helm/repo/${PROJECT_NAME}-1.0.0.tgz").exists()
		new File(testProjectDir.root, "/build/helm/test/${PROJECT_NAME}/helm-junit-report.xml").exists()

		where:
		helmVersion << ["2.17.0", "3.0.0"]
	}

	def "two chart in one project can be build and tested with helm #helmVersion"() {
		given:
		TestProjects.createChartProjectWithTwoDifferentCharts(
				testProjectDir,
				buildFile,
				helmVersion
		)

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withPluginClasspath()
				.withArguments(":helmChartTest", ":helmSecondChartTest", "--info", "--stacktrace")
				.build()

		then:
		result.task(":helmChartBuild").outcome == SUCCESS
		result.task(":helmChartTest").outcome == SUCCESS
		new File(testProjectDir.root, "/build/helm/repo/${PROJECT_NAME}-1.0.0.tgz").exists()
		new File(testProjectDir.root, "/build/helm/repo2/${PROJECT_NAME}2-1.0.0.tgz").exists()
		new File(testProjectDir.root, "/build/helm/test/${PROJECT_NAME}/helm-junit-report.xml").exists()
		new File(testProjectDir.root, "/build/helm/test/${PROJECT_NAME}2/helm-junit-report.xml").exists()

		where:
		helmVersion << ["2.17.0", "3.0.0"]
	}
}
