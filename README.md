
[![Build Status](https://travis-ci.org/scalytica/clammyscan.svg?branch=master)](https://travis-ci.org/scalytica/clammyscan)
# ClammyScan

There isn't really all that much to it. The Play-ReactiveMongo plugin, which this library depends on, comes with a gridfsBodyParser that allows streaming file uploads directly into MongoDB. ClammyScan implements its own BodyParser, that will both scan the file stream with clamd (over TCP using INSTREAM) and save it to MongoDB. If the file contains a virus or is otherwise infected, it is removed from GridFS...and returns an HTTP NotAcceptable. If the file is OK, the Controller will have the file part available for further processing in the request.

There are plans to improve on this library to make it more generic and usable. For example allowing the use of other mechanisms for file persistence than reactive-mongo. And and option that just handles virus-scanning before entering the Controller.

### Installation

Add the following repository to your build.sbt file:

```scala
resolvers += "JCenter" at "http://jcenter.bintray.com/"
```
And the dependency for the ClammyScan library:

```scala
libraryDependencies += "net.scalytica" %% "clammyscan" % "0.19"
```

### Configuration

ClammyScan has some configurable parameters. At the moment the configurable parameters are as follows, and should be located in the application.conf file:

```bash
# ClammyScan configuration
# ------------------------
clammyscan {
  clamd {
    host="localhost"
    #host="clamserver"
    port="3310"
    # Timeout is in milliseconds, where 0 means infinite. (See clamd documentation)
    timeout="0"
  }
  gridfs {
    allowDuplicateFiles=false
  }
  # Defaults to true
  removeInfected=true
  # Defaults to true, but will be treated as false if failOnError=true
  removeOnError=true
  # Defaults to false...if set to true it will also set removeOnError=false
  failOnError=false
  # Disables the clamd scan process and just handle the upload. Defaults to false.
  scanDisabled=false
  # A regex for validating the filename of the file to be uploaded. Will allow anything if not set.
  validFilenameRegex="""(.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)"""
}
```

The properties should be fairly self-explanatory.

### Usage

A minimal controller could look something like this:

```scala
object Application extends Controller with MongoController with ClammyBodyParsers {
  
  def gfs = GridFS(db)
  
  def upload(filename: String) = Action.async(scanAndParseAsGridFS(gfs)) { implicit request =>
    futureGridFSFile.map(file => {
      logger.info(s"Saved file with name ${file.filename}")
      Ok
    }).recover {
      case e: Throwable => InternalServerError(Json.obj("message" -> e.getMessage))
    }
  }
}
```
It is also possible to, optionally, specify any additional metadata to use in GridFS for the saved file. For example, if you have a few request parameters that need to be set, this can be done by passing them to the body parser.

```scala
object Application extends Controller with MongoController with ClammyBodyParsers {

  def gfs = GridFS(db)
  
  def upload(param1: String, param2: String, filename: String) = Action.async(scanAndParseAsGridFS(gfs, Map[String, BSONValue]("param1" -> BSONString(param1), "param2" -> BSONString(param2)))) { implicit request =>
    futureGridFSFile.map(file => {
      logger.info(s"Saved file with name ${file.filename}")
      Ok
    }).recover {
      case e: Throwable => InternalServerError(Json.obj("message" -> e.getMessage))
    }
  }
}
```

There are a couple of other body parsers in addition to the scanAsGridFS one shown above. ```scanOnly```is a convenience that just scans your input stream and returns a result without persisting the file in any way. ```scanAndParseAsTempFile``` has the same sort of behaviour as ```scanAndParseAsGridFS```, but as the name implies creates a temp file instead of writing to GridFS.

### Building and Testing

Currently the tests depend on the precense of a clamd instance running. For local testing, change the configuration in conf/application.conf to point to a running installation.
