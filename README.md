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

Another issue is scalability. If all clients poll the server at a rate optimized for 
reasonable responsiveness when active, there will be an excessive number of polls
from inactive clients. This does not scale well. To reduce the amount of traffic,
a decay timer is added which increases the poll delay incrementally over time while
reducing the delay geometrically when notifications are received by the client--much
the way windowing is managed by TCP. Additionally, the decay spec for all clients
is managed by the server, allowing a further increase in poll delays when under load.

## Demos

See [castra-notify-random](https://github.com/hoplon/demos/tree/master/castra-notify-random)
and [castra-notify-chat](https://github.com/hoplon/demos/tree/master/castra-notify-chat)

## Client API

The **notify.notification-rpc/register-notification!** is used to assign a function to a 
keyword defining a type of 
change. It takes two arguments, the keyword and the function. The function passed takes
two argument in turn--the value of the change and the timestamp for when the change
occurred on the server.

The **notify.notification-rpc/poll-server** function is used to request changes from the 
server. Typical usage is:

```
(js/setInterval notify.notification-rpc/poll-server 300)
```

The poll-server function fetches any new changes and, for each change, calls the assigned 
function with the value of the change.

The **notify.notification-rpc/smart-poll** function serves as an alternative to poll-server.
Polling frequency is controlled by the server. 

An input cell, **notify.notification-rpc/loading**, can be used to indicate when castra is loading
data from the server. This can be passed as the last argument to mkremote. Sample display:

```
(div
    :id "loading"
    :fade-toggle loading?
    :css {:display "none"}
    "loading...")
```

The input cell, **notify.notification-rpc/last-id**, holds the id of the last notification processed. 
This needs to be passed in
the the first request passed to a server if that request is made before the server is polled,
as such requests must ensure that there is a session for the client. And creating a session requires
the last-id.

## Server API

The **notify.notification-api/get-max-sessions** function returns the maximum number of sessions.
When exceeded, the session which has not polled for the longest period is dropped.

The **notify.notification-api/set-max-sessions!** function sets the maximum number of sessions.

The **notify.notification-api/get-session-count** function returns the number of sessions.

The **notify.notification-api/identify-session!** function assigns a random UUID as the 
:session-id for the current session, as needed. This provides a unique identifier to every 
session. It takes no arguments and the return value is true, 
allowing it to be used in a :rpc/pre expression in defrpc.

The **notify.notification-api/get-session-id** returns the previously assigned 
session id for the current session. It takes no arguments.

The **notify.notification-api/add-notification!** function adds a server change to the table
of changes to be sent to the clients. This function takes three arguments:
the session-id of the client that should receive the change, the keyword used by the client to
determine how to process the change, and the value of the change.

The **notify.notification-api/make-session!** function initializes a session. This function takes two arguments:
the last-id from the client and the session-id. Call this function when processing a request
which was sent before the initial poll from the client.

The **notify.notification-api/get-decay** function returns the poll decay spec in the form 
[min-delay, max-delay, delay-inc], where min-delay is the minimum delay between smart client polls,
max-delay is the maximum-delay and delay-inc is the amount the poll delay is incremented with
each poll. On receipt of one or more notifications, the client halves the poll delay.

The **notify.notification-api/set-decay** function updates the poll decay spec.
It takes 3 arguments: min-delay, max-delay and delay-inc. With each smart poll request,
the client passes its decay spec. If this differs from the server's decay spec, a notification
with the new spec is sent to the client. The set-decay function thus controls the poll delay
for all clients, using eventual consistency.

## Change Log

**0.1.1** A second poll mechanism was added to reduce the amount of polling when notifications
for that client are infrequent.

**0.1.0** When there are too many sessions (the default is 1,000),
the older sessions are dropped.

This was a major change, breaking backward compatibility, as session data
is now kept in a priority-map.

**0.0.2** Fixed the release process. The manifest 
was missing. Added task deploy-release to boot.build.

**0.0.1** Initial release.
