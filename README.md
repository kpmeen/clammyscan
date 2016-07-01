[![Join the chat at https://gitter.im/scalytica/clammyscan](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scalytica/clammyscan?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Codacy Badge](https://api.codacy.com/project/badge/grade/4a510cbec8f04bccb849793b5b1c981a)](https://www.codacy.com/app/kp/clammyscan) [![Codacy Badge](https://api.codacy.com/project/badge/coverage/4a510cbec8f04bccb849793b5b1c981a)](https://www.codacy.com/app/kp/clammyscan) [![Build Status](https://api.shippable.com/projects/54971a6ad46935d5fbc0c29f/badge?branch=master)](https://app.shippable.com/projects/54971a6ad46935d5fbc0c29f)

# ClammyScan

ClammyScan is a Play! Framework Module that enables anti-virus scanning of streaming file uploads. It does this by exposing a few `BodyParser`s that can be used in your controller actions.

If the file is infected, a `HTTP 406 NotAcceptable` is returned with a message describing the reason. If the file is OK, the Controller will have the file part available for further processing in the request.

### Why?
Traditionally, AV scanning is handled by some service running on the machine(s) where files are stored. And files are typically scanned _after_ they've been persisted. In many cases this can result in data corruption. The reason for this is that the services that process the file uploads write metadata, location references, etc to some database. If the AV on the file server detects an infected file, it will typically be placed in a quarantine of some sort. But the file service and database are unaware of what just happened. So now the DB contains broken file references. 

With ClammyScan this can be avoided since the file is scanned while it's being uploaded. If any infections are found, the system is able to react in a way that avoid broken DB references etc. And the users will be notified that they are trying to upload an infected file.


## Usage

Add the following repository to your build.sbt file:

```scala
resolvers += "JCenter" at "http://jcenter.bintray.com/"
```
And the dependency for the ClammyScan library:

```scala
libraryDependencies += "net.scalytica" %% "clammyscan" % "1.0.1"
```

## Configuration

### ClamAV configuration
Please refer to the official ClamAV documentation.

### ClammyScan configuration
ClammyScan has some configurable parameters. At the moment the configurable parameters are as follows, and should be located in the application.conf file:

```bash
# ClammyScan configuration
# ------------------------
play.modules.enabled += "net.scalytica.clammyscan.ClammyScanModule"

clammyscan {
  clamd {
    host="localhost"
    port="3310"
    # Duration before connection timeout, where 0 means infinite.
    # (See clamd documentation)
    timeout=0
  }
  # Defaults to true
  removeInfected=true
  # Defaults to true, but will be treated as false if failOnError=true
  removeOnError=true
  # Defaults to false...if set to true it will also set removeOnError=false
  failOnError=false
  # Disables the clamd scan process and just handle the upload.
  # Defaults to false.
  scanDisabled=false
  # A regex for validating the filename of the file to be uploaded.
  # Will allow anything if not set.
  validFilenameRegex="""(.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)"""
}
```

The properties should be fairly self-explanatory.

## Available BodyParsers

### scan

Takes two arguments. A function for saving the file content in parallel with the AV scan. And a function for handling removal of the file in case it is infected. This is the most powerful of the available parsers.

This is also the parser to use if you need to provide custom handling for saving the file.

### scanOnly
Is a convenience that just scans your input stream and returns a result without persisting the file in any way.

```scala
  def scanFile = Action(clammyScan.scanOnly) { request =>
    request.body.files.head.ref._1 match {
      case Left(err) =>
        err match {
          case vf: VirusFound =>
            NotAcceptable(Json.obj("message" -> vf.message))

          case ce =>
            logger.error(s"An unknown error occured: ${ce.message}")
            InternalServerError(Json.obj("message" -> ce.message))
        }

      case Right(ok) =>
        Ok(Json.obj("message" -> "file is clean"))
    }
  }
```

### scanWithTmpFile

Will, as the name implies, create a temp file available for later processing in the controller.

```scala
  def scanTempFile = Action(clammyScan.scanWithTmpFile) { request =>
    request.body.files.headOption.map { f =>
      val fname = f.ref._2.get.file.getName
      f.ref._1 match {
        case Left(err) =>
          Ok(Json.obj("message" -> s"$fname scan result was: ${err.message}"))

        case Right(fileOk) =>
          Ok(Json.obj("message" -> s"$fname uploaded successfully"))
      }
    }.getOrElse {
      BadRequest("could not find attached file")
    }
  }
```



For a full example of how to use the parsers, please have a look at the play application in the [sample](sample) directory.


# Contributing

Any contributions and suggestions are very welcome!

## Building and Testing

Currently the tests depend on the presence of a clamd instance running. For local testing, change the configuration in conf/application.conf to point to a running installation. Alternatively, you can start up the following Docker container: `docker pull kpmeen/docker-clamav`
