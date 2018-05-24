[![Maven Central](https://img.shields.io/maven-central/v/org.tools4j/tools4j-nobark.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22tools4j-nobark%22)
[![Javadocs](http://www.javadoc.io/badge/org.tools4j/tools4j-nobark.svg)](http://www.javadoc.io/doc/org.tools4j/tools4j-nobark)

<b>tools4j-nobark</b> is a library with low latency zero gc data structures and utilities.

##### Conflation queues
A [conflation queue](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/queue/ConflationQueue.html)
is a queue with a safety mechanism to prevent overflow.  Values are enqueued with a conflation key, and if a value with
the same key already resides in the queue then the two values will be "conflated".  Conflation in the simplest case
means that the most recent value survives and replaces older values;  some more advanced implementations support merging
when conflation occurs.

[more information](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/queue/package-summary.html)

### Gradle
```gradle
dependencies {
    compile 'org.tools4j:tools4j-nobark:1.2'
}
```

### Maven
```xml
<dependency>
    <groupId>org.tools4j</groupId>
    <artifactId>tools4j-nobark</artifactId>
    <version>1.2</version>
</dependency>
```

### API Javadoc
[![Javadocs](http://javadoc.io/badge/org.tools4j/tools4j-nobark.svg)](http://javadoc.io/doc/org.tools4j/tools4j-nobark)

