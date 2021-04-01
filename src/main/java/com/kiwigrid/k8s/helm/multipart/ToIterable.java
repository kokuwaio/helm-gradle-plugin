package com.kiwigrid.k8s.helm.multipart;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author wind57
 *
 * Transform a List of {@link MultipartToken} to an Iterable of byte[].
 * This is needed for the http client
 */
public final class ToIterable {

	private static final Map<MultipartType, Function<MultipartToken, byte[]>> MAP = Map.of(

		MultipartType.STRING, new StringPartSpecification(),
		MultipartType.FILE, new FilePartSpecification(),
		MultipartType.FINAL_BOUNDARY, new FinalBoundaryPartSpecification()

	);

	private final List<MultipartToken> list;

	public ToIterable(List<MultipartToken> list) {
		this.list = list;
	}

	public Iterable<byte[]> transform() {
		return list.stream()
				.map(x -> Optional.ofNullable(MAP.get(x.getType()))
						.orElseThrow()
						.apply(x))
				.collect(Collectors.toList());
	}

}
