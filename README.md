[![Join the chat at https://gitter.im/scalytica/clammyscan](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scalytica/clammyscan?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Codacy Badge](https://api.codacy.com/project/badge/grade/4a510cbec8f04bccb849793b5b1c981a)](https://www.codacy.com/app/kp/clammyscan) [![Codacy Badge](https://api.codacy.com/project/badge/coverage/4a510cbec8f04bccb849793b5b1c981a)](https://www.codacy.com/app/kp/clammyscan) [![Build Status](https://img.shields.io/shippable/54971a6ad46935d5fbc0c29f.svg)](https://app.shippable.com/projects/54971a6ad46935d5fbc0c29f/builds/latest)

# ClammyScan

ClammyScan largely consists of a trait defining a few `BodyParser`s. These are made that incoming files can be scanned with clamd (over TCP using INSTREAM). If the file contains a virus or is otherwise infected, a HTTP NotAcceptable is returned with a message explaining why. If the file is OK, the Controller will have the file part available for further processing in the request.

### Why?
AV scanning is handled by some service running on the machine where files are stored. Files are typically scanned _after_ they've been persisted, and often services that process these files write metadata and location references to a DB. If the AV service should detect an infected file, this file will mostly be put in a quarantine of some sort while the service itself is unaware of what happened. The DB all of a sudden contains broken references. With ClammyScan this can be avoided since the file is scanned while it's being uploaded. Users uploading infected files will be notified about the infected files when they are discovered, instead of wondering why the file can't be found.


## Usage

Add the following repository to your build.sbt file:

```scala
resolvers += "JCenter" at "http://jcenter.bintray.com/"
```
And the dependency for the ClammyScan library:

```scala
libraryDependencies += "net.scalytica" %% "clammyscan" % "0.22"
```

## Configuration

### ClamAV configuration
Please refer to the official ClamAV documentation.

### ClammyScan configuration
ClammyScan has some configurable parameters. At the moment the configurable parameters are as follows, and should be located in the application.conf file:

```bash
# ClammyScan configuration
# ------------------------
clammyscan {
  clamd {
    host="localhost"
    port="3310"
    # Timeout is in milliseconds, where 0 means infinite. (See clamd documentation)
    timeout="0"
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

## Available BodyParsers

* `scan` takes two arguments. A function for saving the file content in parallel with the AV scan. And a function for handling removal of the file in case it is infected. This is the most powerful of the available `BodyParser`s.

* `scanOnly`is a convenience that just scans your input stream and returns a result without persisting the file in any way. 
 
* `scanAndParseAsTempFile` will, as the name implies, create a temp file available for later processing in the controller.

## Building and Testing

Currently the tests depend on the presence of a clamd instance running. For local testing, change the configuration in conf/application.conf to point to a running installation. The repository also contains a simple script that will start a Docker image containing ClamAV.
