[![Join the chat at https://gitter.im/scalytica/clammyscan](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scalytica/clammyscan?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Codacy Badge](https://api.codacy.com/project/badge/grade/4a510cbec8f04bccb849793b5b1c981a)](https://www.codacy.com/app/kp/clammyscan) [![Codacy Badge](https://api.codacy.com/project/badge/coverage/4a510cbec8f04bccb849793b5b1c981a)](https://www.codacy.com/app/kp/clammyscan) [![Build Status](https://api.shippable.com/projects/54971a6ad46935d5fbc0c29f/badge?branch=master)](https://app.shippable.com/projects/54971a6ad46935d5fbc0c29f)
 [ ![Download](https://api.bintray.com/packages/kpmeen/maven/clammyscan/images/download.svg) ](https://bintray.com/kpmeen/maven/clammyscan/_latestVersion)

# ClammyScan

ClammyScan is a Play! Framework Module that enables parallel anti-virus scanning of file upload streams.

Traditionally, AV scanning is handled by some background service running on the machine(s) where files are stored. And files are typically scanned after they've been persisted. If the AV on the file server detects an infected file, it will typically be placed in a quarantine of some sort. In many cases this can cause broken file references and/or data inconsistencies.

With ClammyScan this risk can be reduced since the file is scanned while it's being uploaded. This gives you control more control on how to react when a user tries to upload an infected file.


## Usage

Add the dependency for ClammyScan to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies += "net.scalytica" %% "clammyscan" % "1.1.0"
```

Or you can clone the repository and build from source.

## Configuration

### ClamAV

Please refer to the official ClamAV [documentation](https://www.clamav.net/documents/installing-clamav) to ensure you have a properly configured clamd installation.

### ClammyScan

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



## Available BodyParsers

Currently, the BodyParsers only support `multipart/form-data` based file uploads.

### scan

Takes two arguments. A function for saving the file content in parallel with the AV scan. And a function for handling removal of the file in case it is infected. This is the most powerful of the currently available parsers.

This is also the parser to use if you need to provide custom handling for saving the file.

### scanOnly
Scans your file and returns a result without persisting the file in any way.

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

Scans your file and writes it to a temporary file, available for later processing in the controller.

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

For a full example application using the parsers, please have a look in the [sample](sample) directory.

## Other

In addition to the implemented parsers above, an instance of a ClammyScan parser can execute the following commands against clamd.

### ping

Will just execute the `PING` command against clamd, which will respond with a `PONG`.

### version

Fetches the version string for the connected clamd installation.

### stats

Fetches a string with statistics information from the connected clamd installation.

The official clamav documentation states the following about the response format:

> The exact reply format is subject to changes in future releases.

So there has been no extra effort to properly parse the response into meaningful types.


# Contributing

Any contributions and suggestions are very welcome!

## Building and Testing

Currently the tests depend on the presence of a clamd instance running. For local testing, change the configuration in conf/application.conf to point to a running installation. Alternatively, you can start up a Docker container using the following commands: 

```bash
docker pull kpmeen/docker-clamav

docker run --name clammy -d -p 3310:3310 kpmeen/docker-clamav

# And to keep an eye on the clamd log
docker exec -it clammy tail -300f /var/log/clamav/clamav.log
```


