# SignalR

## What It Is
SignalR is a library for ASP.NET that simplifies adding real-time web functionality to applications. It automatically chooses the best transport (WebSocket, Server-Sent Events, Long Polling) and provides high-level abstractions for broadcasting messages to connected clients.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SIGNALR ARCHITECTURE                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                         ┌─────────────────────────────────────┐
                         │        SignalR Server                │
                         │  ┌─────────────────────────────┐    │
       Clients           │  │         Hub                 │    │
    ┌──────────┐         │  │  • OnConnected()           │    │
    │ Browser  │◀───────▶│  │  • OnDisconnected()        │    │
    │ (JS SDK) │   WS    │  │  • Custom methods          │    │
    └──────────┘         │  │  • Groups management       │    │
    ┌──────────┐         │  └─────────────────────────────┘    │
    │ Mobile   │◀───────▶│                                     │
    │ (.NET)   │   WS    │  ┌─────────────────────────────┐    │
    └──────────┘         │  │     Connection Manager      │    │
    ┌──────────┐         │  │  • Connection tracking      │    │
    │ Desktop  │◀───────▶│  │  • Group membership         │    │
    │ (C#)     │   WS    │  │  • User mapping             │    │
    └──────────┘         │  └─────────────────────────────┘    │
                         └─────────────────────────────────────┘

TRANSPORT NEGOTIATION:
  1. Client connects to /negotiate endpoint
  2. Server returns supported transports
  3. Client picks best available:
     WebSocket > Server-Sent Events > Long Polling
```

## Hub Concept

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HUB = BIDIRECTIONAL RPC                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Server-side Hub:
  public class ChatHub : Hub
  {
      // Clients can call this
      public async Task SendMessage(string user, string message)
      {
          // Broadcast to all clients
          await Clients.All.SendAsync("ReceiveMessage", user, message);
      }
      
      // Server can call this on specific clients
      // Clients.Group("room1").SendAsync("ReceiveMessage", ...)
      // Clients.User("userId").SendAsync("ReceiveMessage", ...)
      // Clients.Client(connectionId).SendAsync("ReceiveMessage", ...)
  }

Client-side (JavaScript):
  const connection = new signalR.HubConnectionBuilder()
      .withUrl("/chatHub")
      .build();
  
  // Listen for server calls
  connection.on("ReceiveMessage", (user, message) => {
      console.log(`${user}: ${message}`);
  });
  
  // Call server method
  connection.invoke("SendMessage", "Alice", "Hello!");
```

## Server Implementation

```csharp
// Startup.cs / Program.cs
builder.Services.AddSignalR()
    .AddAzureSignalR(connectionString);  // For scaling

app.MapHub<ChatHub>("/chatHub");

// ChatHub.cs
public class ChatHub : Hub
{
    // Called when client connects
    public override async Task OnConnectedAsync()
    {
        var userId = Context.UserIdentifier;
        await Groups.AddToGroupAsync(Context.ConnectionId, "GlobalRoom");
        await base.OnConnectedAsync();
    }

    // Called when client disconnects
    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        await Groups.RemoveFromGroupAsync(Context.ConnectionId, "GlobalRoom");
        await base.OnDisconnectedAsync(exception);
    }

    // Custom method
    public async Task JoinRoom(string roomName)
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, roomName);
        await Clients.Group(roomName).SendAsync("UserJoined", Context.UserIdentifier);
    }

    public async Task SendToRoom(string roomName, string message)
    {
        await Clients.Group(roomName).SendAsync("ReceiveMessage", 
            Context.UserIdentifier, message);
    }
}
```

## Client Implementation

```javascript
// JavaScript/TypeScript client
import * as signalR from "@microsoft/signalr";

class ChatClient {
    constructor() {
        this.connection = new signalR.HubConnectionBuilder()
            .withUrl("/chatHub", {
                accessTokenFactory: () => getAccessToken()
            })
            .withAutomaticReconnect([0, 2000, 5000, 10000, 30000])
            .configureLogging(signalR.LogLevel.Information)
            .build();

        this.setupHandlers();
    }

    setupHandlers() {
        this.connection.on("ReceiveMessage", (user, message) => {
            this.onMessageReceived(user, message);
        });

        this.connection.on("UserJoined", (user) => {
            console.log(`${user} joined the room`);
        });

        this.connection.onreconnecting(error => {
            console.log("Reconnecting...", error);
        });

        this.connection.onreconnected(connectionId => {
            console.log("Reconnected", connectionId);
        });

        this.connection.onclose(error => {
            console.log("Connection closed", error);
        });
    }

    async start() {
        try {
            await this.connection.start();
            console.log("Connected");
        } catch (err) {
            console.error("Connection failed", err);
            setTimeout(() => this.start(), 5000);
        }
    }

    async joinRoom(roomName) {
        await this.connection.invoke("JoinRoom", roomName);
    }

    async sendMessage(roomName, message) {
        await this.connection.invoke("SendToRoom", roomName, message);
    }
}
```

```csharp
// .NET client
using Microsoft.AspNetCore.SignalR.Client;

var connection = new HubConnectionBuilder()
    .WithUrl("https://example.com/chatHub")
    .WithAutomaticReconnect()
    .Build();

connection.On<string, string>("ReceiveMessage", (user, message) =>
{
    Console.WriteLine($"{user}: {message}");
});

await connection.StartAsync();
await connection.InvokeAsync("SendMessage", "Bob", "Hello from .NET!");
```

## Groups

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         GROUP MANAGEMENT                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Groups = Named collections of connections

  ┌─────────────────────────────────────────────────────────────────┐
  │                        SignalR Server                           │
  │                                                                 │
  │   Group: "room-123"           Group: "room-456"                │
  │   ┌─────────────────┐         ┌─────────────────┐              │
  │   │ conn-A          │         │ conn-C          │              │
  │   │ conn-B          │         │ conn-D          │              │
  │   └─────────────────┘         │ conn-E          │              │
  │                               └─────────────────┘              │
  │                                                                 │
  │   conn-A can be in multiple groups!                            │
  └─────────────────────────────────────────────────────────────────┘

USE CASES:
  • Chat rooms
  • Game lobbies
  • Document collaboration
  • Notification channels
  
API:
  await Groups.AddToGroupAsync(connectionId, "room-123");
  await Groups.RemoveFromGroupAsync(connectionId, "room-123");
  await Clients.Group("room-123").SendAsync("Message", content);
  await Clients.GroupExcept("room-123", excludedIds).SendAsync(...);
```

## Scaling with Azure SignalR Service

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SCALING CHALLENGE                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PROBLEM: Multiple server instances don't share connections

  Client A ──▶ Server 1 (has Client A's connection)
  Client B ──▶ Server 2 (has Client B's connection)
  
  Server 1 can't send to Client B!

SOLUTION 1: REDIS BACKPLANE
  ┌──────────┐     ┌───────────────┐     ┌──────────┐
  │ Server 1 │◀───▶│  Redis Pub/Sub │◀───▶│ Server 2 │
  └──────────┘     └───────────────┘     └──────────┘
  
  Messages published to Redis, all servers receive

SOLUTION 2: AZURE SIGNALR SERVICE (Recommended)
  ┌─────────────────────────────────────────────────────────────────┐
  │                                                                 │
  │  Clients ──WebSocket──▶ Azure SignalR Service ◀── App Servers  │
  │                              │                                  │
  │                    ┌─────────┴─────────┐                       │
  │                    │ Manages all       │                       │
  │                    │ connections       │                       │
  │                    │ centrally         │                       │
  │                    └───────────────────┘                       │
  └─────────────────────────────────────────────────────────────────┘
  
  App servers don't hold connections directly!
  
CONFIGURATION:
  services.AddSignalR()
      .AddAzureSignalR("Endpoint=https://xxx.service.signalr.net;...");
```

## Authentication

```csharp
// Server: Require authentication
[Authorize]
public class ChatHub : Hub
{
    public override Task OnConnectedAsync()
    {
        // Access authenticated user
        var userId = Context.UserIdentifier;  // From ClaimTypes.NameIdentifier
        var name = Context.User?.Identity?.Name;
        
        return base.OnConnectedAsync();
    }
}

// Client: Send token
const connection = new signalR.HubConnectionBuilder()
    .withUrl("/chatHub", {
        accessTokenFactory: () => {
            return localStorage.getItem("jwt_token");
        }
    })
    .build();
```

## Streaming

```csharp
// Server: Stream data to client
public async IAsyncEnumerable<int> StreamCounter(int count, int delay)
{
    for (var i = 0; i < count; i++)
    {
        yield return i;
        await Task.Delay(delay);
    }
}

// Client: Receive stream
connection.stream("StreamCounter", 10, 500)
    .subscribe({
        next: (item) => console.log(item),
        complete: () => console.log("Stream complete"),
        error: (err) => console.error(err)
    });

// Server: Receive stream from client
public async Task UploadStream(IAsyncEnumerable<string> stream)
{
    await foreach (var item in stream)
    {
        Console.WriteLine(item);
    }
}
```

## When to Use

✅ **Good for:**
- Real-time dashboards
- Chat applications
- Collaborative editing
- Live notifications
- Multiplayer games
- Stock tickers

❌ **Consider alternatives for:**
- Simple REST APIs
- Fire-and-forget messaging (use queues)
- Offline-first apps
- High-throughput event streaming (use Kafka)

## Trade-offs

| Pros | Cons |
|------|------|
| Automatic transport selection | .NET ecosystem focus |
| Simple programming model | Memory per connection |
| Built-in reconnection | Scaling requires backplane |
| Azure managed option | Learning curve for groups |

## SignalR vs Alternatives

```
┌────────────────────┬───────────────────────────────────────────────────────────────────┐
│ Technology         │ Best For                                                          │
├────────────────────┼───────────────────────────────────────────────────────────────────┤
│ SignalR            │ .NET apps, Azure ecosystem, bidirectional RPC                     │
│ Socket.io          │ Node.js apps, similar abstraction                                 │
│ Raw WebSocket      │ Maximum control, cross-platform, lower-level                      │
│ Server-Sent Events │ Server-to-client only, simpler                                    │
│ gRPC streaming     │ Service-to-service, strongly typed                                │
└────────────────────┴───────────────────────────────────────────────────────────────────┘
```

## Used In (This Guide)

- **Microsoft Teams (Q1)**: Real-time messaging
- **Collaborative Editing (Q10)**: Document sync
- **Live Dashboard (Q20)**: Real-time metrics
- **Notifications (Q6)**: Push notifications
