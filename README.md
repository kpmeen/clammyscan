
# ClammyScan

Long story short... working on a project using Play Framework with reactive-mongo (using the play reactive-mongo plugin), when a completely overlooked and forgotten requirement popped up. Namely, it was necessary to scan files being uploaded with a virus scanner to ensure only clean files are saved. So, I hacked this little library together.

### What does it do?

There isn't really all that much to it. The Play Reactive Mongo plugin, which this library depends on, comes with a gridfsBodyParser that allows streaming file uploads directly into MongoDB. ClammyScan implements its own BodyParser, that will both scan the file stream with clamd (over TCP using INSTREAM) and save it to MongoDB. If the file contains a virus or is otherwise infected, it is removed from GridFS...and returns an HTTP NotAcceptable. If the file is OK, the Controller will get the same file part type as from the Play reactive mongo plugin.
