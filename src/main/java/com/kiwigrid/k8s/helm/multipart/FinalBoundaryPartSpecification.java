package com.kiwigrid.k8s.helm.multipart;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * @author wind57
 *
 * The last part in the multi-part upload specification
 */
public final class FinalBoundaryPartSpecification implements Function<MultipartToken, byte[]> {

	@Override
	public byte[] apply(MultipartToken multipartToken) {
		return ("--" + multipartToken.getBoundary() + "--").getBytes(StandardCharsets.UTF_8);
	}

}
