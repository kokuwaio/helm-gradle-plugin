package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.kiwigrid.k8s.helm.HelmRepository;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * created on 28.03.18.
 *
 * @author Jörg Eichhorn {@literal <joerg.eichhorn@kiwigrid.com>}
 */
public class HelmDeployTask extends AbstractHelmTask {

	private HelmRepository target;

	public HelmDeployTask() {
		setDescription("Uploads Helm chart to a helm repository");
	}

	@TaskAction
	public void deploy() throws IOException {
		ConfigurableFileTree chartFiles = getProject().fileTree(getOutputDirectory());
		chartFiles.include("*.tgz");
		if (target == null || target.getDeploySpec() == null || target.getDeploySpec().getUploadUrl() == null) {
			throw new IllegalArgumentException("Missing target upload info");
		}
		for (File chartFile : chartFiles) {
			uploadSingle(chartFile);
		}
	}

	@Input
	public HelmRepository getTarget() {
		return target;
	}

	public HelmDeployTask setTarget(HelmRepository target) {
		this.target = target;
		return this;
	}

	private void uploadSingle(File file) throws IOException {
		String uploadUrl = target.getDeploySpec().getUploadUrl();
		if (target.getDeploySpec().getMethod() == HelmRepository.DeploymentSpec.HttpMethod.PUT
				&& uploadUrl.endsWith("/"))
		{
			// concatenate file name
			uploadUrl = uploadUrl.concat(file.getName());
		}
		HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();

		try {
			if (target.isAuthenticated()) {
				String authString = target.getUser() + ":" + target.getPassword();
				String authHeaderValue = "Basic " + new String(Base64.getEncoder()
						.encode(authString.getBytes(StandardCharsets.UTF_8)), StandardCharsets.US_ASCII);
				connection.setRequestProperty("Authorization", authHeaderValue);
			}
			connection.setDoOutput(true);
			connection.setRequestMethod(target.getDeploySpec().getMethod().name());
			connection.setRequestProperty("Content-Type", "application/gzip");
			try (FileInputStream fileInputStream = new FileInputStream(file)) {
				IOUtils.copy(fileInputStream, connection.getOutputStream());
			}
			if (connection.getResponseCode() >= 400) {
				throw new RuntimeException("Failed to upload chart "
						+ file
						+ " via "
						+ connection.getRequestMethod()
						+ " to "
						+ connection.getURL()
						+ " : "
						+ getResponseMessage(connection));
			} else {
				getLogger().lifecycle("Chart {} uploaded via {} {}: {}",
						file,
						connection.getRequestMethod(),
						connection.getURL(),
						getResponseMessage(connection)
				);
			}
		} finally {
			connection.disconnect();
		}
	}

	private static String getResponseMessage(HttpURLConnection connection) throws IOException {
		InputStream errorStream = connection.getErrorStream();
		if (errorStream != null) {
			return "Code "
					+ connection.getResponseCode()
					+ " - "
					+ IOUtils.toString(errorStream, StandardCharsets.UTF_8);
		} else {
			return "Code " + connection.getResponseCode();
		}
	}
}
