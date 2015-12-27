# notify
[![Clojars Project](http://clojars.org/hoplon/notify/latest-version.svg)](http://clojars.org/hoplon/notify)

[Castra](https://github.com/hoplon/castra)
is a great RPC service for developing applications in
[Hoplon](https://github.com/hoplon/hoplon),
making it easy for web clients to access the server.

But the strength of Castra is also its weakness, as it is
too easy for developers to simply query the server about
its state. And in larger applications it is natural to break
the server state into multiple parts so that the client can 
query only the parts it is currently interested in.

Unfortunately, easy does not mean scalable. Clients that need to 
stay in sync with the various parts of the server end up polling 
each of these parts. Successful applications end up with many
many clients. And the net result is the same as a denial of service
attack.

A chat application is a good example here. We are not talking about
sending the response to a request from a client, as Castra already
handles that very well. Rather, we are focused here on passing state
changes to a client which resulted from activity by other clients.

The notify project is a first step in addressing this. Only changes
to the server state are passed to clients, which means a significant 
reduction in the amount of data transfered after the initial interchange.
And because only changes are passed, a single query can be used by
a client to fetch all the changes of interest that the client has not
yet seen.

Of course, we now have the issue that a client might not get all the
server updates that it needs. Notify manages this by a per-client sequence
number. Every change for every client has a consecutively assigned id.
All unacknowledged changes are sent with each query from a client, but that query
contains the number of the last processed change. So once a change is acknowledged
by a client, it is dropped by the server. Meanwhile the client ignores any 
incoming change with a sequence number not greater than what it has already processed.
This simple protocol provides the necessary robustness while minimizing the amount
of traffic passed between client and server dealing with changes of state.

## Demos

See [castra-simple2](https://github.com/hoplon/demos/tree/master/castra-simple2)

## Client API

The client-side API for notify is just two functions: register-notification! and poll-server.

The **notify.notification-rpc/register-notification!** is used to assign a function to a 
keyword defining a type of 
change. It takes two arguments, the keyword and the function. The function passed takes
only a single argument--the value of the change.

The **notify.notification-rpc/poll-server** function is used to request changes from the 
server. Typical usage is:

```
(js/setInterval notify.notification-rpc/poll-server 300)
```

The poll-server function fetches any new changes and, for each change, calls the assigned 
function with the value of the change.

## Server API

The server-side API for notify is just three functions: identify-session!, get-session-id
and add-notification!.

The **notify.notification-api/identify-session!** function assigns a random UUID as the 
:session-id for the current session, as needed. This provides a unique identifier to every 
session. It takes no arguments and the return value is true, allowing it to be used in a :rpc/pre expression in defrpc.

The **notify.notification-api/get-session-id** returns the previously assigned 
session id for the current session. It takes no arguments.

The **notify.notification-api/get-session-id** function adds a server change to the table
of changes to be sent to the clients. This function takes three arguments:
the session-id of the client that should receive the change, the keyword used by the client to
determine how to process the change, and the value of the change.

## Change Log

**0.0.1** Initial release.

**0.0.2** Fixed the release process. The manifest 
was missing. Added task deploy-release to boot.build.