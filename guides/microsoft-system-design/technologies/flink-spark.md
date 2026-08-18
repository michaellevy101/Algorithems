# Flink & Spark (Stream Processing)

## What They Are
Apache Flink and Apache Spark are distributed data processing frameworks. Flink is optimized for real-time stream processing, while Spark excels at batch processing with streaming capabilities. Both are used for large-scale data transformations and analytics.

## Flink vs Spark

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPARISON                                                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────┬─────────────────────────────┬─────────────────────────────────────┐
│ Aspect             │ Apache Flink                │ Apache Spark                        │
├────────────────────┼─────────────────────────────┼─────────────────────────────────────┤
│ Primary Model      │ Stream-first                │ Batch-first                         │
│ Streaming          │ True streaming (per-event)  │ Micro-batching                      │
│ Latency            │ Milliseconds                │ Seconds (micro-batch interval)      │
│ State Management   │ Built-in, checkpointed      │ External (requires workarounds)     │
│ Exactly-once       │ Native support              │ Supported with limitations          │
│ Batch Processing   │ Treats batch as bounded     │ Highly optimized                    │
│                    │ stream                      │                                     │
│ SQL Support        │ Flink SQL                   │ Spark SQL (very mature)             │
│ ML Libraries       │ FlinkML (limited)           │ MLlib (comprehensive)               │
└────────────────────┴─────────────────────────────┴─────────────────────────────────────┘

USE FLINK WHEN:
  • Sub-second latency needed
  • Complex event processing (CEP)
  • Stateful stream processing
  • True real-time requirements

USE SPARK WHEN:
  • Batch processing primary
  • Machine learning pipelines
  • SQL analytics
  • Existing Hadoop ecosystem
```

## Apache Flink Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FLINK ARCHITECTURE                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────────────────┐
                    │           Job Manager                   │
                    │  • Coordinates execution                │
                    │  • Schedules tasks                      │
                    │  • Manages checkpoints                  │
                    └───────────────────┬─────────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              │                         │                         │
              ▼                         ▼                         ▼
    ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
    │  Task Manager   │       │  Task Manager   │       │  Task Manager   │
    │  ┌───────────┐  │       │  ┌───────────┐  │       │  ┌───────────┐  │
    │  │ Task Slot │  │       │  │ Task Slot │  │       │  │ Task Slot │  │
    │  │ (1 CPU)   │  │       │  │ (1 CPU)   │  │       │  │ (1 CPU)   │  │
    │  └───────────┘  │       │  └───────────┘  │       │  └───────────┘  │
    │  ┌───────────┐  │       │  ┌───────────┐  │       │  ┌───────────┐  │
    │  │ Task Slot │  │       │  │ Task Slot │  │       │  │ Task Slot │  │
    │  └───────────┘  │       │  └───────────┘  │       │  └───────────┘  │
    └─────────────────┘       └─────────────────┘       └─────────────────┘

DATA FLOW:
  Kafka ──▶ Source ──▶ Map ──▶ KeyBy ──▶ Window ──▶ Sink ──▶ Output
               │           │        │          │
               └───────────┴────────┴──────────┘
                      Distributed execution
```

## Flink Streaming Example

```java
// Java Flink job
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// Enable checkpointing for fault tolerance
env.enableCheckpointing(60000); // Every 60 seconds

DataStream<String> input = env
    .addSource(new FlinkKafkaConsumer<>("events", new SimpleStringSchema(), properties));

DataStream<Alert> alerts = input
    .map(json -> parseEvent(json))
    .keyBy(event -> event.getUserId())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new CountAggregator())
    .filter(count -> count > 100)
    .map(count -> new Alert("High activity: " + count));

alerts.addSink(new FlinkKafkaProducer<>("alerts", new AlertSerializer(), properties));

env.execute("Event Alert Job");
```

```python
# Python (PyFlink)
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.table import StreamTableEnvironment

env = StreamExecutionEnvironment.get_execution_environment()
t_env = StreamTableEnvironment.create(env)

# Define source table (Kafka)
t_env.execute_sql("""
    CREATE TABLE events (
        user_id STRING,
        event_type STRING,
        event_time TIMESTAMP(3),
        WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
    ) WITH (
        'connector' = 'kafka',
        'topic' = 'events',
        'properties.bootstrap.servers' = 'kafka:9092',
        'format' = 'json'
    )
""")

# Windowed aggregation
result = t_env.sql_query("""
    SELECT 
        user_id,
        TUMBLE_START(event_time, INTERVAL '5' MINUTE) AS window_start,
        COUNT(*) AS event_count
    FROM events
    GROUP BY user_id, TUMBLE(event_time, INTERVAL '5' MINUTE)
    HAVING COUNT(*) > 100
""")
```

## Apache Spark Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SPARK ARCHITECTURE                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────────────────┐
                    │           Driver Program                │
                    │  • SparkContext                         │
                    │  • Creates DAG of operations            │
                    │  • Schedules tasks                      │
                    └───────────────────┬─────────────────────┘
                                        │
                    ┌───────────────────┴─────────────────────┐
                    │         Cluster Manager                 │
                    │  (YARN / Kubernetes / Standalone)       │
                    └───────────────────┬─────────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              ▼                         ▼                         ▼
    ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
    │    Executor     │       │    Executor     │       │    Executor     │
    │  ┌───────────┐  │       │  ┌───────────┐  │       │  ┌───────────┐  │
    │  │   Task    │  │       │  │   Task    │  │       │  │   Task    │  │
    │  └───────────┘  │       │  └───────────┘  │       │  └───────────┘  │
    │  ┌───────────┐  │       │  ┌───────────┐  │       │  ┌───────────┐  │
    │  │  Cache    │  │       │  │  Cache    │  │       │  │  Cache    │  │
    │  └───────────┘  │       │  └───────────┘  │       │  └───────────┘  │
    └─────────────────┘       └─────────────────┘       └─────────────────┘

RDD (Resilient Distributed Dataset):
  • Immutable, partitioned collection
  • Lazy evaluation
  • Fault-tolerant (lineage tracking)
```

## Spark Examples

```python
# PySpark Batch Processing
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, avg, window

spark = SparkSession.builder \
    .appName("EventAnalytics") \
    .getOrCreate()

# Read from data lake
events = spark.read.parquet("s3://bucket/events/")

# Aggregation
daily_stats = events \
    .groupBy("date", "event_type") \
    .agg(
        count("*").alias("event_count"),
        avg("duration").alias("avg_duration")
    ) \
    .orderBy("date")

daily_stats.write.mode("overwrite").parquet("s3://bucket/daily_stats/")
```

```python
# Spark Structured Streaming
from pyspark.sql.functions import from_json, col
from pyspark.sql.types import StructType, StringType, TimestampType

schema = StructType() \
    .add("user_id", StringType()) \
    .add("event_type", StringType()) \
    .add("timestamp", TimestampType())

# Read from Kafka
stream = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "kafka:9092") \
    .option("subscribe", "events") \
    .load() \
    .select(from_json(col("value").cast("string"), schema).alias("data")) \
    .select("data.*")

# Windowed aggregation (micro-batch)
windowed = stream \
    .withWatermark("timestamp", "10 minutes") \
    .groupBy(
        window("timestamp", "5 minutes"),
        "user_id"
    ) \
    .count()

# Write to output
query = windowed.writeStream \
    .outputMode("update") \
    .format("console") \
    .trigger(processingTime="10 seconds") \
    .start()

query.awaitTermination()
```

## Windowing

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WINDOW TYPES                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. TUMBLING WINDOW (Fixed, non-overlapping)
   
   ├─────────┼─────────┼─────────┼─────────┤
   │ Window1 │ Window2 │ Window3 │ Window4 │
   ├─────────┼─────────┼─────────┼─────────┤
   0    5min    10min    15min    20min
   
   Each event belongs to exactly one window

2. SLIDING WINDOW (Overlapping)
   
   ├─────────────────────┤  Window 1 (0-10min)
       ├─────────────────────┤  Window 2 (5-15min)
           ├─────────────────────┤  Window 3 (10-20min)
   
   Events may belong to multiple windows
   
3. SESSION WINDOW (Gap-based)
   
   User activity: ●  ●●  ●     ●●●●     ●  ●
                  └─────┘     └────┘   └───┘
                  Session1   Session2  Session3
   
   Window closes after inactivity gap

4. GLOBAL WINDOW (All events in one window)
   Used with custom triggers
```

## State Management (Flink)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FLINK STATE                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

// Keyed State - partitioned by key
public class CountingMapper extends KeyedProcessFunction<String, Event, Result> {
    private ValueState<Long> countState;
    
    @Override
    public void open(Configuration params) {
        ValueStateDescriptor<Long> descriptor =
            new ValueStateDescriptor<>("count", Long.class);
        countState = getRuntimeContext().getState(descriptor);
    }
    
    @Override
    public void processElement(Event event, Context ctx, Collector<Result> out) {
        Long count = countState.value();
        if (count == null) count = 0L;
        count++;
        countState.update(count);
        
        if (count >= 100) {
            out.collect(new Result(event.getKey(), count));
        }
    }
}

STATE BACKENDS:
  • MemoryStateBackend: Fast, limited size
  • FsStateBackend: Checkpoints to HDFS/S3
  • RocksDBStateBackend: Large state, spills to disk
```

## Exactly-Once Processing

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FLINK CHECKPOINTING                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Checkpoint = Consistent snapshot of:
  • Operator state
  • Kafka offsets
  • In-flight records

PROCESS:
  1. Job Manager triggers checkpoint
  2. Sources snapshot their positions (Kafka offsets)
  3. Checkpoint barriers flow through pipeline
  4. Each operator snapshots state when barrier passes
  5. State stored in durable storage (S3, HDFS)

ON FAILURE:
  1. Restore state from last checkpoint
  2. Resume from saved Kafka offsets
  3. No data loss, no duplicates

  Time ─────────────────────────────────────────────▶
       │                                             │
  CP1 ─┼─────────────────┐                           │
       │                 │ Failure                   │
       │                 ▼                           │
       │            ╳ Crash                          │
       │                                             │
       │  ┌──── Restore from CP1 ────▶ Continue     │
       │  │                                          │
       └──┘                                          │
```

## When to Use

✅ **Flink for:**
- Real-time event processing
- Complex event patterns
- Stateful computations
- Low-latency requirements

✅ **Spark for:**
- Batch ETL pipelines
- Machine learning
- SQL analytics
- Near-real-time (seconds latency OK)

## Trade-offs

| | Flink | Spark |
|--|-------|-------|
| Latency | Milliseconds | Seconds |
| Streaming | Native | Micro-batch |
| Batch | Good | Excellent |
| State | Built-in | External |
| Ecosystem | Growing | Very mature |

## Used In (This Guide)

- **Real-time Analytics (Q44)**: Stream aggregations
- **Fraud Detection (Q27)**: Pattern matching
- **ETL Pipelines (Q20)**: Data transformations
- **Recommendation (Q33)**: Feature computation
