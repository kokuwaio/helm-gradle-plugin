package com.kiwigrid.k8s.helm.tasks;

import java.io.File;

import com.kiwigrid.k8s.helm.HelmSpec;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;

public class AbstractHelmTask extends AbstractTask implements HelmSpec {

	private File helmExecutableDirectory;
	private File outputDirectory;
	private String version;
	private String architecture;
	private String helmDownloadUrl;
	private File helmHomeDirectory;

	@Override
	public void setHelmExecutableDirectory(File helmExecutableDirectory) {
		this.helmExecutableDirectory = helmExecutableDirectory;
	}

	@InputDirectory
	@Override
	public File getHelmExecutableDirectory() {
		return helmExecutableDirectory;
	}

	@Override
	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	@Internal
	@Override
	public File getOutputDirectory() {
		return outputDirectory;
	}

	@Override
	public void setVersion(String version) {
		this.version = version;
	}

	@Input
	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public void setArchitecture(String architecture) {
		this.architecture = architecture;
	}

	@Input
	@Override
	public String getArchitecture() {
		return architecture;
	}

	@Override
	public void setHelmDownloadUrl(String helmDownloadUrl) {
		this.helmDownloadUrl = helmDownloadUrl;
	}

	@Input
	@Override
	public String getHelmDownloadUrl() {
		return helmDownloadUrl;
	}

	@Override
	public void setHelmHomeDirectory(File helmHomeDirectory) {
		this.helmHomeDirectory = helmHomeDirectory;
	}

	@Internal
	@Override
	public File getHelmHomeDirectory() {
		return helmHomeDirectory;
	}

	@Input
	public String getHelmHomeDirectoryPath() {
		return getHelmHomeDirectory().getAbsolutePath();
	}
}
