package com.kiwigrid.k8s.helm.multipart;

import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wind57
 *
 * transform many multi-parts into a BodyPublisher
 */
public final class MultiPartBodyPublisher {

	private final List<MultipartToken> multipartTokens = new ArrayList<>();
	private final String boundary = UUID.randomUUID().toString();

	public HttpRequest.BodyPublisher build() {

		if (multipartTokens.size() == 0) {
			throw new IllegalStateException("Must have at least one part to build multipart message.");
		}

		long howMany = multipartTokens.stream()
				.filter(x -> x.getType() == MultipartType.FINAL_BOUNDARY)
				.count();

		if (howMany == 1) {
			throw new IllegalStateException("Must have more than just final boundary set");
		}

		addFinalBoundaryPart();
		return HttpRequest.BodyPublishers.ofByteArrays(new ToIterable(multipartTokens).transform());
	}

	public final MultiPartBodyPublisher addStringPart(String name, String value) {
		multipartTokens.add(new MultipartToken()
				.setType(MultipartType.STRING)
				.setBoundary(boundary)
				.setName(name)
				.setValue(value));
		return this;
	}

	public final MultiPartBodyPublisher addFilePart(String name, Path value) {
		multipartTokens.add(new MultipartToken()
				.setType(MultipartType.FILE)
				.setBoundary(boundary)
				.setName(name)
				.setPath(value));
		return this;
	}


	private void addFinalBoundaryPart() {
		multipartTokens.add(new MultipartToken()
				.setType(MultipartType.FINAL_BOUNDARY)
				.setBoundary(boundary));
	}

	public String getBoundary() {
		return boundary;
	}

}
