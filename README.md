Helm Plugin for Gradle
======================

This is a simple plugin for interacting with [Helm](https://helm.sh/) in gradle builds.

![Java CI](https://github.com/kiwigrid/helm-gradle-plugin/workflows/Java%20CI/badge.svg)

Why?
---
When the plugin has been created, there hasn't been an alternative. These days there are numerous alternatives:
* https://github.com/unbroken-dome/gradle-helm-plugin
  
  This one does it all but requires a locally installed helm
* https://github.com/rmee/gradle-plugins/tree/master/helm

  Comes integrated with a `kubectl` plugin and also allows
  remote release management.
* https://github.com/wfhartford/gradle-helm

  Extensive multi chart support.
  
* https://github.com/frantic777/helmplugin

  Yet another one.
  
* https://plugins.gradle.org/plugin/nl.surfsara.sda.buildplugin

  That one is also listed on the gradle plugin portal.
  
### But why then?

Use one of the above if they suit your use case. This plugin is different a little bit.

* Focus on a single thing and do it good: **build charts**.
* Thus only **limited support for uploads/publishing**. Although
  [chartmuseum](https://chartmuseum.com/) and 
  [artifactory](https://jfrog.com/artifactory/) should 
  work, I recommend to **use different ways of helm chart
  publishing** as there's no official spec on how to do it (Helm 3 has experimental support for publishing to OCI registries).
  For example use the [artifactory gradle plugin](https://www.jfrog.com/confluence/display/RTF/Gradle+Artifactory+Plugin)
  if you're using artifactory.
* **the only plugin** which has a real **notion of chart tests** including some
  simple types of assertions.


What?
---

* zero config because of sane defaults
* ready for CI/CD, no dependencies to build environment (downloads appropriate Helm version automatically)
* provides task types:
  * `HelmInitTask`: client only helm init, usually done only once
  * `HelmBuildTask`: render the expansions, build deps and package the chart
  * `HelmTestTask`: test the packaged chart
    * executes `helm lint` with default values
    * for each test:
      * executes `helm lint` for test values
      * executes `helm template` for test values
      * runs any given assertions against rendered output
  * `HelmDeployTask`: uploads the packaged chart
* preconfigures all tasks according to DSL (see below)

Tested with Gradle 6

How?
---

### Activate
```groovy
plugins {
    id 'com.kiwigrid.helm' version '1.3.0'
}
```

### Configure
```groovy
// everything is optional
helm {
    version 'canary' // defaults to 2.15.2
    architecture 'amd64' // auto-detected if not given
    operatingSystem 'linux' // auto-detected if not given
    helmDownloadUrl 'https://example.com/helm.tar.gz' // defaults to 'https://kubernetes-helm.storage.googleapis.com/helm-v${version}-${operatingSystem}-${architecture}.tar.gz'

    // will be added via: helm repo add <name> <url>, non-existent will be removed (but 'local' and 'stable')
    repositories {
        myHelmRepoName {
            url "https://example.com"
            user "happy-dev" // username used for basic auth (supported since helm v2.9.0-rc3)
            password "1234" // password used for basic auth (supported since helm v2.9.0-rc3)
            deployVia 'PUT' to 'https://example.de/' // also supports 'POST', filename will be appended if url ends with '/' and method is 'PUT'
			// shortcut if url is the same:
			// deployVia 'PUT' toFetchUrl()
        }
    }

    // expansions are replaced in the chart files if found in the form ${path.to.value}
    expansions = [
            helm: [
                    chartName:'my-chart',
                    chartVersion: project.version
            ],
            anotherParam: project.version,
            path: [
                    to: [
                            value: 'foobar'
                    ]
            ]
    ]

    // this will upload the chart to the respective repo using the appropriate deploy spec
    deployTo repositories.myHelmRepoName
}
```

### Author chart
```
<project folder>
|-- src/main/helm
|   |-- Chart.yml
|   `-- templates
|       `-- ...
`-- src/test/helm
    |-- test-suite01
    |   |-- values_test1.yaml
    |   `-- values_test2.yaml
    |-- value-config-1.yaml
    |-- value-config-2.yaml
    `-- value-config-3.yaml
```

#### Noteworthy:
* the plugin automatically applies the base and attaches tasks as dependencies to the lifecycle tasks
* If you're using Helm >= `2.8.0` `helmChartTest` is locally rendering 
  all templates for each test value file into `build/helm/test/<value-file-name>/`
  so you can test drive how your templates react to values
* If you're using Helm >= `2.9.0-rc3`
  * `helmChartTest` is linting once with default values and once per 
     test so you can test drive various value combinations.
  * authenticated repositories are supported (using basic auth)

#### Author Tests
The structure of a test yaml file can take 2 different forms.

If on the top level the key `title` and `assert` are found this is
considered a _structured test_. Otherwise it's just a _value test_.

A tests name is determined by the relative path within `src/test/helm`.

##### Value Tests
Provides a sample value file. The test case is named like the file name.
The value file is fed into `helm template` and `helm lint`. 
If both succeed with exit code 0 the test is considered successful.

##### Structured Tests
Create a yaml structure like this:
```yaml
# a simple title for logging purposes
title: "Test title"

# everything under values is provided in a separate value file to helm
values: ~ 

# by default value files are expected to work, set succeed to false
# for negative testing (asserts are ignored in this case)
succeed: true  

# any assertions to be made on the rendered files
assert:
  - file: "<rendered file name relative to chart root>"
    test: "<name-of-test>"
    # ... test specific properties, see below
  - ...
```
If and only if `title` **and** `values` is present, a structured test is assumed.

Supported tests are:
* `eq`: property `path` is [JSON-PATH](https://github.com/json-path/JsonPath)'d out of file and has to equal propery `value` 
* `match`: property `path` is [JSON-PATH](https://github.com/json-path/JsonPath)'d out of file as YAML and has to match the regular expression given in property `pattern` 

Note:
 * loading a manifest yaml is done such that it's always a list of yaml documents whereby the outermost list has to be respected in the path (e.g. `$[*][data]`)
 * `file` is a relative path to the chart (e.g. `templates/configmap.yaml`)
 * `pattern` is a [java regular expression](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html) which has to match the complete extracted fragment, you might want to use embedded modifiers (e.g. `(?ms)`)

### Further work:
* support for Helm 3
* generation of test results report as junit compatible xml
* provenance files
* certificate based authentication
* auto-detect at least local chart dependencies to improve up-to-date checks
* support for [kube score](https://github.com/zegl/kube-score)