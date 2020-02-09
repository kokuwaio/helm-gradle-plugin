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

	static void copy(HelmSpec from, HelmSpec to) {
		to.setHelmExecutableDirectory(from.getHelmExecutableDirectory());
		to.setOutputDirectory(from.getOutputDirectory());
		to.setVersion(from.getVersion());
		to.setArchitecture(from.getArchitecture());
		to.setHelmDownloadUrl(from.getHelmDownloadUrl());
		to.setHelmHomeDirectory(from.getHelmHomeDirectory());
	}
}