package com.kiwigrid.k8s.helm;

import java.io.Serializable;
import java.util.Objects;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

public class HelmRepository implements Serializable {

	private final String name;

	private String url;

	private DeploymentSpec deploySpec;

	private String user;

	private String password;

	// X-JFrog-Art-Api from artifactory
	private String apiKey;

	public HelmRepository(String name) {
		this.name = name;
	}

	public HelmRepository(String name, String url) {
		this(name);
		this.url = url;
	}

	public boolean isAuthenticated() {
		return password != null || user != null;
	}

	public boolean isApiKeyProvided() {
		return apiKey != null;
	}

	public DeployDsl deployVia(DeploymentSpec.HttpMethod httpMethod) {
		return new DeployDsl(httpMethod);
	}

	public class DeployDsl {
		private final DeploymentSpec.HttpMethod httpMethod;

		private DeployDsl(DeploymentSpec.HttpMethod httpMethod) {
			this.httpMethod = httpMethod;
		}
		public void toFetchUrl() {
			deploySpec = new DeploymentSpec().uploadUrl(url).method(httpMethod);
		}
		public void to(String url) {
			deploySpec = new DeploymentSpec().uploadUrl(url).method(httpMethod);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		HelmRepository that = (HelmRepository) o;
		return Objects.equals(name, that.name) &&
				Objects.equals(url, that.url) &&
				Objects.equals(deploySpec, that.deploySpec) &&
				Objects.equals(user, that.user) &&
				Objects.equals(password, that.password) &&
				Objects.equals(apiKey, that.apiKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, url, deploySpec, user, password, apiKey);
	}

	@Input
	public String getApiKey() {
		return apiKey;
	}

	public HelmRepository apiKey(String apiKey) {
		this.apiKey = apiKey;
		return this;
	}

	@Input
	public String getName() {
		return name;
	}

	@Input
	public String getUrl() {
		return url;
	}

	public HelmRepository url(String url) {
		this.url = url;
		return this;
	}

	@Input
	public String getUser() {
		return user;
	}

	public HelmRepository user(String user) {
		this.user = user;
		return this;
	}

	@Input
	public String getPassword() {
		return password;
	}

	public HelmRepository password(String password) {
		this.password = password;
		return this;
	}

	public static class DeploymentSpec implements Serializable {

		public enum HttpMethod {
			POST, PUT
		}

		@Input
		public HttpMethod getMethod() {
			return method;
		}

		private DeploymentSpec.HttpMethod method;

		@Input
		public String getUploadUrl() {
			return uploadUrl;
		}

		private String uploadUrl;

		public DeploymentSpec method(DeploymentSpec.HttpMethod method) {
			this.method = method;
			return this;
		}

		public DeploymentSpec uploadUrl(String url) {
			this.uploadUrl = url;
			return this;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			DeploymentSpec that = (DeploymentSpec) o;
			return method == that.method &&
					Objects.equals(uploadUrl, that.uploadUrl);
		}

		@Override
		public int hashCode() {
			return Objects.hash(method, uploadUrl);
		}

	}

	@Nested
	public DeploymentSpec getDeploySpec() {
		return deploySpec;
	}
}
