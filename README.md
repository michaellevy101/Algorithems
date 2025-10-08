# String Matching Algorithms

A comprehensive Java implementation of various string matching algorithms, demonstrating the evolution from basic algorithms to optimized versions with extensive testing and documentation.

## 📋 Table of Contents

- [Overview](#overview)
- [Algorithms Implemented](#algorithms-implemented)
- [Features](#features)
- [Getting Started](#getting-started)
- [Usage Examples](#usage-examples)
- [Testing](#testing)
- [Performance](#performance)
- [Documentation](#documentation)
- [Contributing](#contributing)

## 🎯 Overview

This project provides educational implementations of classical string matching algorithms, focusing on:
- **Correctness**: All implementations pass comprehensive test suites
- **Educational Value**: Extensive inline documentation and comments
- **Performance**: O(n + m) time complexity for all algorithms
- **Testing**: 100+ test cases ensuring robustness

## 🔍 Algorithms Implemented

### 1. Dueling Algorithm (`Dueling.java`)
- **Galil-Seiferas-Vishkin implementation** (1980)
- Uses two competing scans (hence "dueling")
- **O(1) space complexity** - better than KMP!
- Time Complexity: O(n + m)
- Handles both periodic and aperiodic patterns optimally

### 2. Morris-Pratt Algorithm (`MorrisPrattSearch.java`)
- **Original 1970 implementation** with explicit window shifting
- Classic failure function (LPS array) construction
- Time Complexity: O(n + m)
- Educational focus on understanding the basic concepts

### 3. KMP Standard Algorithm (`KMPSearchStandard.java`)
- **Standard KMP implementation** as commonly taught
- Uses LPS array for pattern shifting
- Single-pass text scanning without backtracking
- Time Complexity: O(n + m)

### 4. KMP Optimized Algorithm (`KMPSearch.java`)
- **Enhanced KMP** with redundancy elimination
- Modified failure function with negative values
- Skips guaranteed-to-fail character comparisons
- Time Complexity: O(n + m) with fewer actual comparisons

### 5. Morris-Pratt with Logging (`MPSearch.java`)
- **Debug-enabled version** for educational purposes
- Comprehensive logging of algorithm steps
- Detailed visualization of pattern matching process
- Perfect for learning and teaching

## ✨ Features

### 🔧 Robust Implementation
- **Null safety**: All methods validate input parameters
- **Edge case handling**: Empty strings, single characters, no matches
- **Exception handling**: Clear error messages for invalid inputs

### 🧪 Comprehensive Testing
- **100+ test cases** across all algorithms
- **Reusable test framework** (`StringMatchingAlgorithmTest`)
- **Algorithm-specific tests** for table construction
- **Edge case coverage**: null inputs, empty patterns, overlapping matches

### 📚 Educational Focus
- **Extensive documentation**: JavaDoc for all public methods
- **Inline comments**: Step-by-step algorithm explanations
- **Example outputs**: Expected results for common patterns
- **Algorithm comparison**: Clear differences between implementations

### 🚀 Easy Integration
- **Consistent API**: All algorithms use the same method signature
- **Maven project**: Easy dependency management
- **Java 8 compatible**: Wide compatibility

## 🚀 Getting Started

### Prerequisites
- Java 8 or higher
- Maven 3.6 or higher

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/Algorithems.git
   cd Algorithems
   ```

2. **Build the project**
   ```bash
   mvn compile
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

## 💡 Usage Examples

### Basic Search
```java
import strings.KMPSearchStandard;
import java.util.List;

// Search for pattern in text
List<Integer> positions = KMPSearchStandard.search("ABABCABABA", "ABABA");
// Returns: [0, 5] - pattern found at positions 0 and 5
```

### Algorithm Comparison
```java
// Compare different implementations
String text = "ABABABABABABC";
String pattern = "ABABABC";

// Dueling (competing scans, O(1) space)
List<Integer> duelingResults = Dueling.search(text, pattern);

// Morris-Pratt (explicit window shifting)
List<Integer> mpResults = MorrisPrattSearch.search(text, pattern);

// KMP Standard (single-pass scanning)
List<Integer> kmpResults = KMPSearchStandard.search(text, pattern);

// KMP Optimized (redundancy elimination)
List<Integer> kmpOptResults = KMPSearch.search(text, pattern);

// All return: [6] - pattern found at position 6
```

### Table Construction
```java
// Build and examine failure function tables
String pattern = "ABABABC";

// Morris-Pratt LPS array
int[] mpTable = MPSearch.buildTable(pattern);
// Returns: [0, 0, 1, 2, 3, 4, 0]

// KMP optimized table
int[] kmpTable = KMPSearch.buildTable(pattern);
// Returns: [-1, 0, -1, 0, -1, 0, 4, 0]
```

## 🧪 Testing

The project includes a comprehensive test suite with:

### Test Categories
- **Functionality tests**: Basic pattern matching
- **Edge case tests**: Empty inputs, single characters
- **Performance tests**: Large inputs, overlapping patterns
- **Table construction tests**: Verify failure function correctness
- **Null safety tests**: Input validation

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=KMPSearchTest

# Run with verbose output
mvn test -Dtest=MPSearchConcreteTest
```

### Test Results
- **101 total tests** across all algorithms
- **100% pass rate** with comprehensive coverage
- **Null validation tests** ensure robust error handling

## ⚡ Performance

All algorithms guarantee **O(n + m)** time complexity where:
- `n` = text length
- `m` = pattern length

### Space Complexity
- **O(m)** for failure function table storage
- **O(k)** for result list where k = number of matches

### Algorithm Characteristics
| Algorithm | Time | Space | Comparisons | Best For |
|-----------|------|-------|-------------|----------|
| Dueling | O(n+m) | O(1) | Competing scans | Space-critical applications |
| Morris-Pratt | O(n+m) | O(m) | Standard | Learning MP concepts |
| KMP Standard | O(n+m) | O(m) | Standard | General use |
| KMP Optimized | O(n+m) | O(m) | Reduced | Performance critical |
| MP with Logging | O(n+m) | O(m) | Standard | Debugging/Education |

## 📖 Documentation

### Class Documentation
- **Comprehensive JavaDoc**: All public methods documented
- **Algorithm explanations**: Time/space complexity noted
- **Usage examples**: Parameter descriptions and examples
- **Exception handling**: Clear documentation of error conditions

### Code Comments
- **Step-by-step explanations**: Algorithm phases documented
- **Example walkthroughs**: Common patterns traced
- **Mathematical foundations**: LPS array construction explained
- **Optimization details**: KMP improvements described

## 🤝 Contributing

Contributions are welcome! Please feel free to:

1. **Report bugs** or suggest improvements
2. **Add new algorithms** (Boyer-Moore, Rabin-Karp, etc.)
3. **Improve documentation** or add examples
4. **Optimize performance** or add benchmarks

### Development Guidelines
- Maintain consistent API across algorithms
- Add comprehensive tests for new features
- Document all public methods with JavaDoc
- Follow existing code style and patterns

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Zvi Galil, Joel Seiferas, and Uzi Vishkin** for the Dueling algorithm (1980)
- **Donald Knuth, James Morris, and Vaughan Pratt** for the KMP algorithm (1977)
- **James Morris and Vaughan Pratt** for the Morris-Pratt algorithm (1970)
- **The Java community** for excellent tooling and documentation
- **Contributors** who help improve this educational resource

---

**Happy String Matching!** 🎉

For questions or support, please open an issue on GitHub.
