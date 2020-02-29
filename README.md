# ClammyScan

Contains libraries for use in akka-streams pipelines and Play! Framework
applications that require AV scanning. 

The streams library contains the akka-streams implementation that handles the
communication with clamd.

The body-parsers library contains a Play! Framework Module that provides access
to body parsers enabling parallel anti-virus scanning of file upload streams.

## Background

Traditionally, AV scanning is handled by some background service running on the
machine(s) where files are stored. And files are typically scanned after they've
been persisted. If the AV on the file server detects an infected file, it will
typically be placed in a quarantine of some sort. In many cases this can cause
broken file references and/or data inconsistencies.

With the ClammyScan body parsers this risk can be reduced since the file is scanned while it's
being uploaded. This gives you control more control on how to react when a user
tries to upload an infected file.


## Usage

Add the dependency for ClammyScan to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

// See latest version badge above for current release

// Play! Framework body parsers
libraryDependencies += "net.scalytica" %% "clammyscan" % "<version>"

// Akka Streams library that enables streaming data to clamd.
libraryDependencies += "net.scalytica" %% "clammyscan-streams" % "<version>"
```

Or you can clone the repository and build from source.

## Configuration

### ClamAV

Please refer to the official ClamAV
[documentation](https://www.clamav.net/documents/installing-clamav) to ensure
you have a properly configured clamd installation.

### ClammyScan

ClammyScan has some configurable parameters. At the moment the configurable
parameters are defined as follows in the `reference.conf` file. Any overrides
should be located in the play application's `conf/application.conf` file:

```hocon
play.modules.enabled += "net.scalytica.clammyscan.ClammyScanModule"

clammyscan {
  clamd {
    host = "localhost"
    host = ${?CLAM_HOST}
    port = "3310"
    port = ${?CLAM_PORT}
    # Duration before connection timeout, where 0 means infinite.
    # (See clamd documentation)
    timeout = 0
    # Configure to be the same size as the clamd StreamMaxLength parameter.
    # (See clamd documentation) 
    streamMaxLength = 25m
  }
  # Defaults to true
  removeInfected = true
  # Defaults to true, but will be treated as false if failOnError=true
  removeOnError = true
  # Defaults to false...if set to true it will also set removeOnError=false
  failOnError = false
  # Disables the clamd scan process and just handle the upload.
  # Defaults to false.
  scanDisabled = false
  # A regex for validating the filename of the file to be uploaded.
  # Will allow anything if not set.
  validFilenameRegex = """(.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)"""
}
```

## Available BodyParsers

There are `BodyParser`s implemented for both `multipart/form-data` and direct,
chunked, file uploads. To see an example application using most of the parsers,
please have a look in the [sample](sample) directory.


### Multipart parsers

#### scan

Takes two arguments. A function for saving the file content in parallel with the
AV scan. And a function for handling removal of the file in case it is infected.
This is the most powerful of the currently available parsers.

This is also the parser to use if you need to provide custom handling for saving
the file.

#### scanOnly
Scans your file and returns a result without persisting the file in any way.

```scala
  def scanFile: Action[ClamMultipart[Unit]] =
    Action(clammyScan.scanOnly) { request =>
      request.body.files.head.ref.scanResponse match {
        case vf: VirusFound =>
          NotAcceptable(Json.obj("message" -> vf.message))

        case ce: ClamError =>
          logger.error(s"An unknown error occurred: ${ce.message}")
          InternalServerError(Json.obj("message" -> ce.message))

        case FileOk =>
          Ok(Json.obj("message" -> "file is clean"))
      }
    }
```

#### scanWithTmpFile

Scans your file and writes it to a temporary file, available for later
processing in the controller.

```scala
  def scanTempFile: Action[ClamMultipart[Files.TemporaryFile]] =
    Action(clammyScan.scanWithTmpFile) { request =>
      request.body.files.headOption.map { f =>
        val fname = f.ref.maybeRef.get.path.getFileName
        f.ref.scanResponse match {
          case err: ClamError =>
            Ok(Json.obj("message" -> s"$fname scan result was: ${err.message}"))

          case FileOk =>
            Ok(Json.obj("message" -> s"$fname uploaded successfully"))
        }
      }.getOrElse {
        BadRequest("could not find attached file")
      }
    }
```

### Direct / Chunked parser

#### directScan

Similar to the `scan` parser for multipart files. It takes two arguments. A
function for saving the file content in parallel with the AV scan. And a
function for handling removal of the file in case it is infected. This is the
most powerful of the currently available parsers.

This is also the parser to use if you need to provide custom handling for saving
the file.

#### directScanWithTmpFile

Scans your file and writes it to a temporary file, available for later
processing in the controller.

```scala
  def directTempFile: Action[ScannedBody[Files.TemporaryFile]] =
    Action(clammyScan.directScanWithTmpFile) { request =>
      request.body.maybeRef.map { ref =>
        val fname = ref.path.getFileName
        request.body.scanResponse match {
          case err: ClamError =>
            Ok(Json.obj("message" -> s"$fname scan result was: ${err.message}"))

          case FileOk =>
            Ok(Json.obj("message" -> s"$fname uploaded successfully"))
        }
      }.getOrElse {
        Ok(Json.obj("message" -> s"Request did not contain any files"))
      }
    }
```

#### directScanOnly

Scans your file and returns a result without persisting the file in any way.

```scala
  def directScanFile: Action[ScannedBody[Unit]] =
    Action(clammyScan.directScanOnly) { request =>
      request.body.scanResponse match {
        case vf: VirusFound =>
          NotAcceptable(Json.obj("message" -> vf.message))

        case ce: ClamError =>
          logger.error(s"An unknown error occurred: ${ce.message}")
          InternalServerError(Json.obj("message" -> ce.message))

        case FileOk =>
          Ok(Json.obj("message" -> "file is clean"))
      }
    }
```

## Supported clamd commands

In addition to the implemented parsers above, an instance of a ClammyScan parser
can execute the following commands against clamd.

### ping

Will just execute the `PING` command against clamd, which will respond with a
`PONG`.

### version

Fetches the version string for the connected clamd installation.

### stats

Fetches a string with statistics information from the connected clamd
installation.

The official clamav documentation states the following about the response
format:

> The exact reply format is subject to changes in future releases.

So there has been no extra effort to properly parse the response into meaningful
types.


# Contributing

Any contributions and suggestions are very welcome!

## Building and Testing

Currently the tests depend on the presence of a clamd instance running. For
local testing, change the configuration in `sample/conf/application.conf` to
point to a running installation.

The recommended way of starting clamd for development is to execute the
`dockerClamAV.sh` script.

```bash
# start docker-clamav
./dockerClamAV.sh start

# stop docker-clamav
./dockerClamAV.sh stop

# reset docker-clamav (stop, remove container, pull latest and start)
./dockerClamAV.sh reset

# clean docker-clamav (remove container)
./dockerClamAV.sh clean
```

Alternatively, you can start up a Docker container using
the following commands:

```bash
docker pull registry.gitlab.com/kpmeen/docker-clamav

# Note the "-m 2M" argument. It sets the max TCP stream size for clamd to 2Mb
# This is required for a few of the test cases that verify correct behaviour
# when this limit is crossed.
docker run --name clammy -d -p 3310:3310 registry.gitlab.com/kpmeen/docker-clamav -m 2M

# And to keep an eye on the clamd log
docker exec -it clammy tail -300f /var/log/clamav/clamav.log
```


