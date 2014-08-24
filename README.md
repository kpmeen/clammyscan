
# ClammyScan

There isn't really all that much to it. The Play Reactive Mongo plugin, which this library depends on, comes with a gridfsBodyParser that allows streaming file uploads directly into MongoDB. ClammyScan implements its own BodyParser, that will both scan the file stream with clamd (over TCP using INSTREAM) and save it to MongoDB. If the file contains a virus or is otherwise infected, it is removed from GridFS...and returns an HTTP NotAcceptable. If the file is OK, the Controller will get the same file part type as from the Play reactive mongo plugin.

## Installation

Add the following repository to your build.sbt file:

```scala
resolvers += "JCenter" at "http://jcenter.bintray.com/"
```
And the dependency for ClammyScan:

```scala
libraryDependencies += "net.scalytica" %% "clammyscan" % "0.1"
```



## Configuration

ClammyScan has some configurable parameters. At the moment the configurable parameters are as follows, and should be located in the application.conf file:

```
# ClammyScan configuration
# ~~~~~
clammyscan {
  clamd {
    host="clamserver"
    port="3310"
    timeout="0" # Timeout is in milliseconds, where 0 means infinite. (See clamd documentation)
  }
}
```
The properties should be fairly self-explanatory.



