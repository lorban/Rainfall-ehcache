Rainfall
========

Rainfall is an extensible java framework to implement custom DSL based stress and performance tests in your application.

It has a customisable fluent interface that lets you implement your own DSL when writing tests scenarios, and define your own tests actions and metrics.
Rainfall is open to extensions, three of which are currently in progress,
- Rainfall web is a Yet Another Web Application performance testing library
- Rainfall JCache is a library to test the performance of JSR107 caches solutions
- Rainfall Ehcache is a library to test the performance of Ehcache 2 and 3


Components
----------
[Rainfall-core](https://github.com/aurbroszniowski/Rainfall-core) is the core library containing the key elements of the framework.
 When writing your framework implementation, you must include this library as a dependency.

[Rainfall-web](https://github.com/aurbroszniowski/Rainfall-web) is the Web Application performance testing implementation.

[Rainfall-jcache](https://github.com/aurbroszniowski/Rainfall-jcache) is the JSR107 caches performance testing implementation.

[Rainfall-ehcache](https://github.com/aurbroszniowski/Rainfall-ehcache) is the Ehcache 2.x/3.x performance testing implementation.


Quick start
-----------

Performance tests are written in java

Build the project
-----------------
```
  mvn clean install
```

Use it in your project
----------------------
```
  <dependencies>
    <dependency>
      <groupId>org.rainfall</groupId>
      <artifactId>rainfall-ehcache</artifactId>
      <version>1.0-SNAPSHOT</version>
    </dependency>

    <dependency>
      <groupId>org.rainfall</groupId>
      <artifactId>rainfall-core</artifactId>
      <version>1.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
```
