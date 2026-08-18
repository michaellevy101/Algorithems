# Geohash

## What It Is
Geohash is a geocoding system that converts geographic coordinates (latitude, longitude) into a short alphanumeric string. Nearby locations share similar prefixes, making it ideal for proximity searches.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         GEOHASH ENCODING                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

INPUT: Latitude 47.6062, Longitude -122.3321 (Seattle)
OUTPUT: "c23nb62w"

PROCESS: Recursively divide space into quadrants

Step 1: Divide world into 4 quadrants
┌───────────────────────────────────┐
│               │                   │
│      01       │        11         │   Bit pattern:
│   (NW)        │      (NE)         │     0 = West/South
├───────────────┼───────────────────┤     1 = East/North
│               │                   │
│      00       │        10         │   Longitude bit first,
│   (SW)        │      (SE)         │   then latitude bit
│               │                   │
└───────────────────────────────────┘

Step 2: Seattle is in NW quadrant, so first bits = "01"
        Continue dividing that quadrant...

Step 3: After many iterations, get binary string
        Convert to Base32: "c23nb62w"
```

## Encoding Algorithm

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         BINARY INTERLEAVING                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Lat: 47.6062   Range: [-90, 90]
Lng: -122.3321 Range: [-180, 180]

Longitude bits (odd positions):
  [-180, 180] → -122.33 is in left half → 0
  [-180, 0]   → -122.33 is in right half → 1
  [-90, 0]    → -122.33 is in left half → 0
  ...

Latitude bits (even positions):
  [-90, 90]   → 47.60 is in right half → 1
  [0, 90]     → 47.60 is in right half → 1
  [45, 90]    → 47.60 is in left half → 0
  ...

Interleave: lng[0], lat[0], lng[1], lat[1], ...
Binary: 0110 1001 0011 1011 0110 0010 ...
Base32: c    2    3    n    b    6    ...

Result: "c23nb62w"
```

## Precision Levels

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         GEOHASH PRECISION                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

| Length | Lat Error | Lng Error | Cell Size        | Use Case           |
|--------|-----------|-----------|------------------|-------------------|
| 1      | ±2500 km  | ±5000 km  | ~5000 × 5000 km  | Continent         |
| 2      | ±630 km   | ±1250 km  | ~1250 × 625 km   | Large country     |
| 3      | ±78 km    | ±156 km   | ~156 × 156 km    | State/region      |
| 4      | ±20 km    | ±39 km    | ~39 × 20 km      | City              |
| 5      | ±2.4 km   | ±4.9 km   | ~5 × 5 km        | Neighborhood      |
| 6      | ±610 m    | ±1.2 km   | ~1.2 × 0.6 km    | Streets           |
| 7      | ±76 m     | ±153 m    | ~150 × 150 m     | Block             |
| 8      | ±19 m     | ±38 m     | ~38 × 19 m       | Building          |
| 9      | ±2.4 m    | ±4.8 m    | ~5 × 5 m         | Entrance          |

Choose precision based on your radius requirements:
  5km search → Length 5 geohash
  500m search → Length 7 geohash
```

## Proximity Search

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FINDING NEARBY PLACES                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Query: Find restaurants within 1km of "c23nb6"

Step 1: Get the 8 neighboring cells
┌────────┬────────┬────────┐
│ c23nb4 │ c23nb5 │ c23nbh │
├────────┼────────┼────────┤
│ c23nb1 │ c23nb6 │ c23nb7 │  ← Center cell
├────────┼────────┼────────┤
│ c23nb0 │ c23nb2 │ c23nb3 │
└────────┴────────┴────────┘

Step 2: Query database with prefix match
SELECT * FROM places 
WHERE geohash LIKE 'c23nb6%'
   OR geohash LIKE 'c23nb4%'
   OR geohash LIKE 'c23nb5%'
   OR geohash LIKE 'c23nbh%'
   OR geohash LIKE 'c23nb1%'
   OR geohash LIKE 'c23nb7%'
   OR geohash LIKE 'c23nb0%'
   OR geohash LIKE 'c23nb2%'
   OR geohash LIKE 'c23nb3%';

Step 3: Filter by exact distance (Haversine formula)
  - Some results may be outside actual radius
  - Calculate exact distance, filter out false positives
```

## The Boundary Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         EDGE CASE: BOUNDARY                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PROBLEM: Two nearby points in different geohash cells

    ┌──────────┬──────────┐
    │          │          │
    │    A ●───┼───● B    │   A and B are 100m apart
    │          │          │   But in DIFFERENT cells!
    └──────────┴──────────┘
       c23nb6    c23nb7

If you only query c23nb6, you'll miss B!

SOLUTION: Always query 8 neighbors + center cell

    ┌────────┬────────┬────────┐
    │   NW   │   N    │   NE   │
    ├────────┼────────┼────────┤
    │   W    │ CENTER │   E    │
    ├────────┼────────┼────────┤
    │   SW   │   S    │   SE   │
    └────────┴────────┴────────┘
```

## Implementation

```python
import math

# Base32 alphabet for geohash
BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

def encode(lat, lng, precision=8):
    """Encode lat/lng to geohash"""
    lat_range = (-90.0, 90.0)
    lng_range = (-180.0, 180.0)
    
    geohash = []
    bits = 0
    bit_count = 0
    is_lng = True  # Alternate between lng and lat
    
    while len(geohash) < precision:
        if is_lng:
            mid = (lng_range[0] + lng_range[1]) / 2
            if lng >= mid:
                bits = (bits << 1) | 1
                lng_range = (mid, lng_range[1])
            else:
                bits = bits << 1
                lng_range = (lng_range[0], mid)
        else:
            mid = (lat_range[0] + lat_range[1]) / 2
            if lat >= mid:
                bits = (bits << 1) | 1
                lat_range = (mid, lat_range[1])
            else:
                bits = bits << 1
                lat_range = (lat_range[0], mid)
        
        is_lng = not is_lng
        bit_count += 1
        
        if bit_count == 5:
            geohash.append(BASE32[bits])
            bits = 0
            bit_count = 0
    
    return ''.join(geohash)

def decode(geohash):
    """Decode geohash to lat/lng"""
    lat_range = (-90.0, 90.0)
    lng_range = (-180.0, 180.0)
    is_lng = True
    
    for char in geohash:
        bits = BASE32.index(char)
        for i in range(4, -1, -1):
            bit = (bits >> i) & 1
            if is_lng:
                mid = (lng_range[0] + lng_range[1]) / 2
                if bit:
                    lng_range = (mid, lng_range[1])
                else:
                    lng_range = (lng_range[0], mid)
            else:
                mid = (lat_range[0] + lat_range[1]) / 2
                if bit:
                    lat_range = (mid, lat_range[1])
                else:
                    lat_range = (lat_range[0], mid)
            is_lng = not is_lng
    
    lat = (lat_range[0] + lat_range[1]) / 2
    lng = (lng_range[0] + lng_range[1]) / 2
    return lat, lng

def neighbors(geohash):
    """Get 8 neighboring geohash cells"""
    # Implementation involves bit manipulation
    # Returns list of 8 adjacent geohashes
    pass

# Usage
gh = encode(47.6062, -122.3321, 8)
print(gh)  # "c23nb62w"

lat, lng = decode("c23nb62w")
print(lat, lng)  # ~47.6062, ~-122.3321
```

## Database Usage

```sql
-- PostgreSQL with geohash column
CREATE TABLE places (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    lat DECIMAL(10, 8),
    lng DECIMAL(11, 8),
    geohash VARCHAR(12)
);

-- Index for prefix queries
CREATE INDEX idx_places_geohash ON places (geohash);

-- Insert with geohash
INSERT INTO places (id, name, lat, lng, geohash)
VALUES (1, 'Starbucks', 47.6062, -122.3321, 'c23nb62w');

-- Proximity query
SELECT * FROM places
WHERE geohash LIKE 'c23nb6%'  -- All places in cell + subcells
ORDER BY geohash;
```

## Redis Geo Commands

```redis
# Redis uses geohash internally!

# Add places
GEOADD places -122.3321 47.6062 "starbucks"
GEOADD places -122.3400 47.6100 "mcdonalds"

# Find within radius
GEORADIUS places -122.3321 47.6062 5 km WITHDIST WITHCOORD
# Returns places within 5km with distances and coordinates

# Get geohash
GEOHASH places "starbucks"
# Returns: "c23nb62..."

# Distance between two places
GEODIST places "starbucks" "mcdonalds" km
# Returns: 0.8
```

## Geohash vs Alternatives

| Feature | Geohash | S2 (Google) | H3 (Uber) | QuadTree |
|---------|---------|-------------|-----------|----------|
| Cell shape | Rectangle | Square (approx) | Hexagon | Square |
| Hierarchy | String prefix | 64-bit cell ID | 64-bit cell ID | Tree |
| Edge distortion | Yes (poles) | Minimal | Minimal | Yes |
| Neighbor finding | Moderate | Easy | Easy | Moderate |
| Database support | Wide | Limited | Limited | Custom |

## When to Use

✅ **Good for:**
- Proximity searches (find nearby)
- Location-based indexing
- Spatial clustering
- Geofencing
- Map tile indexing

❌ **Not ideal for:**
- Precise distance calculations (use Haversine)
- Path/route queries
- Polygon containment
- Areas near poles (high distortion)

## Trade-offs

| Pros | Cons |
|------|------|
| Simple string operations | Boundary issues |
| Database-friendly (prefix index) | Rectangle cells (not uniform) |
| Hierarchical precision | Distortion at poles |
| Compact representation | Need 9 cells for radius query |
| Human-readable | Not optimal for all use cases |

## Used In (This Guide)

- **Uber/Lyft (Q6)**: Driver location indexing
- **Proximity Service (Q43)**: Finding nearby places
- **Real-time Location (Q21)**: Location tracking
- **Bing Search (in crawler)**: Geo-aware results
