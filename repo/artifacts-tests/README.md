# Artifacts Tests

This module contains tests for changes in Maven and Gradle metadata for all maven artifacts we publish, 
as well as verifies the contents of the Kotlin distribution

To reproduce locally build all artifacts first:

```shell
./gradlew clean install publish mvnPublish
```
