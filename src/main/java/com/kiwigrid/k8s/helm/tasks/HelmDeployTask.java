package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.kiwigrid.k8s.helm.HelmPluginExtension;
import com.kiwigrid.k8s.helm.HelmRepository;
import com.kiwigrid.k8s.helm.multipart.MultiPartBodyPublisher;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * created on 28.03.18.
 *
 * @author Jörg Eichhorn {@literal <joerg.eichhorn@kiwigrid.com>}
 */
public class HelmDeployTask extends AbstractHelmTask {

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.executor(Executors.newFixedThreadPool(2))
			.build();

	private HelmRepository target;

	private Logger logger;

	public HelmDeployTask() {
		setGroup(BasePlugin.UPLOAD_GROUP);
		setDescription("Uploads Helm chart to a helm repository");
	}

	@TaskAction
	public void deploy() {
		logger = getLogger();
		File chartFolder = getOutputDirectory();
		target = getProject().getExtensions().getByType(HelmPluginExtension.class).getDeployTo();
		ConfigurableFileTree chartFiles = getProject().fileTree(chartFolder);
		chartFiles.include("*.tgz");
		if (target == null || target.getDeploySpec() == null || target.getDeploySpec().getUploadUrl() == null) {
			throw new IllegalArgumentException("Missing target upload info");
		}
		for (File chartFile : chartFiles) {
			uploadSingle(chartFile);
		}
	}

	private void uploadSingle(File file) {

		MultiPartBodyPublisher publisher = new MultiPartBodyPublisher().addFilePart(file.getName(), file.toPath());

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(target.getDeploySpec().getUploadUrl()))
				.POST(publisher.build())
				.header("Content-Type", "multipart/form-data; boundary=" + publisher.getBoundary());

		if(target.isAuthenticated()) {
			builder.header("Authorization", basicAuth(target.getUser(), target.getPassword()));
		}

		HttpRequest request = builder.build();
		logRequest(request);

		HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.handle(handlerFunction())
				.join();
	}


	private String basicAuth(String username, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private BiFunction<HttpResponse<String>, Throwable, String> handlerFunction() {
		return (s, t) -> {
			if (t != null) {
				logger.error("error : " + t.getCause());
				throw new RuntimeException(t.getCause());
			}
			logger.lifecycle("status code when uploading : " + s.statusCode());
			logger.lifecycle("result body when uploading : " + s.body());
			return s.body();
		};
	}

	private void logRequest(HttpRequest request) {
		LoggerBodyPublisher pub = new LoggerBodyPublisher();
		request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()).subscribe(pub);

		String headers = request.headers()
				.map()
				.entrySet()
				.stream()
				.map(x -> x.getKey() + x.getValue())
				.collect(Collectors.joining("\n"));

		logger.lifecycle("request : \n" + request(headers, request.method(), request.uri().toASCIIString(), pub.raw()));
	}

	private static String request(String headers, String method, String uri, String payload) {
		StringBuilder sb = new StringBuilder();
		String copyPayload = payload.isEmpty() ? "<NOT_PROVIDED>" : payload.contains("�") ? "BIG_PAYLOAD_HERE" : payload;
		return sb.append("\n").append(headers)
				.append("\n").append(padEnd("Method")).append(method)
				.append("\n").append(padEnd("URI")).append(uri)
				.append("\n").append(padEnd("Payload")).append(copyPayload)
				.toString();
	}

	private static String padEnd(String input) {
		return padEnd(input, 20, ' ') + ": ";
	}

	public static String padEnd(String string, int minLength, char padChar) {
		return string + String.valueOf(padChar).repeat(minLength - string.length());
	}

}
