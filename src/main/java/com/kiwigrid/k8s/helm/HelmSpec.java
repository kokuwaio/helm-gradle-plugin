package com.kiwigrid.k8s.helm;

import java.io.File;

public interface HelmSpec {
	
	void setHelmExecutableDirectory(File helmExecutableDirectory);
	File  getHelmExecutableDirectory();

	void setOutputDirectory(File outputDirectory);
	File getOutputDirectory();

	void  setVersion(String version);
	String getVersion();

	void setArchitecture(String architecture);
	String getArchitecture();

	void  setHelmDownloadUrl(String helmDownloadUrl);
	String getHelmDownloadUrl();

	void setHelmHomeDirectory(File helmHomeDirectory);
	File getHelmHomeDirectory();

	default void copyFrom(HelmSpec other) {
		this.setHelmExecutableDirectory(other.getHelmExecutableDirectory());
		this.setOutputDirectory(other.getOutputDirectory());
		this.setVersion(other.getVersion());
		this.setArchitecture(other.getArchitecture());
		this.setHelmDownloadUrl(other.getHelmDownloadUrl());
		this.setHelmHomeDirectory(other.getHelmHomeDirectory());
	}
}