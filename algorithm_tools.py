#!/usr/bin/env python3
"""
Algorithm Tools - Unified Script for Algorithm Guide Management
=============================================================

This script consolidates all algorithm guide management functionality into a single tool.

Usage:
    python algorithm_tools.py <command> [options]

Commands:
    split           Split guides into theory and problems compilations
    pdf-theory      Generate theory guide PDF
    pdf-problems    Generate problems guide PDF
    pdf-complete    Generate complete reference PDF
    pdf-all         Generate all PDFs (algorithms)
    pdf-ai          Generate AI & Agents guide PDF
    pdf-ml          Generate ML Engineering guide PDF
    pdf-sections    Generate individual PDF for each algorithm guide section
    visuals         Add enhanced visual diagrams to guides
    images          Generate images for Mermaid diagrams and add links to markdown
    help            Show this help message

Examples:
    python algorithm_tools.py split
    python algorithm_tools.py pdf-theory
    python algorithm_tools.py pdf-all
    python algorithm_tools.py pdf-ai
    python algorithm_tools.py pdf-ml
    python algorithm_tools.py pdf-sections
    python algorithm_tools.py visuals
    python algorithm_tools.py images
"""

import os
import sys
import re
import subprocess
from datetime import datetime
from pathlib import Path

def print_header(title):
    """Print a formatted header."""
    print(f"\n🚀 {title}")
    print("=" * 50)

def split_guides():
    """Split individual guides into theory and problems compilations."""
    print_header("Algorithm Guide Splitter")
    
    # Guide files in order with display names
    guide_files = [
        ("guides/01_Union_Find.md", "Union-Find (Disjoint Set Union)"),
        ("guides/02_Binary_Search.md", "Binary Search"),
        ("guides/03_Two_Pointers.md", "Two Pointers"),
        ("guides/04_Sliding_Window.md", "Sliding Window"),
        ("guides/05_Monotonic_Stack.md", "Monotonic Stack"),
        ("guides/06_Intervals.md", "Intervals"),
        ("guides/07_TopK_Heaps.md", "Top-K & Heaps"),
        ("guides/08_Greedy.md", "Greedy Algorithms"),
        ("guides/09_Dynamic_Programming.md", "Dynamic Programming"),
        ("guides/10_Graph_Algorithms.md", "Graph Algorithms"),
        ("guides/11_Tree_Algorithms.md", "Tree Algorithms"),
        ("guides/12_Backtracking.md", "Backtracking"),
        ("guides/13_Trie_Strings.md", "Trie & String Algorithms"),
        ("guides/14_Math_Bits.md", "Math & Bit Manipulation"),
        ("guides/15_Java_DataStructures.md", "Java Data Structures"),
        ("guides/16_Flow_Algorithms.md", "Flow Algorithms")
    ]
    
    print("Processing algorithm guides...")
    
    # Initialize content
    theory_content = []
    problems_content = []
    
    # Add headers with Table of Contents
    theory_content.append("# Algorithm Interview Reference — Theory Guide\n\n")
    theory_content.append("*Core Algorithms, Data Structures, and Implementation Techniques*\n\n")
    theory_content.append(f"**Generated on:** {datetime.now().strftime('%B %d, %Y')}\n\n")
    theory_content.append("---\n\n")
    
    # Add Table of Contents
    theory_content.append("## Table of Contents\n\n")
    for i, (_, name) in enumerate(guide_files, 1):
        anchor = name.lower().replace(' ', '-').replace('&', '').replace('(', '').replace(')', '').replace('--', '-')
        theory_content.append(f"{i}. [{name}](#{i}-{anchor})\n")
    theory_content.append("\n---\n\n")
    
    problems_content.append("# Algorithm Interview Reference — Problems Guide\n\n")
    problems_content.append("*Curated Problems with Solutions and Analysis*\n\n")
    problems_content.append(f"**Generated on:** {datetime.now().strftime('%B %d, %Y')}\n\n")
    problems_content.append("---\n\n")
    
    # Process each guide
    for i, (filename, display_name) in enumerate(guide_files, 1):
        if not os.path.exists(filename):
            print(f"Warning: File '{filename}' not found, skipping...")
            continue
            
        print(f"Processing {filename}...")
        
        with open(filename, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Remove the file-specific header (first line with >>)
        lines = content.split('\n')
        if lines and lines[0].strip().endswith('>>'):
            lines = lines[1:]
        
        content = '\n'.join(lines)
        
        # Remove navigation links at end of file
        content = re.sub(r'\n\[← Previous:.*?\]\(.*?\).*$', '', content, flags=re.DOTALL)
        
        # Remove placeholder "Interview Tips" sections with template content
        content = re.sub(
            r'## Interview Tips\s*\n+### Pattern Recognition\s*\n- When to identify.*?```\s*\n',
            '',
            content,
            flags=re.DOTALL
        )
        
        # Split content at "Common Problem Patterns" section
        split_pattern = r'\n## Common Problem Patterns\n'
        parts = re.split(split_pattern, content, maxsplit=1)
        
        # Theory part (everything before Common Problem Patterns)
        theory_part = parts[0].strip()
        # Fix relative image paths for root location
        theory_part = theory_part.replace('../generated_images/', 'generated_images/')
        
        # Add section header with number
        theory_content.append(f"# {i}. {display_name}\n\n")
        theory_content.append(theory_part)
        theory_content.append("\n\n---\n\n")
        
        # Problems part (Common Problem Patterns and everything after)
        if len(parts) > 1:
            problems_part = "## Common Problem Patterns\n" + parts[1].strip()
            # Fix relative image paths for root location
            problems_part = problems_part.replace('../generated_images/', 'generated_images/')
            
            problems_content.append(f"# {i}. {display_name}\n\n")
            problems_content.append(problems_part)
            problems_content.append("\n\n---\n\n")
    
    # Write theory guide
    theory_output = ''.join(theory_content)
    with open('Algorithm_Theory_Guide.md', 'w', encoding='utf-8') as f:
        f.write(theory_output)
    print("✅ Theory guide created: Algorithm_Theory_Guide.md")
    
    # Write problems guide
    problems_output = ''.join(problems_content)
    with open('Algorithm_Problems_Guide.md', 'w', encoding='utf-8') as f:
        f.write(problems_output)
    print("✅ Problems guide created: Algorithm_Problems_Guide.md")
    
    print("\n✅ Successfully split guides into:")
    print("   📚 Theory: Algorithm_Theory_Guide.md")
    print("   🧩 Problems: Algorithm_Problems_Guide.md")
    print("\nReady to generate separate PDFs! 🎉")

def process_mermaid_diagrams(content, guide_name):
    """
    Find mermaid blocks, convert to images using mmdc, and replace in content.
    Returns processed content.
    """
    import hashlib
    
    # Create images directory if not exists
    images_dir = "generated_images"
    os.makedirs(images_dir, exist_ok=True)
    
    def replacer(match):
        block = match.group(1)
        existing_image = match.group(2)
        
        if existing_image:
            # If image already exists in markdown, just return the image part 
            # (removing the mermaid block from PDF input effectively)
            return existing_image

        # Extract mermaid code from block
        mermaid_code = re.search(r'```mermaid\n(.*?)\n```', block, re.DOTALL).group(1)

        # Create unique hash for this diagram
        code_hash = hashlib.md5(mermaid_code.encode('utf-8')).hexdigest()[:10]
        
        # Use shared filename ignoring guide_name so we don't regenerate for different guides
        image_filename = f"diagram_{code_hash}.png" 
        image_path = os.path.join(images_dir, image_filename)
        
        # Only generate if doesn't exist to save time
        if not os.path.exists(image_path):
            print(f"   Generating diagram: {image_filename}...")
            # Save mermaid code to temp file
            temp_mmd = f"temp_{code_hash}.mmd"
            with open(temp_mmd, 'w', encoding='utf-8') as f:
                f.write(mermaid_code)
                
            # Run mmdc
            # npx -p @mermaid-js/mermaid-cli mmdc -i input.mmd -o output.png
            cmd = ["npx", "-p", "@mermaid-js/mermaid-cli", "mmdc", 
                   "-i", temp_mmd, "-o", image_path, 
                   "-b", "transparent", "-t", "neutral", "-s", "2"]
            
            is_windows = sys.platform == 'win32'
            try:
                subprocess.run(cmd, check=True, capture_output=True, shell=is_windows)
            except subprocess.CalledProcessError as e:
                print(f"   Error generating diagram: {e}")
                if e.stderr:
                    print(f"   STDERR: {e.stderr.decode('utf-8')}")
                if e.stdout:
                    print(f"   STDOUT: {e.stdout.decode('utf-8')}")
                # Fallback: keep code block
                if os.path.exists(temp_mmd): os.remove(temp_mmd)
                return match.group(0)
            
            if os.path.exists(temp_mmd):
                os.remove(temp_mmd)
        
        # Return markdown image link
        return f'\n![Diagram]({image_path.replace(os.path.sep, "/")})\n'

    # Regex to find mermaid blocks (optionally commented) AND optional following image
    pattern = r'((?:<!--\s*)?```mermaid\n.*?\n```(?:\s*-->)?)(\s*!\[.*?\]\(.*?\))?'
    new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)
    # Also handle static references (like diagram_evolution.png) that might be in the markdown?
    # No, md-to-pdf handles normal markdown image links fine.
    return new_content

def generate_pdf(guide_type):
    """Generate PDF from markdown guide using md-to-pdf with pre-rendered diagrams."""
    guide_files = {
        'theory': ('Algorithm_Theory_Guide.md', 'Algorithm_Theory_Guide.pdf'),
        'problems': ('Algorithm_Problems_Guide.md', 'Algorithm_Problems_Guide.pdf'),
        'complete': ('Algorithm_Interview_Reference.md', 'Algorithm_Interview_Reference_Complete.pdf')
    }
    
    if guide_type not in guide_files:
        print(f"Error: Unknown guide type '{guide_type}'")
        return False
    
    input_file, output_file = guide_files[guide_type]
    
    if not os.path.exists(input_file):
        print(f"Error: Input file '{input_file}' not found!")
        return False
    
    print_header(f"Algorithm {guide_type.title()} Guide - PDF Generator")
    print(f"Processing {input_file}...")
    
    try:
        # 1. Read Original Content
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # 2. Pre-process Mermaid Diagrams to Images
        print("Converting Mermaid diagrams to images...")
        processed_content = process_mermaid_diagrams(content, guide_type)
        
        # 3. Save Temporary Processed Markdown
        temp_input_file = input_file.replace('.md', '_rendered.md')
        with open(temp_input_file, 'w', encoding='utf-8') as f:
            f.write(processed_content)
        
        print(f"Generating PDF using md-to-pdf: {output_file}...")
        
        # 4. Run md-to-pdf on the temp file
        # We don't need pdf_config.js strictly anymore for mermaid, 
        # but keep it for CSS styling (fonts etc)
        config_file = "pdf_config.js"
        
        cmd = ["npx", "md-to-pdf", temp_input_file]
        
        if os.path.exists(config_file):
            cmd.extend(["--config-file", config_file])
            print(f"   Using config: {config_file} (for styling)")
        
        is_windows = sys.platform == 'win32'
        
        process = subprocess.run(
            cmd, 
            capture_output=True, 
            text=True,
            shell=is_windows
        )
        
        # Cleanup temp file
        if os.path.exists(temp_input_file):
            os.remove(temp_input_file)
            pass

        if process.returncode == 0:
            print("PDF generation command successful.")
            
            # md-to-pdf output name will be based on input temp file name
            generated_file = temp_input_file.replace('.md', '.pdf')
            
            if os.path.exists(generated_file):
                # Rename to final target
                if os.path.exists(output_file):
                    os.remove(output_file)
                os.rename(generated_file, output_file)
                
                size_mb = os.path.getsize(output_file) / (1024 * 1024)
                print(f"\n✅ Success! {guide_type.title()} PDF generated:")
                print(f"   📄 File: {output_file}")
                print(f"   📊 Size: {size_mb:.1f} MB")
                return True
            else:
                print(f"Error: generated file {generated_file} not found.")
                # Fallback check if it named it matching original?
                return False
        else:
            print(f"Error running md-to-pdf:\n{process.stdout}\n{process.stderr}")
            return False
            
    except Exception as e:
        print(f"Error generating PDF: {e}")
        import traceback
        traceback.print_exc()
        return False

def generate_diagrams_from_files():
    """Generate images from dedicated .mmd files in diagrams/ directory."""
    print_header("Generating Diagrams from File Sources")
    
    diagrams_dir = "diagrams"
    images_dir = "generated_images"
    os.makedirs(images_dir, exist_ok=True)
    
    if not os.path.exists(diagrams_dir):
        print(f"Directory '{diagrams_dir}' not found. Skipping file-based generation.")
        return

    mmd_files = [f for f in os.listdir(diagrams_dir) if f.endswith('.mmd')]
    
    if not mmd_files:
        print("No .mmd files found in diagrams/ directory.")
        return

    print(f"Found {len(mmd_files)} diagram source files.")
    
    for filename in mmd_files:
        filepath = os.path.join(diagrams_dir, filename)
        # Output filename corresponds to input filename (union_find.mmd -> union_find.png)
        image_filename = filename.replace('.mmd', '.png')
        image_path = os.path.join(images_dir, image_filename)
        
        print(f"Processing {filename} -> {image_filename}...")
        
        cmd = ["npx", "-p", "@mermaid-js/mermaid-cli", "mmdc", 
               "-i", filepath, "-o", image_path, 
               "-b", "transparent", "-t", "neutral", "-s", "2"]
        
        is_windows = sys.platform == 'win32'
        try:
            # Check if npx is available
            subprocess.run(cmd, check=True, shell=is_windows, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print(f"   ✅ Generated {image_path}")
        except subprocess.CalledProcessError:
            print(f"   ❌ Failed to generate {image_path}")
        except FileNotFoundError:
             print("   ❌ Error: npx/mmdc not found. Is Node.js installed?")

def update_guides_with_images():
    """Scan guides, generate images for mermaid diagrams, and append image links to markdown."""
    print_header("Updating Guides with Diagram Images")
    
    # Process dedicated diagram files first
    generate_diagrams_from_files()
    
    guides_dir = "guides"
    images_dir = "generated_images"
    os.makedirs(images_dir, exist_ok=True)
    
    # Get all markdown files in guides/
    if not os.path.exists(guides_dir):
        print(f"Error: Directory '{guides_dir}' not found.")
        return

    guide_files = [f for f in os.listdir(guides_dir) if f.endswith('.md')]
    
    import hashlib

    # Regex to capture mermaid block and optional following image
    # Matches ```mermaid\n...\n``` followed optionally by whitespace and an image link
    # Handles HTML comment wrappers <!-- ... -->
    regex_with_image = r'((?:<!--\s*)?```mermaid\n.*?\n```(?:\s*-->)?)(\s*!\[.*?\]\(.*?\))?'

    total_updated = 0

    for filename in guide_files:
        filepath = os.path.join(guides_dir, filename)
        is_updated = False
        
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        # If no mermaid blocks, skip
        if '```mermaid' not in content:
            continue
            
        print(f"Processing {filename}...")

        def processing_replacer(match):
            nonlocal is_updated
            block = match.group(1)
            existing_image = match.group(2)
            
            # Extract mermaid code from block
            mermaid_code = re.search(r'```mermaid\n(.*?)\n```', block, re.DOTALL).group(1)
            
            code_hash = hashlib.md5(mermaid_code.encode('utf-8')).hexdigest()[:10]
            image_filename = f"diagram_{code_hash}.png"
            image_path = os.path.join(images_dir, image_filename)
            # Link from inside guides/ to generated_images/ at root
            relative_image_path = f"../{images_dir}/{image_filename}"
            
            # Generate if needed
            if not os.path.exists(image_path):
                 print(f"   Generating diagram: {image_filename}...")
                 temp_mmd = f"temp_{code_hash}.mmd"
                 with open(temp_mmd, 'w', encoding='utf-8') as f:
                    f.write(mermaid_code)
                 
                 # npx command to run mermaid-cli
                 cmd = ["npx", "-p", "@mermaid-js/mermaid-cli", "mmdc", 
                   "-i", temp_mmd, "-o", image_path, 
                   "-b", "transparent", "-t", "neutral", "-s", "2"]
                 
                 is_windows = sys.platform == 'win32'
                 try:
                     subprocess.run(cmd, check=True, shell=is_windows, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                 except subprocess.CalledProcessError:
                     print(f"   Failed to generate {image_filename}")
                 finally:
                     if os.path.exists(temp_mmd): os.remove(temp_mmd)
            
            if existing_image:
                return match.group(0) 
            else:
                is_updated = True
                print(f"   Adding image link for diagram {code_hash}")
                return f"{block}\n\n![Diagram]({relative_image_path})"

        new_content = re.sub(regex_with_image, processing_replacer, content, flags=re.DOTALL)
        
        if is_updated:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            total_updated += 1
        else:
            pass # No changes needed

    print(f"\n✅ Updated {total_updated} guides with image links.")

def add_enhanced_visuals():
    """Add sophisticated visual diagrams to algorithm guides."""
    print_header("Enhanced Visual Diagram Creator")
    
    # Key guides to enhance with sophisticated visuals
    visual_enhancements = {
        # "guides/01_Union_Find.md": get_union_find_enhanced_visuals(), # REMOVED - Using file-based now
        "guides/08_Greedy.md": get_greedy_enhanced_visuals(), 
        "guides/10_Graph_Algorithms.md": get_graph_enhanced_visuals(),
        "guides/07_TopK_Heaps.md": get_heap_enhanced_visuals(),
        "guides/16_Flow_Algorithms.md": get_flow_enhanced_visuals()
    }
    
    for filename, enhanced_visuals in visual_enhancements.items():
        if not os.path.exists(filename):
            print(f"Warning: File '{filename}' not found, skipping...")
            continue
            
        print(f"Adding enhanced visuals to {filename}...")
        
        try:
            with open(filename, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Create backup
            backup_filename = f"{filename}.visual_enhanced_backup"
            with open(backup_filename, 'w', encoding='utf-8') as f:
                f.write(content)
            
            # Replace visual example section with enhanced version
            enhanced_content = replace_visual_section(content, enhanced_visuals)
            
            with open(filename, 'w', encoding='utf-8') as f:
                f.write(enhanced_content)
                
            print(f"✅ Enhanced visuals added to {filename}")
            
        except Exception as e:
            print(f"Error processing {filename}: {e}")
            continue
    
    print("\n✅ Enhanced visual diagrams added to guides!")
    print("📄 Run 'python algorithm_tools.py split' and 'python algorithm_tools.py pdf-theory' to see enhanced visuals in PDF")


def replace_visual_section(content, enhanced_visuals):
    """Replace the Visual Example section with enhanced visuals."""
    # Find the Visual Example section
    pattern = r'(## Visual Example\s*\n)(.*?)(\n## [^V]|\n# |\Z)'
    
    def replace_section(match):
        header = match.group(1)
        next_section = match.group(3) if match.group(3) else ""
        return header + enhanced_visuals + "\n" + next_section
    
    enhanced_content = re.sub(pattern, replace_section, content, flags=re.DOTALL)
    return enhanced_content

# Get Logic for other visuals remains... (truncated for brevity, but needed if used)
# Since I'm using write_to_file, I must include EVERYTHING.
# I'll include the rest of the file content below.

def get_union_find_enhanced_visuals():
   return "" # Deprecated?

def get_greedy_enhanced_visuals():
    """Get Greedy enhanced visuals."""
    return '''
### Activity Selection Timeline

```
╔═════════════════════════════════════════════════════════════════════════════╗
║                       ACTIVITY SELECTION PROBLEM                           ║
╠═════════════════════════════════════════════════════════════════════════════╣
║ Timeline:  0   1   2   3   4   5   6   7   8   9   10                      ║
║           ├───┼───┼───┼───┼───┼───┼───┼───┼───┤                         ║
║ Activity A: │   ████████████   │   │   │   │   │   │  [1,4] ✅ SELECT      ║
║ Activity B: │   │   ████████████   │   │   │   │   │  [2,5] ❌ Conflicts   ║
║ ⚡ Time Complexity: O(n log n) due to sorting                             ║
╚═════════════════════════════════════════════════════════════════════════════╝
```
'''

def get_graph_enhanced_visuals():
    """Get Graph enhanced visuals."""
    return '''
### BFS vs DFS Traversal Comparison

```
╔═════════════════════════════════════════════════════════════════════════════╗
║                         GRAPH TRAVERSAL ALGORITHMS                         ║
║ BFS: Wave-like exploration (level by level)                               ║
║ DFS: Deep exploration (go as far as possible)                             ║
║ Both: Time O(V + E), Space O(V)                                           ║
╚═════════════════════════════════════════════════════════════════════════════╝
```
'''

def get_heap_enhanced_visuals():
    """Get Heap enhanced visuals."""
    return '''
### Min-Heap Operations Visualization

```
╔═════════════════════════════════════════════════════════════════════════════╗
║                         HEAP DATA STRUCTURE                                ║
║ Min-Heap Property: parent ≤ children                                       ║
║ Insert Time Complexity: O(log n)                                          ║
╚═════════════════════════════════════════════════════════════════════════════╝
```
'''

def get_flow_enhanced_visuals():
    """Get Flow enhanced visuals."""
    return '''
### Maximum Flow Network Visualization

```
╔═════════════════════════════════════════════════════════════════════════════╗
║                         FORD-FULKERSON ALGORITHM                           ║
║ Key Insight: Each iteration finds an augmenting path and sends             ║
║              maximum possible flow through it                              ║
║ Ford-Fulkerson: O(E × f) where f = max flow value                         ║
╚═════════════════════════════════════════════════════════════════════════════╝
```
'''

def generate_section_pdfs():
    """Generate an individual PDF for each algorithm guide section and capacity-planning guide."""
    print_header("Per-Section PDF Generator")

    section_dirs = [
        ("guides/algorithms", "Algorithm Guides"),
        ("guides/capacity-and-ai-systems", "Capacity & AI Systems Guides"),
    ]

    config_file = "pdf_config.js"
    is_windows = sys.platform == 'win32'
    total = 0
    success = 0

    for guides_dir, label in section_dirs:
        if not os.path.exists(guides_dir):
            print(f"  ⚠ Directory not found: {guides_dir}, skipping")
            continue

        md_files = sorted(
            f for f in os.listdir(guides_dir)
            if f.endswith('.md') and f != 'README.md'
            and f != 'UnionFind_Learning_Guide.md'
        )

        if not md_files:
            print(f"  No markdown files in {guides_dir}")
            continue

        print(f"\n📂 {label} ({len(md_files)} files)")

        for md_file in md_files:
            total += 1
            md_path = os.path.join(guides_dir, md_file)
            pdf_file = md_file.replace('.md', '.pdf')
            pdf_path = os.path.join(guides_dir, pdf_file)

            print(f"  📄 {md_file} → {pdf_file}...")

            try:
                with open(md_path, 'r', encoding='utf-8') as f:
                    content = f.read()

                # Remove the >> navigation marker from the first heading
                lines = content.split('\n')
                if lines and lines[0].strip().endswith('>>'):
                    lines[0] = lines[0].replace(' >>', '').rstrip()
                content = '\n'.join(lines)

                # Pre-process mermaid diagrams
                processed_content = process_mermaid_diagrams(content, md_file)

                # Write temp file in repo root so relative image paths work
                temp_md = f"_temp_section_{md_file}"
                with open(temp_md, 'w', encoding='utf-8') as f:
                    f.write(processed_content)

                cmd = ["npx", "md-to-pdf", temp_md]
                if os.path.exists(config_file):
                    cmd.extend(["--config-file", config_file])

                process = subprocess.run(
                    cmd, capture_output=True, text=True, shell=is_windows
                )

                # Clean up temp markdown
                if os.path.exists(temp_md):
                    os.remove(temp_md)

                if process.returncode == 0:
                    generated = temp_md.replace('.md', '.pdf')
                    if os.path.exists(generated):
                        if os.path.exists(pdf_path):
                            os.remove(pdf_path)
                        os.rename(generated, pdf_path)
                        size_kb = os.path.getsize(pdf_path) / 1024
                        print(f"     ✅ {size_kb:.0f} KB")
                        success += 1
                    else:
                        print(f"     ❌ Generated PDF not found")
                else:
                    print(f"     ❌ md-to-pdf failed: {process.stderr[:200]}")
                    # Clean up any leftover temp pdf
                    temp_pdf = temp_md.replace('.md', '.pdf')
                    if os.path.exists(temp_pdf):
                        os.remove(temp_pdf)

            except Exception as e:
                print(f"     ❌ Error: {e}")
                # Clean up on error
                temp_md_cleanup = f"_temp_section_{md_file}"
                if os.path.exists(temp_md_cleanup):
                    os.remove(temp_md_cleanup)

    print(f"\n✅ Generated {success}/{total} section PDFs successfully!")


def show_help():
    """Show help message."""
    print(__doc__)


def generate_ai_pdf():
    """Generate a combined PDF for the AI & Agents guide series."""
    print_header("AI & Agents Guide - PDF Generator")

    ai_guide_files = [
        ("guides/ai-agents/01-llm-foundations.md", "Day 1: Modern LLM Foundations"),
        ("guides/ai-agents/01a-tokens-and-embeddings.md", "Day 1A: Tokens & Embeddings"),
        ("guides/ai-agents/01b-transformer-and-attention.md", "Day 1B: Transformer & Attention"),
        ("guides/ai-agents/01c-training-and-inference.md", "Day 1C: Training & Inference"),
        ("guides/ai-agents/01d-advanced-topics-appendix.md", "Day 1 Appendix: Advanced Techniques"),
        ("guides/ai-agents/02-prompt-engineering.md", "Day 2: Advanced Prompt Engineering"),
        ("guides/ai-agents/03-single-agent-react.md", "Day 3: Single-Agent Systems (ReAct)"),
        ("guides/ai-agents/04-planner-executor.md", "Day 4: Planner/Executor Architecture"),
        ("guides/ai-agents/05-rag-vector-memory.md", "Day 5: RAG + Vector Memory"),
        ("guides/ai-agents/06-multi-agent-collaboration.md", "Day 6: Multi-Agent Collaboration"),
        ("guides/ai-agents/07-final-project-copilot.md", "Day 7: Final Project"),
    ]

    # Also include appendix if it exists
    appendix_file = "guides/ai-agents/appendix-real-world-agents.md"
    if os.path.exists(appendix_file):
        ai_guide_files.append((appendix_file, "Appendix: Real-World Agents"))

    # Include Q&A reference
    qa_file = "guides/ai-agents/qa-reference.md"
    if os.path.exists(qa_file):
        ai_guide_files.append((qa_file, "Q&A Reference"))

    # Include interview cheat sheet
    cheat_file = "guides/ai-agents/interview-cheat-sheet.md"
    if os.path.exists(cheat_file):
        ai_guide_files.append((cheat_file, "Interview Cheat Sheet"))

    # Include fine-tuning guide
    ft_file = "guides/ai-agents/fine-tuning-guide.md"
    if os.path.exists(ft_file):
        ai_guide_files.append((ft_file, "Fine-Tuning Guide"))

    output_file = "AI_Agents_Guide.pdf"
    temp_md = "AI_Agents_Guide_temp.md"

    try:
        combined = []
        combined.append("# AI & Agents — Complete Guide\n\n")
        combined.append("*LLMs + Agents: Beginner → Senior in 7 Days*\n\n")
        combined.append(f"**Generated on:** {datetime.now().strftime('%B %d, %Y')}\n\n")
        combined.append("---\n\n")

        # Table of Contents
        combined.append("## Table of Contents\n\n")
        for i, (_, name) in enumerate(ai_guide_files, 1):
            anchor = name.lower()
            anchor = re.sub(r'[^a-z0-9\s-]', '', anchor)
            anchor = re.sub(r'\s+', '-', anchor).strip('-')
            combined.append(f"{i}. [{name}](#{anchor})\n")
        combined.append("\n---\n\n")

        # Concatenate each file
        files_found = 0
        for filepath, display_name in ai_guide_files:
            if not os.path.exists(filepath):
                print(f"  ⚠ Skipping (not found): {filepath}")
                continue

            files_found += 1
            print(f"  📄 Adding: {filepath}")

            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            # Remove navigation links at end (Next →, Back ←, Start →, etc.)
            content = re.sub(
                r'\n\*\*(?:Next|Start|Deep dive|← Back|Next Day|Or jump).*?\*\*\s*$',
                '', content, flags=re.MULTILINE
            )

            combined.append(content)
            combined.append("\n\n---\n\n")

        if files_found == 0:
            print("❌ No AI guide files found!")
            return False

        # Write temp markdown
        with open(temp_md, 'w', encoding='utf-8') as f:
            f.write(''.join(combined))

        print(f"\n  Combined {files_found} files into {temp_md}")
        print(f"  Generating PDF using md-to-pdf...")

        # Run md-to-pdf
        config_file = "pdf_config.js"
        cmd = ["npx", "md-to-pdf", temp_md]
        if os.path.exists(config_file):
            cmd.extend(["--config-file", config_file])

        is_windows = sys.platform == 'win32'
        process = subprocess.run(cmd, capture_output=True, text=True, shell=is_windows)

        # Cleanup temp markdown
        if os.path.exists(temp_md):
            os.remove(temp_md)

        if process.returncode == 0:
            generated_file = temp_md.replace('.md', '.pdf')
            if os.path.exists(generated_file):
                if os.path.exists(output_file):
                    os.remove(output_file)
                os.rename(generated_file, output_file)

                size_mb = os.path.getsize(output_file) / (1024 * 1024)
                print(f"\n✅ Success! AI & Agents PDF generated:")
                print(f"   📄 File: {output_file}")
                print(f"   📊 Size: {size_mb:.1f} MB")
                return True
            else:
                print(f"❌ Generated PDF not found at {generated_file}")
                return False
        else:
            print(f"❌ md-to-pdf failed:\n{process.stdout}\n{process.stderr}")
            return False

    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        # Cleanup on error
        if os.path.exists(temp_md):
            os.remove(temp_md)
        return False

def generate_ml_pdf():
    """Generate a combined PDF for the ML Engineering guide series."""
    print_header("ML Engineering Guide - PDF Generator")

    ml_guides_dir = "guides/ml-engineering"

    # Collect all chapter files in order, skipping README
    ml_guide_files = []
    if os.path.isdir(ml_guides_dir):
        for filename in sorted(os.listdir(ml_guides_dir)):
            if filename.endswith('.md') and filename.lower() != 'readme.md':
                filepath = os.path.join(ml_guides_dir, filename)
                # Derive display name from filename: strip number prefix and extension
                name_part = re.sub(r'^\d+-', '', filename.replace('.md', ''))
                display_name = name_part.replace('-', ' ').title()
                ml_guide_files.append((filepath, display_name))

    if not ml_guide_files:
        print("❌ No ML Engineering guide files found!")
        return False

    output_file = os.path.join(ml_guides_dir, "ML_Engineering_Complete_Guide.pdf")
    temp_md = "ML_Engineering_temp.md"

    try:
        combined = []
        combined.append("# ML Engineering — Complete Guide\n\n")
        combined.append("*Machine Learning Engineering: Math to Production*\n\n")
        combined.append(f"**Generated on:** {datetime.now().strftime('%B %d, %Y')}\n\n")
        combined.append("---\n\n")

        # Table of Contents
        combined.append("## Table of Contents\n\n")
        for i, (_, name) in enumerate(ml_guide_files, 1):
            anchor = name.lower()
            anchor = re.sub(r'[^a-z0-9\s-]', '', anchor)
            anchor = re.sub(r'\s+', '-', anchor).strip('-')
            combined.append(f"{i}. [{name}](#{anchor})\n")
        combined.append("\n---\n\n")

        # Concatenate each file
        files_found = 0
        for filepath, display_name in ml_guide_files:
            if not os.path.exists(filepath):
                print(f"  ⚠ Skipping (not found): {filepath}")
                continue

            files_found += 1
            print(f"  📄 Adding: {filepath}")

            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            # Remove navigation links at end (Next →, Back ←, Start →, etc.)
            content = re.sub(
                r'\n\*\*(?:Next|Start|Deep dive|← Back|Next Day|Or jump).*?\*\*\s*$',
                '', content, flags=re.MULTILINE
            )

            # Pre-process mermaid diagrams
            content = process_mermaid_diagrams(content, os.path.basename(filepath))

            combined.append(content)
            combined.append("\n\n---\n\n")

        if files_found == 0:
            print("❌ No ML Engineering guide files found!")
            return False

        # Write temp markdown
        with open(temp_md, 'w', encoding='utf-8') as f:
            f.write(''.join(combined))

        print(f"\n  Combined {files_found} files into {temp_md}")
        print(f"  Generating PDF using md-to-pdf...")

        # Run md-to-pdf
        config_file = "pdf_config.js"
        cmd = ["npx", "md-to-pdf", temp_md]
        if os.path.exists(config_file):
            cmd.extend(["--config-file", config_file])

        is_windows = sys.platform == 'win32'
        process = subprocess.run(cmd, capture_output=True, text=True, shell=is_windows)

        # Cleanup temp markdown
        if os.path.exists(temp_md):
            os.remove(temp_md)

        if process.returncode == 0:
            generated_file = temp_md.replace('.md', '.pdf')
            if os.path.exists(generated_file):
                if os.path.exists(output_file):
                    os.remove(output_file)
                os.rename(generated_file, output_file)

                size_mb = os.path.getsize(output_file) / (1024 * 1024)
                print(f"\n✅ Success! ML Engineering PDF generated:")
                print(f"   📄 File: {output_file}")
                print(f"   📊 Size: {size_mb:.1f} MB")
                return True
            else:
                print(f"❌ Generated PDF not found at {generated_file}")
                return False
        else:
            print(f"❌ md-to-pdf failed:\n{process.stdout}\n{process.stderr}")
            return False

    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        # Cleanup on error
        if os.path.exists(temp_md):
            os.remove(temp_md)
        return False


def main():
    """Main function to handle command line arguments."""
    if len(sys.argv) < 2:
        show_help()
        return
    
    command = sys.argv[1].lower()
    
    if command == "split":
        split_guides()
    elif command == "pdf-theory":
        generate_pdf("theory")
    elif command == "pdf-problems":
        generate_pdf("problems")
    elif command == "pdf-complete":
        generate_pdf("complete")
    elif command == "pdf-all":
        print_header("Generating All PDFs")
        success_count = 0
        for pdf_type in ["theory", "problems", "complete"]:
            if generate_pdf(pdf_type):
                success_count += 1
            print()
        if generate_ai_pdf():
            success_count += 1
        print()
        if generate_ml_pdf():
            success_count += 1
        print(f"✅ Generated {success_count}/5 PDFs successfully!")
    elif command == "pdf-ai":
        generate_ai_pdf()
    elif command == "pdf-ml":
        generate_ml_pdf()
    elif command == "pdf-sections":
        generate_section_pdfs()
    elif command == "visuals":
        add_enhanced_visuals()
    elif command == "images":
        update_guides_with_images()
    elif command == "help":
        show_help()
    else:
        print(f"Error: Unknown command '{command}'")
        show_help()

if __name__ == "__main__":
    main()
