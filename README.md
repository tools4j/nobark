[![Build Status](https://travis-ci.org/tools4j/nobark.svg?branch=master)](https://travis-ci.org/tools4j/nobark)
[![Maven Central](https://img.shields.io/maven-central/v/org.tools4j/tools4j-nobark.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22tools4j-nobark%22)
[![Javadocs](http://www.javadoc.io/badge/org.tools4j/tools4j-nobark.svg)](http://www.javadoc.io/doc/org.tools4j/tools4j-nobark)

<b>tools4j-nobark</b> is a library with low latency zero gc data structures and utilities.

##### Conflation queues
A [conflation queue](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/queue/ConflationQueue.html)
is a queue with a safety mechanism to prevent overflow.  Values are enqueued with a conflation key, and if a value with
the same key already resides in the queue then the two values will be "conflated".  Conflation in the simplest case
means that the most recent value survives and replaces older values;  some more advanced implementations support merging
when conflation occurs.

[more information](https://github.com/tools4j/nobark/wiki/Conflation-queues)

[javadoc API](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/queue/package-summary.html)

##### Event loops
The [loop](https://github.com/tools4j/nobark/tree/master/src/main/java/org/tools4j/nobark/loop) package provides
interfaces and classes with simple building blocks for event loops based on executable steps.  A
[Step](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/loop/Step.html) is quite similar
to a Java ``Runnable`` but it returns a value indicating whether substantial work was performed or not --- based on this
value an [IdleStrategy](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/loop/IdleStrategy.html)
allows control over how intensively the event loop occupies the CPU.

[javadoc API](http://javadoc.io/page/org.tools4j/tools4j-nobark/latest/org/tools4j/nobark/loop/package-summary.html)

### Notes

The code makes use of the ``@Contended`` annotation for 
[false sharing](https://mechanical-sympathy.blogspot.com/2011/07/false-sharing.html) prevention.
For best performance, this optimisation needs to be unlocked as follows:
```bash
java -XX:-RestrictContended ...
```

### Gradle
```gradle
dependencies {
    compile 'org.tools4j:tools4j-nobark:1.3'
}
```

### Maven
```xml
<dependency>
    <groupId>org.tools4j</groupId>
    <artifactId>tools4j-nobark</artifactId>
    <version>1.3</version>
</dependency>
```

### API Javadoc
[![Javadocs](http://javadoc.io/badge/org.tools4j/tools4j-nobark.svg)](http://javadoc.io/doc/org.tools4j/tools4j-nobark)

