package com.kiwigrid.k8s.helm.multipart;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * @author widn57
 *
 * generate a byte[] that is used as a "part" in the multi-part specification
 */
public final class FilePartSpecification implements Function<MultipartToken, byte[]> {

	@Override
	public byte[] apply(MultipartToken multipartToken) {
		try {

			Path path = multipartToken.getPath();
			String filename = path.getFileName().toString();

			String partHeader =
				"--" + multipartToken.getBoundary() + "\r\n" +
				"Content-Disposition: form-data; name=\"chart\"" + "; filename=" + filename + "\r\n" +
				"Content-Type: application/octet-stream" + "\r\n" + "\r\n";

			byte[] epilogue = partHeader.getBytes(StandardCharsets.UTF_8);
			byte[] file = Files.readAllBytes(path);
			byte[] prologue = "\r\n".getBytes();

			ByteArrayOutputStream all = new ByteArrayOutputStream(epilogue.length + file.length + prologue.length);
			all.write(epilogue);
			all.write(file);
			all.write(prologue);

			return all.toByteArray();

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
