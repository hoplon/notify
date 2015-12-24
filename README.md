# notify

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
handles that very well. Rather, we are focused her on passing state
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
