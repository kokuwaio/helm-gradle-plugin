package com.kiwigrid.k8s.helm

import org.junit.rules.TemporaryFolder

class TestProjects {

	static def createSimpleChartProject(TemporaryFolder testProjectDir, File buildFile, String helmVersion, repoUrl, repoMethod) {
		testProjectDir.newFolder("src", "main", "helm")
		testProjectDir.newFolder("src", "test", "helm")
		testProjectDir.newFolder("src", "main", "helm", "templates")
		testProjectDir.newFolder("build")
		buildFile << """\
            helm {
                version "${helmVersion}"
                repositories {
                  mockrepo {
                    url "${repoUrl}"
                    deployVia('${repoMethod}').toFetchUrl()
                  }
                }
                expansions = [
                    helm: [
                        chartProjectName: project.name, 
                        chartVersion: project.version
                    ]
                ]
                deployTo repositories.mockrepo
            }
            """.stripIndent()
		testProjectDir.newFile("src/main/helm/Chart.yaml") << """\
            apiVersion: v1
            description: A simple good working helm chart 
            name: \${helm.chartProjectName}
            version: \${helm.chartVersion}
            """.stripIndent()
		testProjectDir.newFile("src/main/helm/templates/deployment.yaml") << """\
            kind: Deployment
            apiVersion: extensions/v1beta1
            metadata:
              name: {{ .Release.Name }}mydeployment
            spec:
              replicas: {{ .Values.replicaCount }}
              strategy:
                type: {{ .Values.updateStrategy | quote }}
                rollingUpdate:
                  maxUnavailable: {{ .Values.maxUnavailable }}
              template:
                metadata:
                  labels:
                    heritage: {{ .Release.Service | quote }}
                    release: {{ .Release.Name | quote }}
                    chart: {{ .Chart.Name }}
                    name: {{ .Release.Name }}mydeploymentpod
                spec:
                  containers:
                  - name: {{ .Release.Name }}mydeploymentpodcontainer
                    image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
                    imagePullPolicy: {{ .Values.image.pullPolicy | quote }}
                    resources:
            {{ toYaml .Values.resources | indent 10 }}
                    env:
                      - name: ENVIRONMENT
                        value: "kubernetes"
                  imagePullSecrets:
                    - name: {{ .Values.image.pullSecret | quote }}
            """.stripIndent()
		testProjectDir.newFile("src/main/helm/values.yaml") << """\
            image:
              repository: example.com/\${helm.chartProjectName}
              tag: \${helm.chartVersion}
              pullPolicy: IfNotPresent
              pullSecret: registry-secret
            resources:
              requests:
                memory: 512Mi
                cpu: 0.2
              limits:
                memory: 1024Mi
                cpu: 0.4
            """.stripIndent()

		testProjectDir.newFile("src/test/helm/valuetest.yaml") << """\
        image:
          repository: "docker.io"
        resources:
          limits:
            cpu: 1
        """.stripIndent()
		testProjectDir.newFile("src/test/helm/structtest.yaml") << """\
        title: "CPU requests should work"
        values:
          resources:
            requests:
              cpu: 1
        assert:
          - file: templates/deployment.yaml
            test: "eq"
            path: "[0]['spec']['template']['spec']['containers'][0]['resources']['requests']['cpu']"
            value: 1
        """.stripIndent()
	}

	static def createChartProjectWithCustomPaths(TemporaryFolder testProjectDir, File buildFile, String helmVersion) {
        testProjectDir.newFolder("differentSrc", "main", "helm")
        testProjectDir.newFolder("differentSrc", "test", "helm")
        testProjectDir.newFolder("differentSrc", "main", "helm", "templates")
        testProjectDir.newFolder("build")
        buildFile << """\
            helm {
                version "${helmVersion}"
                expansions = [
                    helm: [
                        chartProjectName: project.name, 
                        chartVersion: project.version
                    ]
                ]
            }
            
            helmChartBuild() {
                source = file(project.projectDir.getAbsolutePath() + '/differentSrc/main/helm')
            }
            
            helmChartTest() {
                tests = file(project.projectDir.getAbsolutePath() + '/differentSrc/test/helm')
            }
            """.stripIndent()
		testProjectDir.newFile("differentSrc/main/helm/Chart.yaml") << """\
            apiVersion: v1
            description: A simple good working helm chart 
            name: \${helm.chartProjectName}
            version: \${helm.chartVersion}
            """.stripIndent()
		testProjectDir.newFile("differentSrc/main/helm/templates/deployment.yaml") << """\
            kind: Deployment
            apiVersion: extensions/v1beta1
            metadata:
              name: {{ .Release.Name }}mydeployment
            spec:
              replicas: {{ .Values.replicaCount }}
              strategy:
                type: {{ .Values.updateStrategy | quote }}
                rollingUpdate:
                  maxUnavailable: {{ .Values.maxUnavailable }}
              template:
                metadata:
                  labels:
                    heritage: {{ .Release.Service | quote }}
                    release: {{ .Release.Name | quote }}
                    chart: {{ .Chart.Name }}
                    name: {{ .Release.Name }}mydeploymentpod
                spec:
                  containers:
                  - name: {{ .Release.Name }}mydeploymentpodcontainer
                    image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
                    imagePullPolicy: {{ .Values.image.pullPolicy | quote }}
                    resources:
            {{ toYaml .Values.resources | indent 10 }}
                    env:
                      - name: ENVIRONMENT
                        value: "kubernetes"
                  imagePullSecrets:
                    - name: {{ .Values.image.pullSecret | quote }}
            """.stripIndent()
		testProjectDir.newFile("differentSrc/main/helm/values.yaml") << """\
            image:
              repository: example.com/\${helm.chartProjectName}
              tag: \${helm.chartVersion}
              pullPolicy: IfNotPresent
              pullSecret: registry-secret
            resources:
              requests:
                memory: 512Mi
                cpu: 0.2
              limits:
                memory: 1024Mi
                cpu: 0.4
            """.stripIndent()

		testProjectDir.newFile("differentSrc/test/helm/valuetest.yaml") << """\
        image:
          repository: "docker.io"
        resources:
          limits:
            cpu: 1
        """.stripIndent()
		testProjectDir.newFile("differentSrc/test/helm/structtest.yaml") << """\
        title: "CPU requests should work"
        values:
          resources:
            requests:
              cpu: 1
        assert:
          - file: templates/deployment.yaml
            test: "eq"
            path: "[0]['spec']['template']['spec']['containers'][0]['resources']['requests']['cpu']"
            value: 1
        """.stripIndent()
	}

	static def createChartProjectWithTwoDifferentCharts(TemporaryFolder testProjectDir, File buildFile, String helmVersion) {
        testProjectDir.newFolder("src", "main", "helm")
        testProjectDir.newFolder("src", "test", "helm")
        testProjectDir.newFolder("src", "main", "helm", "templates")
        testProjectDir.newFolder("src", "main", "helm2")
        testProjectDir.newFolder("src", "test", "helm2")
        testProjectDir.newFolder("src", "main", "helm2", "templates")
        testProjectDir.newFolder("build")
        buildFile << """\
            import com.kiwigrid.k8s.helm.tasks.HelmBuildTask
            import com.kiwigrid.k8s.helm.tasks.HelmTestTask

            helm {
                version "${helmVersion}"
                expansions = [
                    helm: [
                        chartProjectName: project.name, 
                        chartVersion: project.version
                    ]
                ]
            }
            
            tasks.register("helmSecondChartBuild", HelmBuildTask.class) {
              dependsOn tasks.named("helmRepoSync")
              expansions = helm.expansions
              copyFrom helm

              // custom configuration
              source = file('src/main/helm2')
              outputDirectory = file("\$buildDir/helm/repo2")
            }
            
            tasks.register("helmSecondChartTest", HelmTestTask.class) {
              dependsOn tasks.named("helmSecondChartBuild")
              copyFrom helm

              // custom configuration
              tests = file('src/test/helm2')
              outputDirectory = file("\$buildDir/helm/repo2")
            }
            """.stripIndent()
		testProjectDir.newFile("src/main/helm/Chart.yaml") << """\
            apiVersion: v1
            description: A simple good working helm chart 
            name: \${helm.chartProjectName}
            version: \${helm.chartVersion}
            """.stripIndent()
		testProjectDir.newFile("src/main/helm2/Chart.yaml") << """\
            apiVersion: v1
            description: A simple good working helm chart 
            name: \${helm.chartProjectName}2
            version: \${helm.chartVersion}
            """.stripIndent()

		testProjectDir.newFile("src/main/helm/templates/deployment.yaml") << """\
            kind: Deployment
            apiVersion: extensions/v1beta1
            metadata:
              name: mydeployment
            spec:
              replicas: 1
              template:
                spec:
                  containers:
                  - name: {{ .Release.Name }}mydeploymentpodcontainer
                    image: "test:test"
            """.stripIndent()
		testProjectDir.newFile("src/main/helm2/templates/deployment.yaml") << """\
            kind: Deployment
            apiVersion: extensions/v1beta1
            metadata:
              name: mydeployment2
            spec:
              replicas: 1
              template:
                spec:
                  containers:
                  - name: {{ .Release.Name }}mydeploymentpodcontainer
                    image: "test:test"
            """.stripIndent()

		testProjectDir.newFile("src/test/helm/structtest.yaml") << """\
        title: "Check name"
        assert:
          - file: templates/deployment.yaml
            test: "eq"
            path: "[0]['metadata']['name']"
            value: mydeployment
        """.stripIndent()
		testProjectDir.newFile("src/test/helm2/structtest.yaml") << """\
        title: "Check name"
        assert:
          - file: templates/deployment.yaml
            test: "eq"
            path: "[0]['metadata']['name']"
            value: mydeployment2
        """.stripIndent()
	}
}
