# QuadTree

## What It Is
A QuadTree is a tree data structure where each internal node has exactly four children, used to partition a 2D space by recursively subdividing it into four quadrants. It's commonly used for spatial indexing, collision detection, and location-based services.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         QUADTREE STRUCTURE                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Start with a 2D region, subdivide when capacity exceeded:

Step 1: Points arrive       Step 2: Subdivide (>4 points)
┌───────────────────────┐   ┌───────────┬───────────┐
│                       │   │ NW   •    │    NE     │
│    •        •         │   │           │     •     │
│         •             │   ├───────────┼───────────┤
│    •                  │   │ SW        │    SE     │
│              •        │   │    •    • │      •    │
└───────────────────────┘   └───────────┴───────────┘

Tree representation:
                    [Root]
                   /  |  \  \
                 NW  NE  SW  SE
                 /\       |
              [...]    [Points]
```

## Recursive Subdivision

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SUBDIVISION EXAMPLE                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

World: 100x100 units, capacity = 4 points per node

Insert points: (10,10), (20,80), (70,70), (80,20), (85,25)

After 5th point (exceeds capacity):

┌─────────────────────────────────────┐
│                   │                 │
│   (10,10)         │    (20,80)     │
│        •          │       •         │
│─────────────────────────────────────│
│                   │     • (70,70)   │
│                   │     • (80,20)   │
│                   │     • (85,25)   │
└─────────────────────────────────────┘

SE quadrant has 3 points (under capacity) - no further split
If more points added to SE, it subdivides into 4 more quadrants
```

## Implementation

```python
class Point:
    def __init__(self, x, y, data=None):
        self.x = x
        self.y = y
        self.data = data

class Rectangle:
    def __init__(self, x, y, width, height):
        self.x = x          # Center x
        self.y = y          # Center y
        self.w = width      # Half width
        self.h = height     # Half height
    
    def contains(self, point):
        return (self.x - self.w <= point.x < self.x + self.w and
                self.y - self.h <= point.y < self.y + self.h)
    
    def intersects(self, other):
        return not (other.x - other.w > self.x + self.w or
                   other.x + other.w < self.x - self.w or
                   other.y - other.h > self.y + self.h or
                   other.y + other.h < self.y - self.h)

class QuadTree:
    def __init__(self, boundary, capacity=4):
        self.boundary = boundary    # Rectangle
        self.capacity = capacity    # Max points before split
        self.points = []
        self.divided = False
        self.nw = self.ne = self.sw = self.se = None
    
    def subdivide(self):
        x, y, w, h = self.boundary.x, self.boundary.y, self.boundary.w, self.boundary.h
        
        self.nw = QuadTree(Rectangle(x - w/2, y + h/2, w/2, h/2), self.capacity)
        self.ne = QuadTree(Rectangle(x + w/2, y + h/2, w/2, h/2), self.capacity)
        self.sw = QuadTree(Rectangle(x - w/2, y - h/2, w/2, h/2), self.capacity)
        self.se = QuadTree(Rectangle(x + w/2, y - h/2, w/2, h/2), self.capacity)
        
        self.divided = True
        
        # Redistribute existing points
        for point in self.points:
            self._insert_into_children(point)
        self.points = []
    
    def insert(self, point):
        if not self.boundary.contains(point):
            return False
        
        if not self.divided:
            if len(self.points) < self.capacity:
                self.points.append(point)
                return True
            self.subdivide()
        
        return self._insert_into_children(point)
    
    def _insert_into_children(self, point):
        return (self.nw.insert(point) or self.ne.insert(point) or
                self.sw.insert(point) or self.se.insert(point))
    
    def query(self, range_rect, found=None):
        """Find all points within a rectangular range"""
        if found is None:
            found = []
        
        if not self.boundary.intersects(range_rect):
            return found
        
        for point in self.points:
            if range_rect.contains(point):
                found.append(point)
        
        if self.divided:
            self.nw.query(range_rect, found)
            self.ne.query(range_rect, found)
            self.sw.query(range_rect, found)
            self.se.query(range_rect, found)
        
        return found

# Usage
boundary = Rectangle(50, 50, 50, 50)  # 0-100 in both dimensions
qt = QuadTree(boundary)

# Insert restaurants
qt.insert(Point(25, 75, {"name": "Pizza Place"}))
qt.insert(Point(30, 70, {"name": "Burger Joint"}))
qt.insert(Point(80, 20, {"name": "Sushi Bar"}))

# Find nearby (within 20 units of point 30, 70)
search_area = Rectangle(30, 70, 20, 20)
nearby = qt.query(search_area)
```

## Nearest Neighbor Search

```python
def find_nearest(self, target_point):
    """Find the nearest point to target"""
    best = {"point": None, "distance": float('inf')}
    
    def distance(p1, p2):
        return ((p1.x - p2.x)**2 + (p1.y - p2.y)**2) ** 0.5
    
    def search(node):
        if node is None:
            return
        
        # Check points in this node
        for point in node.points:
            d = distance(point, target_point)
            if d < best["distance"]:
                best["point"] = point
                best["distance"] = d
        
        # Search children, prioritizing quadrant containing target
        if node.divided:
            # Calculate which quadrant target is in
            children = [node.nw, node.ne, node.sw, node.se]
            children.sort(key=lambda c: distance(
                Point(c.boundary.x, c.boundary.y), target_point
            ))
            
            for child in children:
                # Only search if it could contain a closer point
                if child.boundary.intersects(Rectangle(
                    target_point.x, target_point.y,
                    best["distance"], best["distance"]
                )):
                    search(child)
    
    search(self)
    return best["point"]
```

## Use Cases

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LOCATION-BASED SERVICES                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. FIND NEARBY RESTAURANTS
   
   User at (lat, lng) wants restaurants within 1km
   
   Query QuadTree with bounding box:
   ┌────────────────────┐
   │        1km         │
   │    ┌───────┐       │
   │    │ •User │       │
   │    │   •   │       │
   │    │  • •  │       │
   │    └───────┘       │
   └────────────────────┘
   
   O(log n) average vs O(n) brute force

2. COLLISION DETECTION (Games)
   
   For each object, only check collision with objects in same quadrant
   
   Without QuadTree: n² collision checks
   With QuadTree: ~n log n checks

3. MAP RENDERING
   
   Only load/render objects visible in viewport
   Query QuadTree with viewport rectangle
```

## QuadTree vs Other Spatial Indexes

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPARISON                                                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────┬───────────────────────────────────────────────────────────────────────────┐
│ Structure    │ Best For                                                                  │
├──────────────┼───────────────────────────────────────────────────────────────────────────┤
│ QuadTree     │ 2D point data, dynamic insertions, games, maps                           │
│              │ Good for sparse, unevenly distributed data                               │
├──────────────┼───────────────────────────────────────────────────────────────────────────┤
│ Geohash      │ Single-dimension encoding, Redis/DB storage                              │
│              │ Good for database indexing, simple proximity                             │
├──────────────┼───────────────────────────────────────────────────────────────────────────┤
│ R-Tree       │ Rectangles/polygons, database indexes (PostGIS)                          │
│              │ Better for complex shapes, heavily optimized                             │
├──────────────┼───────────────────────────────────────────────────────────────────────────┤
│ KD-Tree      │ Multi-dimensional data, machine learning                                 │
│              │ Better for higher dimensions, static data                                │
└──────────────┴───────────────────────────────────────────────────────────────────────────┘

DEPTH CONSIDERATIONS:
  Max depth = log4(area / min_cell_size)
  
  World = 40,000km × 40,000km
  Min cell = 10m × 10m
  Max depth = log4(4×10⁹ / 100) ≈ 20 levels
```

## Complexity

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         TIME COMPLEXITY                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                    Average         Worst Case
                    ────────        ──────────
Insert              O(log n)        O(n) *
Query (range)       O(log n + k)    O(n)
Nearest neighbor    O(log n)        O(n)

* Worst case when all points cluster in same area
  (forces deep recursive subdivision)

SPACE: O(n) for n points

OPTIMIZATION:
  - Set reasonable max depth
  - Use minimum cell size
  - Consider bulk loading for static data
```

## When to Use

✅ **Good for:**
- 2D spatial queries (nearby points)
- Dynamic data with frequent inserts
- Collision detection in games
- Map/viewport rendering
- Image compression

❌ **Not ideal for:**
- 3D or higher dimensions (use Octree/KD-Tree)
- Data with complex shapes (use R-Tree)
- Very clustered data (unbalanced tree)
- Need sorted traversal

## Trade-offs

| Pros | Cons |
|------|------|
| Fast spatial queries | Can be unbalanced |
| Simple implementation | 2D only |
| Dynamic inserts | Memory overhead per node |
| Good cache locality | Worst case O(n) if clustered |

## Used In (This Guide)

- **Proximity Service (Q43)**: Finding nearby drivers/restaurants
- **Map Rendering**: Viewport culling
- **Geospatial Search (Q5)**: Location-based autocomplete
- **Collision Detection**: Gaming scenarios
