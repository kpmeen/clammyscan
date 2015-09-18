[![License](http://img.shields.io/:license-mit-blue.svg)](http://scalytica.mit-license.org)
[![Build Status](https://api.shippable.com/projects/54971a6ad46935d5fbc0c29f/badge?branchName=master)](https://app.shippable.com/projects/54971a6ad46935d5fbc0c29f/builds/latest)   [![Join the chat at https://gitter.im/scalytica/clammyscan](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scalytica/clammyscan?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**_Currently undergoing some major changes..._**

# ClammyScan

There isn't really all that much to it. ClammyScan implements its own BodyParser, that can scan incoming files with clamd (over TCP using INSTREAM). If the file contains a virus or is otherwise infected, a HTTP NotAcceptable is returned with a message explaining why. If the file is OK, the Controller will have the file part available for further processing in the request.

### Installation

Add the following repository to your build.sbt file:

```scala
resolvers += "JCenter" at "http://jcenter.bintray.com/"
```
And the dependency for the ClammyScan library:

```scala
libraryDependencies += "net.scalytica" %% "clammyscan" % "0.20"
```

### Configuration

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

### Usage

 ```scanOnly```is a convenience that just scans your input stream and returns a result without persisting the file in any way. ```scanAndParseAsTempFile``` will, as the name implies, create a temp file available for later processing in the controller.

### Building and Testing

Currently the tests depend on the presence of a clamd instance running. For local testing, change the configuration in conf/application.conf to point to a running installation.
