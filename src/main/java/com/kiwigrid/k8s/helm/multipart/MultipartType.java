package com.kiwigrid.k8s.helm.multipart;

/**
 * @author wind57
 *
 * What type of multipart is this. Currently only a String and a File are supported,
 * which is enough for uploading to chart museums
 */
enum MultipartType {

	STRING,
	FILE,
	FINAL_BOUNDARY; // the boundary itself, not a specific part

}
