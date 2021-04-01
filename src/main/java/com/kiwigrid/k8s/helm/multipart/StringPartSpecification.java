package com.kiwigrid.k8s.helm.multipart;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * @author widn57
 *
 * generate a byte[] that is used as a "part" in the multi-part specification
 */
public final class StringPartSpecification implements Function<MultipartToken, byte[]> {

	@Override
	public byte[] apply(MultipartToken multipartToken) {
		String part =
			"--" +
			multipartToken.getBoundary() + "\r\n" +
			"Content-Disposition: form-data; name=" + multipartToken.getName() + "\r\n" +
			"Content-Type: text/plain; charset=UTF-8" + "\r\n" + "\r\n" + multipartToken.getValue() + "\r\n";

		return part.getBytes(StandardCharsets.UTF_8);
	}

}
