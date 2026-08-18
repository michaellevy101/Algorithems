# Raft Consensus Algorithm

## What It Is
Raft is a consensus algorithm that allows a cluster of servers to agree on a sequence of values (log entries) even if some servers fail. It's designed to be understandable and is used in etcd, Consul, and CockroachDB.

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         DISTRIBUTED CONSENSUS PROBLEM                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

5 servers need to agree on a value. What if:
  • Network partitions occur?
  • Servers crash and restart?
  • Messages are delayed or lost?

REQUIREMENTS:
  • Safety: All servers agree on the same value (no conflicts)
  • Liveness: System makes progress despite failures
  • Fault tolerance: Works with majority (N/2 + 1) servers alive
```

## How Raft Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         RAFT OVERVIEW                                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

THREE ROLES:
  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
  │    LEADER    │    │   FOLLOWER   │    │  CANDIDATE   │
  │              │    │              │    │              │
  │ • Handles    │    │ • Passive    │    │ • Requesting │
  │   all client │    │ • Responds   │    │   votes to   │
  │   requests   │    │   to leader  │    │   become     │
  │ • Replicates │    │ • Redirects  │    │   leader     │
  │   log        │    │   clients    │    │              │
  └──────────────┘    └──────────────┘    └──────────────┘

NORMAL OPERATION:
  ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
  │ Server1 │     │ Server2 │     │ Server3 │     │ Server4 │     │ Server5 │
  │ LEADER  │     │FOLLOWER │     │FOLLOWER │     │FOLLOWER │     │FOLLOWER │
  └────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘
       │               │               │               │               │
       │──── AppendEntries ───────────▶│               │               │
       │──── AppendEntries ────────────────────────────▶               │
       │──── AppendEntries ───────────────────────────────────────────▶│
       │               │               │               │               │
       │◀──── OK ──────│               │               │               │
       │◀──── OK ──────────────────────│               │               │
       │               │               │               │               │
       │  Committed when majority respond                              │
```

## Leader Election

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LEADER ELECTION                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

TRIGGER: Follower doesn't hear from leader within election timeout (150-300ms)

PROCESS:
  1. Follower becomes CANDIDATE
  2. Increments current term
  3. Votes for itself
  4. Sends RequestVote to all servers

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Term 1: Server1 is leader                                                              │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐                                           │
│  │ Server1 │     │ Server2 │     │ Server3 │                                           │
│  │ LEADER  │     │FOLLOWER │     │FOLLOWER │                                           │
│  └────┬────┘     └─────────┘     └─────────┘                                           │
│       │                                                                                 │
│       X  Server1 crashes!                                                              │
│                                                                                         │
│  Election timeout on Server2...                                                        │
│                                                                                         │
│  Term 2: Server2 becomes candidate                                                     │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐                                           │
│  │ Server1 │     │ Server2 │     │ Server3 │                                           │
│  │  DOWN   │     │CANDIDATE│     │FOLLOWER │                                           │
│  └─────────┘     └────┬────┘     └────┬────┘                                           │
│                       │               │                                                 │
│                       │─RequestVote──▶│                                                 │
│                       │◀───Vote Yes───│                                                 │
│                       │               │                                                 │
│                   Majority votes!                                                       │
│                       │                                                                 │
│  Term 2: Server2 becomes leader                                                        │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐                                           │
│  │ Server1 │     │ Server2 │     │ Server3 │                                           │
│  │  DOWN   │     │ LEADER  │     │FOLLOWER │                                           │
│  └─────────┘     └─────────┘     └─────────┘                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

VOTING RULES:
  • Each server votes once per term
  • Vote for candidate with log at least as up-to-date as yours
  • First-come-first-served within a term
```

## Log Replication

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LOG REPLICATION                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Leader receives client request: SET x = 5

1. APPEND to leader's log (uncommitted)
2. SEND AppendEntries RPC to followers
3. WAIT for majority to acknowledge
4. COMMIT entry (apply to state machine)
5. RESPOND to client
6. Notify followers to commit

LOG STRUCTURE:
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Index:    │  1  │  2  │  3  │  4  │  5  │  6  │                                       │
│  Term:     │  1  │  1  │  1  │  2  │  2  │  2  │                                       │
│  Command:  │x=1  │y=2  │x=3  │y=5  │x=7  │z=1  │                                       │
│            └─────┴─────┴─────┴─────┴─────┴─────┘                                       │
│                              ▲                                                          │
│                         commitIndex                                                     │
│                                                                                         │
│  Entries 1-5 are committed (majority has them)                                         │
│  Entry 6 is pending (waiting for majority)                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

APPENDING ENTRIES:
  Leader sends:
    {
      term: 2,
      leaderId: "server1",
      prevLogIndex: 5,
      prevLogTerm: 2,
      entries: [{term: 2, command: "z=1"}],
      leaderCommit: 5
    }

  Follower checks:
    • Does log[prevLogIndex].term == prevLogTerm?
    • Yes → Append entries, return success
    • No → Return failure, leader decrements prevLogIndex and retries
```

## Safety Guarantees

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         RAFT SAFETY PROPERTIES                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. ELECTION SAFETY
   At most one leader per term
   (Each server votes once per term)

2. LEADER APPEND-ONLY
   Leader never overwrites or deletes log entries
   Only appends new entries

3. LOG MATCHING
   If two logs have entry with same index and term,
   all preceding entries are identical

4. LEADER COMPLETENESS
   If entry is committed in term T,
   all leaders in terms > T will have that entry

5. STATE MACHINE SAFETY
   If a server applies entry at index N,
   no other server applies different entry at N
```

## Handling Network Partitions

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         NETWORK PARTITION                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

5 servers, partition splits [A,B] from [C,D,E]:

  Partition 1: [A, B]          │  Partition 2: [C, D, E]
  ┌─────────┐ ┌─────────┐      │  ┌─────────┐ ┌─────────┐ ┌─────────┐
  │    A    │ │    B    │      │  │    C    │ │    D    │ │    E    │
  │ LEADER  │ │FOLLOWER │      │  │FOLLOWER │ │FOLLOWER │ │FOLLOWER │
  │ Term 1  │ │ Term 1  │      │  │ Term 1  │ │ Term 1  │ │ Term 1  │
  └─────────┘ └─────────┘      │  └─────────┘ └─────────┘ └─────────┘
                               │
  A can't commit (no majority) │  C times out, becomes candidate
                               │  C elected leader (3/5 majority)
                               │
  ┌─────────┐ ┌─────────┐      │  ┌─────────┐ ┌─────────┐ ┌─────────┐
  │    A    │ │    B    │      │  │    C    │ │    D    │ │    E    │
  │ LEADER  │ │FOLLOWER │      │  │ LEADER  │ │FOLLOWER │ │FOLLOWER │
  │ Term 1  │ │ Term 1  │      │  │ Term 2  │ │ Term 2  │ │ Term 2  │
  └─────────┘ └─────────┘      │  └─────────┘ └─────────┘ └─────────┘
                               │
  Stale! Can't make progress   │  Active! Can commit entries

PARTITION HEALS:
  A sees term 2 > term 1, steps down to follower
  A's uncommitted entries discarded
  A syncs log from new leader C
```

## Implementation

```go
type RaftNode struct {
    currentTerm int
    votedFor    string
    log         []LogEntry
    commitIndex int
    lastApplied int
    
    // Leader state
    nextIndex   map[string]int  // For each follower
    matchIndex  map[string]int  // For each follower
    
    state       State  // Follower, Candidate, Leader
}

type LogEntry struct {
    Term    int
    Command interface{}
}

func (n *RaftNode) RequestVote(args RequestVoteArgs) RequestVoteReply {
    if args.Term < n.currentTerm {
        return RequestVoteReply{Term: n.currentTerm, VoteGranted: false}
    }
    
    if args.Term > n.currentTerm {
        n.currentTerm = args.Term
        n.state = Follower
        n.votedFor = ""
    }
    
    // Check if candidate's log is at least as up-to-date
    lastLogIndex := len(n.log) - 1
    lastLogTerm := n.log[lastLogIndex].Term
    
    logOK := args.LastLogTerm > lastLogTerm ||
             (args.LastLogTerm == lastLogTerm && args.LastLogIndex >= lastLogIndex)
    
    if (n.votedFor == "" || n.votedFor == args.CandidateId) && logOK {
        n.votedFor = args.CandidateId
        return RequestVoteReply{Term: n.currentTerm, VoteGranted: true}
    }
    
    return RequestVoteReply{Term: n.currentTerm, VoteGranted: false}
}
```

## When to Use

✅ **Good for:**
- Distributed databases (leader election)
- Configuration management (etcd, Consul)
- Distributed locks
- Replicated state machines
- Leader election in microservices

❌ **Not ideal for:**
- High write throughput (single leader bottleneck)
- Large data volumes (log can grow)
- Latency-sensitive (round-trip to majority)
- Cross-datacenter (high latency)

## Trade-offs

| Pros | Cons |
|------|------|
| Understandable | Single leader (write bottleneck) |
| Strong consistency | Requires majority available |
| Proven correct | Higher latency than async |
| Widely implemented | Not ideal for geo-distribution |

## Raft vs Paxos

| Feature | Raft | Paxos |
|---------|------|-------|
| Understandability | Easy | Hard |
| Leader | Required | Optional |
| Log structure | Linear | Slots |
| Implementation | Straightforward | Complex |
| Performance | Similar | Similar |

## Used In (This Guide)

- **Distributed Locks (Q41)**: Lock coordination
- **Key-Value Store (Q40)**: Replication
- **Distributed Cache (Q38)**: Cluster membership
- **etcd/Consul**: Service discovery
