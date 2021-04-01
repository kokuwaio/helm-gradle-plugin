package com.kiwigrid.k8s.helm.multipart;

import java.nio.file.Path;

/**
 * @author wind57
 *
 * acts as a "holder" or "context" to carry information.
 * not all properties are mandatory
 */
public final class MultipartToken {

	private MultipartType type;

	private String name;
	private String value;

	private Path path;
	private String filename;
	private String contentType;

	private String boundary;

	public MultipartType getType() {
		return type;
	}

	public MultipartToken setType(MultipartType type) {
		this.type = type;
		return this;
	}

	public String getName() {
		return name;
	}

	public MultipartToken setName(String name) {
		this.name = name;
		return this;
	}

	public String getValue() {
		return value;
	}

	public MultipartToken setValue(String value) {
		this.value = value;
		return this;
	}

	public Path getPath() {
		return path;
	}

	public MultipartToken setPath(Path path) {
		this.path = path;
		return this;
	}

	public String getFilename() {
		return filename;
	}

	public MultipartToken setFilename(String filename) {
		this.filename = filename;
		return this;
	}

	public String getContentType() {
		return contentType;
	}

	public MultipartToken setContentType(String contentType) {
		this.contentType = contentType;
		return this;
	}

	public String getBoundary() {
		return boundary;
	}

	public MultipartToken setBoundary(String boundary) {
		this.boundary = boundary;
		return this;
	}
}
