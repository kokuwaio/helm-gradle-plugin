Contributions Welcome!
===

What to contribute
---

Pick a [ticket](https://github.com/kiwigrid/helm-gradle-plugin/issues), or an item off the [future work list](README.md#further-work) in the or just the one feature you want to see here.

Build plugin locally
---

To build and test locally, simply issue : 

```
gradle clean && gradle publishToMavenLocal
```

you can optionally also change the version to be published in your local maven repo by changing `gradle.properties` file.

Once the publishing is done, simply use that particular version. For example:

```
helm {
    version '0.0.0-SNAPSHOT'
    ....
```

How
---

1. Fork 
2. Develop
3. Issue a Pull-Request against `master`

Releasing
---

Push a tag of the form `v1.2.3`, a github action will do the rest.