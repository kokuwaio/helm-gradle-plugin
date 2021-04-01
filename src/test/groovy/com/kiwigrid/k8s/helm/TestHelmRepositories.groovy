package com.kiwigrid.k8s.helm

import com.github.tomakehurst.wiremock.junit.WireMockRule

import static com.github.tomakehurst.wiremock.client.WireMock.*

class TestHelmRepositories {

	static def emptyHelmRepoAcceptingPostOrPut(WireMockRule wireMockRule, String method) {
		wireMockRule.stubFor(
				get(urlEqualTo("/index.yaml"))
						.willReturn(aResponse()
								.withStatus(200)
								.withHeader("content-type", "application/yaml")
								.withBody("""\
                                   apiVersion: v1
                                   entries:
                                   generated: 2021-04-01T15:07:54.499029981+01:00
									""".stripIndent()))
		)
		wireMockRule.stubFor(request(method, anyUrl())
				.willReturn(aResponse().withStatus(204)))
	}
}
