"""
Generate 3Blue1Brown-style geometric diagrams for Ch01.
Dark background, colored vectors, grid lines — mimicking 3B1B visual style.
"""
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, Polygon
from matplotlib.collections import LineCollection
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), 'images')
os.makedirs(OUT_DIR, exist_ok=True)

# 3B1B-style colors
BG = '#1b1b2f'
GRID = '#2a2a4a'
IHAT = '#00ff88'   # green for i-hat
JHAT = '#ff4444'   # red for j-hat
VEC_YELLOW = '#ffdd44'
VEC_BLUE = '#44aaff'
VEC_PURPLE = '#cc66ff'
TEXT_WHITE = '#e0e0e0'
AREA_FILL = '#ffdd4433'

def setup_3b1b_style():
    plt.rcParams.update({
        'figure.facecolor': BG, 'axes.facecolor': BG,
        'text.color': TEXT_WHITE, 'axes.labelcolor': TEXT_WHITE,
        'xtick.color': TEXT_WHITE, 'ytick.color': TEXT_WHITE,
        'axes.edgecolor': '#444466',
        'font.size': 12, 'axes.titlesize': 14, 'axes.titleweight': 'bold',
        'figure.dpi': 150, 'savefig.bbox': 'tight', 'savefig.pad_inches': 0.3,
        'savefig.facecolor': BG,
    })

def draw_grid(ax, xlim=(-1, 5), ylim=(-1, 5), transform=None, color=GRID, alpha=0.3):
    """Draw a grid, optionally transformed by a 2x2 matrix."""
    lines = []
    for i in range(int(xlim[0])-2, int(xlim[1])+3):
        # vertical lines
        pts = np.array([[i, ylim[0]-2], [i, ylim[1]+2]], dtype=float)
        if transform is not None:
            pts = (transform @ pts.T).T
        lines.append(pts)
        # horizontal lines
        pts = np.array([[xlim[0]-2, i], [xlim[1]+2, i]], dtype=float)
        if transform is not None:
            pts = (transform @ pts.T).T
        lines.append(pts)
    lc = LineCollection(lines, colors=color, linewidths=0.8, alpha=alpha)
    ax.add_collection(lc)

def draw_vector(ax, v, color, label=None, lw=3, head_width=0.15):
    ax.annotate('', xy=v, xytext=(0, 0),
                arrowprops=dict(arrowstyle='->', color=color, lw=lw,
                                mutation_scale=15))
    if label:
        offset = np.array([0.15, 0.15])
        if v[0] < 0: offset[0] = -0.5
        if v[1] < 0: offset[1] = -0.4
        ax.text(v[0] + offset[0], v[1] + offset[1], label,
                color=color, fontsize=13, fontweight='bold')

def save(fig, name):
    fig.savefig(os.path.join(OUT_DIR, name))
    plt.close(fig)
    print(f'  -> {name}')


# ===================================================================
# 1. BASIS VECTORS & COORDINATES
# ===================================================================
def basis_vectors():
    fig, ax = plt.subplots(figsize=(8, 7))
    ax.set_title('Coordinates Are Instructions: [3, 2] = 3î + 2ĵ', color=TEXT_WHITE)
    draw_grid(ax, xlim=(-1, 5), ylim=(-1, 4))

    # Basis vectors
    draw_vector(ax, [1, 0], IHAT, 'î', lw=3.5)
    draw_vector(ax, [0, 1], JHAT, 'ĵ', lw=3.5)

    # Show 3*i-hat steps
    for i in range(3):
        ax.annotate('', xy=[i+1, 0], xytext=[i, 0],
                     arrowprops=dict(arrowstyle='->', color=IHAT, lw=2, alpha=0.4))
    ax.text(1.5, -0.4, '3 steps along î', color=IHAT, fontsize=10, ha='center')

    # Show 2*j-hat steps
    for j in range(2):
        ax.annotate('', xy=[3, j+1], xytext=[3, j],
                     arrowprops=dict(arrowstyle='->', color=JHAT, lw=2, alpha=0.4))
    ax.text(3.5, 0.8, '2 steps\nalong ĵ', color=JHAT, fontsize=10)

    # Result vector
    draw_vector(ax, [3, 2], VEC_YELLOW, '[3, 2]', lw=3)
    ax.plot(3, 2, 'o', color=VEC_YELLOW, ms=8, zorder=5)

    # Dashed lines showing components
    ax.plot([3, 3], [0, 2], '--', color=JHAT, alpha=0.3, lw=1)
    ax.plot([0, 3], [2, 2], '--', color=IHAT, alpha=0.3, lw=1)

    ax.text(0.5, 3.3,
            'A vector is NOT just a list of numbers.\n'
            'It\'s an arrow from the origin.\n'
            'The numbers are instructions:\n'
            '"Walk 3 along î, then 2 along ĵ"',
            color=TEXT_WHITE, fontsize=10,
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))

    ax.set_xlim(-1, 5.5); ax.set_ylim(-1, 4.5)
    ax.set_aspect('equal')
    ax.set_xlabel('x'); ax.set_ylabel('y')
    save(fig, 'ch01-3b1b-basis-vectors.png')


# ===================================================================
# 2. LINEAR TRANSFORMATION = MOVING THE GRID
# ===================================================================
def linear_transform_grid():
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    M = np.array([[2, 1], [0, 1.5]])  # transformation matrix

    for idx, (ax, title, transform) in enumerate(zip(
        axes,
        ['Before: Standard Grid', f'After: Columns of W = where î, ĵ land'],
        [None, M]
    )):
        ax.set_title(title, color=TEXT_WHITE, fontsize=12)
        draw_grid(ax, xlim=(-1, 4), ylim=(-1, 4), transform=transform, alpha=0.25)

        if transform is None:
            draw_vector(ax, [1, 0], IHAT, 'î = [1,0]', lw=3)
            draw_vector(ax, [0, 1], JHAT, 'ĵ = [0,1]', lw=3)
            # Show unit square
            sq = np.array([[0,0],[1,0],[1,1],[0,1],[0,0]])
            ax.fill(sq[:,0], sq[:,1], alpha=0.15, color=VEC_YELLOW)
            ax.plot(sq[:,0], sq[:,1], '-', color=VEC_YELLOW, alpha=0.4, lw=1)
        else:
            new_i = M @ np.array([1, 0])
            new_j = M @ np.array([0, 1])
            draw_vector(ax, new_i, IHAT, f'Wî = [{new_i[0]:.0f},{new_i[1]:.0f}]', lw=3)
            draw_vector(ax, new_j, JHAT, f'Wĵ = [{new_j[0]:.0f},{new_j[1]:.1f}]', lw=3)
            # Transformed unit square
            sq = np.array([[0,0],[1,0],[1,1],[0,1],[0,0]])
            sq_t = (M @ sq.T).T
            ax.fill(sq_t[:,0], sq_t[:,1], alpha=0.15, color=VEC_YELLOW)
            ax.plot(sq_t[:,0], sq_t[:,1], '-', color=VEC_YELLOW, alpha=0.4, lw=1)

            # Show matrix
            ax.text(0.02, 0.02,
                    f'W = [{M[0,0]:.0f}  {M[0,1]:.0f}]\n'
                    f'    [{M[1,0]:.0f}  {M[1,1]:.1f}]\n\n'
                    f'Column 1 = where î lands\n'
                    f'Column 2 = where ĵ lands',
                    transform=ax.transAxes, fontsize=10, color=TEXT_WHITE, va='bottom',
                    bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))

        ax.set_xlim(-1.5, 5); ax.set_ylim(-1.5, 5)
        ax.set_aspect('equal')
        ax.axhline(0, color='#444466', lw=0.5)
        ax.axvline(0, color='#444466', lw=0.5)

    fig.suptitle('A Matrix IS a Transformation of Space',
                 color=VEC_YELLOW, fontsize=15, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch01-3b1b-linear-transform-grid.png')


# ===================================================================
# 3. MATRIX MULTIPLY = COMPOSITION
# ===================================================================
def composition():
    fig, axes = plt.subplots(1, 3, figsize=(18, 5.5))

    A = np.array([[0, -1], [1, 0]])     # 90° rotation
    B = np.array([[2, 0], [0, 0.5]])    # scale x2 horizontal, 0.5 vertical
    BA = B @ A                           # combined: first A then B

    configs = [
        ('Step 1: Rotate 90° (A)', A, 'First apply A'),
        ('Step 2: Scale (B)', B, 'Then apply B'),
        (f'Combined: B × A', BA, 'B × A = one matrix!'),
    ]

    for ax, (title, M, note) in zip(axes, configs):
        ax.set_title(title, color=TEXT_WHITE, fontsize=11)
        draw_grid(ax, xlim=(-3, 3), ylim=(-3, 3), transform=M, alpha=0.2)
        draw_grid(ax, xlim=(-3, 3), ylim=(-3, 3), transform=None, color='#333355', alpha=0.1)

        new_i = M @ np.array([1, 0])
        new_j = M @ np.array([0, 1])
        draw_vector(ax, new_i, IHAT, lw=2.5)
        draw_vector(ax, new_j, JHAT, lw=2.5)

        # Transformed unit square
        sq = np.array([[0,0],[1,0],[1,1],[0,1],[0,0]])
        sq_t = (M @ sq.T).T
        ax.fill(sq_t[:,0], sq_t[:,1], alpha=0.15, color=VEC_YELLOW)

        ax.text(0.5, 0.02, f'[{M[0,0]:+.1f} {M[0,1]:+.1f}]\n[{M[1,0]:+.1f} {M[1,1]:+.1f}]\n{note}',
                transform=ax.transAxes, fontsize=9, ha='center', color=TEXT_WHITE, va='bottom',
                bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))

        ax.set_xlim(-3, 3); ax.set_ylim(-3, 3)
        ax.set_aspect('equal')
        ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    fig.suptitle('Matrix Multiplication = Applying One Transformation After Another',
                 color=VEC_YELLOW, fontsize=14, fontweight='bold', y=1.03)
    fig.tight_layout()
    save(fig, 'ch01-3b1b-composition.png')


# ===================================================================
# 4. DETERMINANT = AREA SCALING
# ===================================================================
def determinant():
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))

    matrices = [
        ('det = 3.0 (area tripled)', np.array([[2, 1], [0, 1.5]])),
        ('det = 0 (squished flat!)', np.array([[2, 1], [4, 2]])),
        ('det = -1 (flipped!)', np.array([[0, 1], [1, 0]])),
    ]

    for ax, (title, M) in zip(axes, matrices):
        det = np.linalg.det(M)
        ax.set_title(title, color=TEXT_WHITE, fontsize=11)
        draw_grid(ax, xlim=(-2, 4), ylim=(-2, 4), transform=M, alpha=0.15)

        # Original unit square (faint)
        sq = np.array([[0,0],[1,0],[1,1],[0,1],[0,0]])
        ax.fill(sq[:,0], sq[:,1], alpha=0.08, color='white')
        ax.plot(sq[:,0], sq[:,1], '--', color='white', alpha=0.2, lw=1)

        # Transformed parallelogram
        sq_t = (M @ sq.T).T
        fill_color = '#ff444444' if det < 0 else ('#88888844' if abs(det) < 0.01 else AREA_FILL)
        ax.fill(sq_t[:,0], sq_t[:,1], alpha=0.3, color=VEC_YELLOW if det > 0.01 else ('#ff4444' if det < 0 else '#888888'))
        ax.plot(sq_t[:,0], sq_t[:,1], '-', color=VEC_YELLOW, alpha=0.6, lw=1.5)

        new_i = M @ np.array([1, 0])
        new_j = M @ np.array([0, 1])
        draw_vector(ax, new_i, IHAT, lw=2.5)
        draw_vector(ax, new_j, JHAT, lw=2.5)

        ax.text(0.5, 0.02,
                f'det(W) = {det:.1f}\n'
                f'Original area: 1\n'
                f'New area: |{det:.1f}| = {abs(det):.1f}',
                transform=ax.transAxes, fontsize=9, ha='center', color=TEXT_WHITE, va='bottom',
                bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))

        ax.set_xlim(-2, 5); ax.set_ylim(-2, 5)
        ax.set_aspect('equal')
        ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    fig.suptitle('Determinant = How Much the Transformation Scales Area',
                 color=VEC_YELLOW, fontsize=14, fontweight='bold', y=1.03)
    fig.tight_layout()
    save(fig, 'ch01-3b1b-determinant.png')


# ===================================================================
# 5. DOT PRODUCT DUALITY
# ===================================================================
def dot_product_duality():
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    # Left: algebraic view
    ax = axes[0]
    ax.set_title('Algebraic: Multiply & Sum', color=TEXT_WHITE)
    draw_grid(ax, xlim=(-1, 5), ylim=(-1, 4), alpha=0.1)

    a = np.array([4, 2])
    b = np.array([1, 3])
    draw_vector(ax, a, VEC_BLUE, 'a = [4, 2]', lw=3)
    draw_vector(ax, b, JHAT, 'b = [1, 3]', lw=3)

    ax.text(0.5, 0.95,
            'a · b = 4×1 + 2×3 = 10\n\n'
            'Just multiply matching\n'
            'entries and add up.',
            transform=ax.transAxes, fontsize=11, ha='center', va='top', color=TEXT_WHITE,
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))

    ax.set_xlim(-1, 6); ax.set_ylim(-1, 5)
    ax.set_aspect('equal')
    ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    # Right: geometric (projection) view
    ax = axes[1]
    ax.set_title('Geometric: Project & Multiply Lengths', color=TEXT_WHITE)
    draw_grid(ax, xlim=(-1, 5), ylim=(-1, 4), alpha=0.1)

    draw_vector(ax, a, VEC_BLUE, 'a', lw=3)
    draw_vector(ax, b, JHAT, 'b', lw=3)

    # Projection of a onto b
    proj_scalar = np.dot(a, b) / np.linalg.norm(b)
    proj_vec = proj_scalar * b / np.linalg.norm(b)
    ax.plot([a[0], proj_vec[0]], [a[1], proj_vec[1]], '--', color=VEC_PURPLE, lw=1.5, alpha=0.7)
    draw_vector(ax, proj_vec, VEC_PURPLE, lw=2)
    ax.text(proj_vec[0]-0.8, proj_vec[1]+0.2, f'proj = {proj_scalar:.1f}',
            color=VEC_PURPLE, fontsize=10)

    # Right angle marker
    perp = a - proj_vec
    ax.plot([a[0], proj_vec[0]], [a[1], proj_vec[1]], '--', color='#666688', lw=1)

    ax.text(0.5, 0.95,
            'a · b = |proj_b(a)| × ||b||\n'
            f'     = {proj_scalar:.1f} × {np.linalg.norm(b):.1f} = {np.dot(a,b):.0f}\n\n'
            'Drop a perpendicular from a\n'
            'onto b\'s direction line.\n'
            'That shadow length × ||b|| = dot product!',
            transform=ax.transAxes, fontsize=10, ha='center', va='top', color=TEXT_WHITE,
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))

    ax.set_xlim(-1, 6); ax.set_ylim(-1, 5)
    ax.set_aspect('equal')
    ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    fig.suptitle('The Dot Product Has Two Faces (Duality)',
                 color=VEC_YELLOW, fontsize=14, fontweight='bold', y=1.03)
    fig.tight_layout()
    save(fig, 'ch01-3b1b-dot-product-duality.png')


# ===================================================================
# 6. EIGENVECTORS STAY ON THEIR SPAN
# ===================================================================
def eigen_span():
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    M = np.array([[3, 1], [0, 2]])
    eigenvalues, eigenvectors = np.linalg.eig(M)

    # Left: regular vectors get knocked off
    ax = axes[0]
    ax.set_title('Regular Vectors: Knocked Off Their Line', color=TEXT_WHITE)
    draw_grid(ax, xlim=(-3, 5), ylim=(-3, 5), alpha=0.1)

    test_vectors = [np.array([1, 1]), np.array([1, -1]), np.array([-1, 1])]
    for v in test_vectors:
        Mv = M @ v
        # Original direction line (dashed)
        t = np.linspace(-5, 5, 2)
        ax.plot(v[0]*t, v[1]*t, '--', color='#555577', lw=0.8, alpha=0.4)
        draw_vector(ax, v, '#888899', lw=2)
        draw_vector(ax, Mv, VEC_YELLOW, lw=2)
        # Arrow from tip to transformed tip
        ax.annotate('', xy=Mv, xytext=v,
                     arrowprops=dict(arrowstyle='->', color='#ffffff44', lw=1, connectionstyle='arc3,rad=0.2'))

    ax.text(0.5, 0.02, 'Gray = original, Yellow = after W\nVectors get rotated off their line!',
            transform=ax.transAxes, fontsize=10, ha='center', color=TEXT_WHITE, va='bottom',
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))
    ax.set_xlim(-4, 6); ax.set_ylim(-4, 6); ax.set_aspect('equal')
    ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    # Right: eigenvectors stay on span
    ax = axes[1]
    ax.set_title('Eigenvectors: Stay on Their Line (Just Stretched)', color=TEXT_WHITE)
    draw_grid(ax, xlim=(-3, 5), ylim=(-3, 5), alpha=0.1)

    colors_ev = [IHAT, JHAT]
    for i in range(2):
        ev = eigenvectors[:, i] * 1.5
        Mev = M @ ev
        # Span line
        t = np.linspace(-5, 5, 2)
        ax.plot(ev[0]*t/1.5, ev[1]*t/1.5, '--', color=colors_ev[i], lw=1, alpha=0.3)
        draw_vector(ax, ev, colors_ev[i], f'v (λ={eigenvalues[i]:.0f})', lw=2.5)
        draw_vector(ax, Mev, colors_ev[i], f'Wv = {eigenvalues[i]:.0f}v', lw=2.5)
        # Show it's just scaled
        ax.annotate('', xy=Mev, xytext=ev,
                     arrowprops=dict(arrowstyle='->', color=colors_ev[i], lw=1.5, alpha=0.5,
                                     connectionstyle='arc3,rad=0.15'))

    ax.text(0.5, 0.02,
            'Wv = λv\n'
            'Eigenvectors only get SCALED,\n'
            'never rotated off their span!',
            transform=ax.transAxes, fontsize=10, ha='center', color=TEXT_WHITE, va='bottom',
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))
    ax.set_xlim(-4, 6); ax.set_ylim(-4, 6); ax.set_aspect('equal')
    ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    fig.suptitle('Eigenvectors Are the "Special Directions" of a Transformation',
                 color=VEC_YELLOW, fontsize=14, fontweight='bold', y=1.03)
    fig.tight_layout()
    save(fig, 'ch01-3b1b-eigen-span.png')


# ===================================================================
# 7. CHANGE OF BASIS
# ===================================================================
def change_of_basis():
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    # Standard basis
    ax = axes[0]
    ax.set_title('Standard Basis: î = [1,0], ĵ = [0,1]', color=TEXT_WHITE)
    draw_grid(ax, xlim=(-1, 5), ylim=(-1, 4), alpha=0.2)
    draw_vector(ax, [1, 0], IHAT, 'î', lw=3)
    draw_vector(ax, [0, 1], JHAT, 'ĵ', lw=3)
    v = np.array([3, 2])
    draw_vector(ax, v, VEC_YELLOW, '[3, 2]', lw=3)
    ax.plot(v[0], v[1], 'o', color=VEC_YELLOW, ms=8)
    ax.text(0.5, 0.02, 'In standard coordinates:\nv = 3î + 2ĵ = [3, 2]',
            transform=ax.transAxes, fontsize=10, ha='center', color=TEXT_WHITE, va='bottom',
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))
    ax.set_xlim(-1, 5.5); ax.set_ylim(-1, 4.5); ax.set_aspect('equal')
    ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    # Different basis
    ax = axes[1]
    b1 = np.array([2, 1])
    b2 = np.array([-0.5, 1])
    B = np.column_stack([b1, b2])
    v_new = np.linalg.solve(B, v)

    ax.set_title(f'New Basis: b₁ = [2,1], b₂ = [-0.5,1]', color=TEXT_WHITE)
    # Draw new basis grid
    draw_grid(ax, xlim=(-2, 4), ylim=(-2, 4), transform=B, color='#3a3a5a', alpha=0.2)
    draw_vector(ax, b1, IHAT, 'b₁', lw=3)
    draw_vector(ax, b2, JHAT, 'b₂', lw=3)
    draw_vector(ax, v, VEC_YELLOW, f'[{v_new[0]:.1f}, {v_new[1]:.1f}]', lw=3)
    ax.plot(v[0], v[1], 'o', color=VEC_YELLOW, ms=8)

    # Show components in new basis
    comp1 = v_new[0] * b1
    ax.annotate('', xy=comp1, xytext=(0, 0),
                 arrowprops=dict(arrowstyle='->', color=IHAT, lw=1.5, alpha=0.4))
    ax.annotate('', xy=v, xytext=comp1,
                 arrowprops=dict(arrowstyle='->', color=JHAT, lw=1.5, alpha=0.4))

    ax.text(0.5, 0.02,
            f'Same point in space!\n'
            f'New coordinates: [{v_new[0]:.1f}, {v_new[1]:.1f}]\n'
            f'v = {v_new[0]:.1f}·b₁ + {v_new[1]:.1f}·b₂',
            transform=ax.transAxes, fontsize=10, ha='center', color=TEXT_WHITE, va='bottom',
            bbox=dict(boxstyle='round', facecolor='#2a2a4a', edgecolor='#444466'))
    ax.set_xlim(-1, 5.5); ax.set_ylim(-1, 4.5); ax.set_aspect('equal')
    ax.axhline(0, color='#444466', lw=0.5); ax.axvline(0, color='#444466', lw=0.5)

    fig.suptitle('Change of Basis: Same Point, Different Instructions',
                 color=VEC_YELLOW, fontsize=14, fontweight='bold', y=1.03)
    fig.tight_layout()
    save(fig, 'ch01-3b1b-change-of-basis.png')


# ===================================================================
# RUN ALL
# ===================================================================
if __name__ == '__main__':
    setup_3b1b_style()
    print('Generating 3Blue1Brown-style diagrams...\n')
    basis_vectors()
    linear_transform_grid()
    composition()
    determinant()
    dot_product_duality()
    eigen_span()
    change_of_basis()
    print(f'\nDone! 7 new 3B1B-style images.')
