"""
Generate all geometric diagrams for the ML Engineering Guide.
Usage: python generate_diagrams.py
Output: guides/ml-engineering/images/*.png
"""
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, Circle, Polygon
from matplotlib.colors import LinearSegmentedColormap
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), 'images')
os.makedirs(OUT_DIR, exist_ok=True)

# Consistent style
COLORS = {
    'blue': '#2563EB', 'red': '#DC2626', 'green': '#16A34A',
    'orange': '#EA580C', 'purple': '#9333EA', 'gray': '#6B7280',
    'light_blue': '#DBEAFE', 'light_red': '#FEE2E2', 'light_green': '#DCFCE7',
    'light_purple': '#F3E8FF', 'light_orange': '#FED7AA',
}

def setup_style():
    plt.rcParams.update({
        'figure.facecolor': 'white', 'axes.facecolor': 'white',
        'font.size': 11, 'axes.titlesize': 13, 'axes.titleweight': 'bold',
        'axes.grid': True, 'grid.alpha': 0.3, 'grid.linestyle': '--',
        'figure.dpi': 150, 'savefig.bbox': 'tight', 'savefig.pad_inches': 0.2,
    })

def save(fig, name):
    fig.savefig(os.path.join(OUT_DIR, name))
    plt.close(fig)
    print(f'  -> {name}')


# ===================================================================
# CHAPTER 01 — Vectors & Linear Algebra
# ===================================================================

def ch01_vectors_2d():
    fig, ax = plt.subplots(figsize=(6, 5))
    ax.set_title('Vectors as Points in Feature Space')
    # User vectors
    users = {'Alice\n(25, 80k)': (25, 80), 'Bob\n(30, 60k)': (30, 60), 'Carol\n(45, 120k)': (45, 120)}
    for label, (x, y) in users.items():
        ax.annotate('', xy=(x, y), xytext=(0, 0),
                     arrowprops=dict(arrowstyle='->', color=COLORS['blue'], lw=2))
        ax.plot(x, y, 'o', color=COLORS['blue'], ms=8)
        ax.annotate(label, (x, y), textcoords='offset points', xytext=(8, 5), fontsize=9)
    ax.set_xlabel('Age'); ax.set_ylabel('Salary (thousands)')
    ax.set_xlim(-2, 55); ax.set_ylim(-5, 140)
    ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
    save(fig, 'ch01-vectors-2d.png')

def ch01_dot_product_projection():
    fig, axes = plt.subplots(1, 2, figsize=(11, 5))
    # Left: dot product = projection * length
    ax = axes[0]
    ax.set_title('Dot Product = Projection × Length')
    A = np.array([4, 2]); B = np.array([3, 4])
    proj_scalar = np.dot(A, B) / np.linalg.norm(B)
    proj_vec = proj_scalar * B / np.linalg.norm(B)
    ax.annotate('', xy=A, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=COLORS['blue'], lw=2.5))
    ax.annotate('', xy=B, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=COLORS['red'], lw=2.5))
    ax.plot([A[0], proj_vec[0]], [A[1], proj_vec[1]], '--', color=COLORS['gray'], lw=1.5)
    ax.annotate('', xy=proj_vec, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=COLORS['green'], lw=2))
    ax.annotate('A = [4, 2]', A, textcoords='offset points', xytext=(5, -12), color=COLORS['blue'], fontweight='bold')
    ax.annotate('B = [3, 4]', B, textcoords='offset points', xytext=(5, 5), color=COLORS['red'], fontweight='bold')
    ax.annotate(f'proj = {proj_scalar:.1f}', proj_vec, textcoords='offset points', xytext=(-15, -18), color=COLORS['green'], fontsize=9)
    ax.text(0.05, 0.95, 'A · B = 4×3 + 2×4 = 20', transform=ax.transAxes, fontsize=10, va='top',
            bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.set_xlim(-0.5, 5.5); ax.set_ylim(-0.5, 5.5); ax.set_aspect('equal')
    ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)

    # Right: large vs small dot product
    ax = axes[1]
    ax.set_title('Dot Product → Similarity')
    A = np.array([3, 1])
    similar = np.array([3.5, 1.5]); different = np.array([-1, 3])
    for v, label, col in [(A, 'A', COLORS['blue']), (similar, 'B (similar)', COLORS['green']), (different, 'C (different)', COLORS['red'])]:
        ax.annotate('', xy=v, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=col, lw=2.5))
        ax.annotate(label, v, textcoords='offset points', xytext=(5, 5), color=col, fontweight='bold')
    dp1 = np.dot(A, similar); dp2 = np.dot(A, different)
    ax.text(0.05, 0.95, f'A·B = {dp1:.0f} (high → similar)\nA·C = {dp2:.0f} (low → different)',
            transform=ax.transAxes, fontsize=10, va='top', bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.set_xlim(-2, 5); ax.set_ylim(-1, 4); ax.set_aspect('equal')
    ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
    fig.tight_layout()
    save(fig, 'ch01-dot-product.png')

def ch01_cosine_similarity():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.5))
    cases = [
        ('Same Direction\ncos(θ) ≈ 1.0', np.array([3, 1]), np.array([4, 1.5]), COLORS['green']),
        ('Perpendicular\ncos(θ) ≈ 0.0', np.array([3, 0]), np.array([0, 3]), COLORS['orange']),
        ('Opposite Direction\ncos(θ) ≈ -1.0', np.array([3, 1]), np.array([-3, -1]), COLORS['red']),
    ]
    for ax, (title, A, B, col) in zip(axes, cases):
        ax.set_title(title, fontsize=12)
        ax.annotate('', xy=A, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=COLORS['blue'], lw=3))
        ax.annotate('', xy=B, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=col, lw=3))
        ax.annotate('A', A, textcoords='offset points', xytext=(5, 5), color=COLORS['blue'], fontweight='bold', fontsize=12)
        ax.annotate('B', B, textcoords='offset points', xytext=(5, 5), color=col, fontweight='bold', fontsize=12)
        # Draw angle arc
        angle_A = np.arctan2(A[1], A[0]); angle_B = np.arctan2(B[1], B[0])
        theta = np.linspace(min(angle_A, angle_B), max(angle_A, angle_B), 30)
        r = 0.8
        ax.plot(r*np.cos(theta), r*np.sin(theta), '-', color=COLORS['gray'], lw=1.5)
        cos_val = np.dot(A, B) / (np.linalg.norm(A) * np.linalg.norm(B))
        mid_angle = (angle_A + angle_B) / 2
        ax.text(1.2*np.cos(mid_angle), 1.2*np.sin(mid_angle), f'θ', fontsize=12, ha='center', color=COLORS['gray'])
        ax.set_xlim(-4.5, 5.5); ax.set_ylim(-2.5, 4.5); ax.set_aspect('equal')
        ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
    fig.suptitle('Cosine Similarity — Measures the Angle Between Vectors', fontsize=14, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch01-cosine-similarity.png')

def ch01_matrix_transform():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.5))
    # Original unit square
    square = np.array([[0,0],[1,0],[1,1],[0,1],[0,0]]).T
    transforms = [
        ('Scaling (2x, 0.5y)', np.array([[2, 0], [0, 0.5]])),
        ('Rotation (45°)', np.array([[np.cos(np.pi/4), -np.sin(np.pi/4)], [np.sin(np.pi/4), np.cos(np.pi/4)]])),
        ('Shear', np.array([[1, 0.5], [0, 1]])),
    ]
    for ax, (title, M) in zip(axes, transforms):
        ax.set_title(title)
        # Original
        ax.plot(square[0], square[1], '-', color=COLORS['gray'], lw=1.5, alpha=0.5, label='Original')
        ax.fill(square[0][:-1], square[1][:-1], alpha=0.1, color=COLORS['gray'])
        # Transformed
        transformed = M @ square
        ax.plot(transformed[0], transformed[1], '-', color=COLORS['blue'], lw=2.5, label='Transformed')
        ax.fill(transformed[0][:-1], transformed[1][:-1], alpha=0.2, color=COLORS['blue'])
        # Show matrix
        ax.text(0.02, 0.98, f'W = [{M[0,0]:.2f}  {M[0,1]:.2f}]\n    [{M[1,0]:.2f}  {M[1,1]:.2f}]',
                transform=ax.transAxes, fontsize=9, va='top', fontfamily='monospace',
                bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
        ax.set_xlim(-1.5, 2.5); ax.set_ylim(-1, 2); ax.set_aspect('equal')
        ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
        ax.legend(fontsize=8, loc='lower right')
    fig.suptitle('Matrix = Geometric Transformation', fontsize=14, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch01-matrix-transforms.png')

def ch01_eigenvectors():
    fig, ax = plt.subplots(figsize=(7, 6))
    ax.set_title('Eigenvectors Don\'t Change Direction Under Transformation')
    M = np.array([[2, 1], [1, 3]])
    eigenvalues, eigenvectors = np.linalg.eig(M)
    # Draw some regular vectors and their transforms
    for angle in np.linspace(0, 2*np.pi, 12, endpoint=False):
        v = np.array([np.cos(angle), np.sin(angle)]) * 1.5
        Mv = M @ v
        ax.annotate('', xy=v, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=COLORS['gray'], lw=1, alpha=0.3))
        ax.annotate('', xy=Mv, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=COLORS['gray'], lw=1, alpha=0.3, linestyle='--'))
    # Draw eigenvectors (before and after — same direction!)
    for i, col in enumerate([COLORS['blue'], COLORS['red']]):
        ev = eigenvectors[:, i] * 1.5
        Mev = M @ ev  # = eigenvalue * ev — same direction!
        ax.annotate('', xy=ev, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=col, lw=3))
        ax.annotate('', xy=Mev, xytext=(0, 0), arrowprops=dict(arrowstyle='->', color=col, lw=3, linestyle='--'))
        ax.annotate(f'v{i+1} (λ={eigenvalues[i]:.1f})', ev, textcoords='offset points', xytext=(5, 8), color=col, fontweight='bold')
        ax.annotate(f'Mv{i+1} = {eigenvalues[i]:.1f}·v{i+1}', Mev, textcoords='offset points', xytext=(5, -12), color=col, fontsize=9)
    ax.text(0.02, 0.02, 'Solid = original vectors\nDashed = after transformation\nEigenvectors (colored) only stretch, not rotate',
            transform=ax.transAxes, fontsize=9, va='bottom', bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    lim = 6; ax.set_xlim(-lim, lim); ax.set_ylim(-lim, lim); ax.set_aspect('equal')
    ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
    save(fig, 'ch01-eigenvectors.png')


# ===================================================================
# CHAPTER 02 — Calculus & Optimization
# ===================================================================

def ch02_gradient_descent_2d():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    x = np.linspace(-4, 4, 200)
    loss = x**2 + 0.5*np.sin(3*x)

    # Left: simple parabola
    ax = axes[0]; ax.set_title('Gradient Descent on a Loss Curve')
    ax.plot(x, x**2, color=COLORS['blue'], lw=2)
    # Steps
    pos = 3.5; lr = 0.3
    for i in range(6):
        new_pos = pos - lr * 2 * pos
        ax.plot(pos, pos**2, 'o', color=COLORS['red'], ms=8, zorder=5)
        if i < 5:
            ax.annotate('', xy=(new_pos, new_pos**2), xytext=(pos, pos**2),
                         arrowprops=dict(arrowstyle='->', color=COLORS['red'], lw=1.5))
        pos = new_pos
    ax.set_xlabel('Parameter θ'); ax.set_ylabel('Loss L(θ)')
    ax.text(0.05, 0.95, 'θ ← θ - η · ∂L/∂θ\n\nEach step moves\ndownhill along gradient',
            transform=ax.transAxes, fontsize=10, va='top', bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))

    # Right: learning rate comparison
    ax = axes[1]; ax.set_title('Learning Rate Effect')
    ax.plot(x, x**2, color=COLORS['gray'], lw=2, alpha=0.5)
    for lr, col, label in [(0.05, COLORS['blue'], 'η=0.05 (too small)'),
                            (0.3, COLORS['green'], 'η=0.3 (just right)'),
                            (0.9, COLORS['red'], 'η=0.9 (too large)')]:
        pos = 3.5; path = [(pos, pos**2)]
        for _ in range(8):
            pos = pos - lr * 2 * pos
            path.append((pos, pos**2))
        path = np.array(path)
        ax.plot(path[:, 0], path[:, 1], 'o-', color=col, ms=5, lw=1.5, label=label, alpha=0.8)
    ax.set_xlabel('Parameter θ'); ax.set_ylabel('Loss L(θ)')
    ax.legend(fontsize=9)
    fig.tight_layout()
    save(fig, 'ch02-gradient-descent.png')

def ch02_loss_surface_3d():
    fig = plt.figure(figsize=(8, 6))
    ax = fig.add_subplot(111, projection='3d')
    ax.set_title('Loss Surface with Gradient Descent Path')
    x = np.linspace(-3, 3, 50); y = np.linspace(-3, 3, 50)
    X, Y = np.meshgrid(x, y)
    Z = X**2 + Y**2 + 0.5*X*Y
    ax.plot_surface(X, Y, Z, alpha=0.3, cmap='coolwarm', edgecolor='none')
    # GD path
    pos = np.array([2.5, 2.5]); lr = 0.15; path = [pos.copy()]
    for _ in range(15):
        grad = np.array([2*pos[0] + 0.5*pos[1], 2*pos[1] + 0.5*pos[0]])
        pos = pos - lr * grad
        path.append(pos.copy())
    path = np.array(path)
    z_path = path[:, 0]**2 + path[:, 1]**2 + 0.5*path[:, 0]*path[:, 1]
    ax.plot(path[:, 0], path[:, 1], z_path, 'o-', color=COLORS['red'], ms=4, lw=2, zorder=10)
    ax.set_xlabel('θ₁'); ax.set_ylabel('θ₂'); ax.set_zlabel('Loss')
    ax.view_init(elev=35, azim=225)
    save(fig, 'ch02-loss-surface-3d.png')

def ch02_saddle_point():
    fig = plt.figure(figsize=(8, 6))
    ax = fig.add_subplot(111, projection='3d')
    ax.set_title('Saddle Point — Goes Down in One Direction, Up in Another')
    x = np.linspace(-2, 2, 50); y = np.linspace(-2, 2, 50)
    X, Y = np.meshgrid(x, y)
    Z = X**2 - Y**2
    ax.plot_surface(X, Y, Z, alpha=0.4, cmap='RdYlBu', edgecolor='none')
    ax.plot([0], [0], [0], 'o', color=COLORS['red'], ms=10, zorder=10)
    ax.text(0, 0, 0.5, 'Saddle\nPoint', fontsize=10, ha='center', color=COLORS['red'])
    ax.set_xlabel('θ₁ (curves up)'); ax.set_ylabel('θ₂ (curves down)'); ax.set_zlabel('Loss')
    ax.view_init(elev=25, azim=225)
    save(fig, 'ch02-saddle-point.png')


# ===================================================================
# CHAPTER 03 — Probability & Statistics
# ===================================================================

def ch03_normal_distribution():
    fig, ax = plt.subplots(figsize=(9, 5))
    ax.set_title('Normal Distribution — The 68-95-99.7 Rule')
    x = np.linspace(-4, 4, 300)
    y = np.exp(-x**2 / 2) / np.sqrt(2 * np.pi)
    ax.plot(x, y, color=COLORS['blue'], lw=2.5)
    # Shade bands
    bands = [(1, COLORS['light_blue'], '68%'), (2, COLORS['light_green'], '95%'), (3, COLORS['light_purple'], '99.7%')]
    for sigma, col, pct in reversed(bands):
        mask = np.abs(x) <= sigma
        ax.fill_between(x[mask], y[mask], alpha=0.3, color=col, label=f'±{sigma}σ = {pct}')
    for s in [1, 2, 3]:
        ax.axvline(s, color=COLORS['gray'], lw=0.8, ls='--', alpha=0.5)
        ax.axvline(-s, color=COLORS['gray'], lw=0.8, ls='--', alpha=0.5)
        ax.text(s, -0.01, f'+{s}σ', ha='center', fontsize=8)
        ax.text(-s, -0.01, f'-{s}σ', ha='center', fontsize=8)
    ax.axvline(0, color='black', lw=1); ax.text(0, -0.02, 'μ', ha='center', fontsize=10, fontweight='bold')
    ax.set_xlabel('x'); ax.set_ylabel('p(x)')
    ax.text(0.98, 0.95, r'$p(x) = \frac{1}{\sigma\sqrt{2\pi}} e^{-\frac{(x-\mu)^2}{2\sigma^2}}$',
            transform=ax.transAxes, fontsize=13, ha='right', va='top',
            bbox=dict(boxstyle='round', facecolor='white', edgecolor=COLORS['blue']))
    ax.legend(fontsize=10)
    save(fig, 'ch03-normal-distribution.png')

def ch03_distributions():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.5))
    # Bernoulli
    ax = axes[0]; ax.set_title('Bernoulli (p=0.7)')
    ax.bar([0, 1], [0.3, 0.7], color=[COLORS['red'], COLORS['green']], width=0.4)
    ax.set_xticks([0, 1]); ax.set_xticklabels(['0 (fail)', '1 (success)'])
    ax.set_ylabel('P(X=x)'); ax.set_ylim(0, 1)
    ax.text(0.5, 0.9, 'P(X=x) = p^x · (1-p)^(1-x)', transform=ax.transAxes, ha='center', fontsize=10,
            bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))

    # Uniform
    ax = axes[1]; ax.set_title('Uniform [a, b]')
    a, b = 1, 5; x = np.linspace(-0.5, 6.5, 200)
    y = np.where((x >= a) & (x <= b), 1/(b-a), 0)
    ax.plot(x, y, color=COLORS['blue'], lw=2.5)
    ax.fill_between(x, y, alpha=0.2, color=COLORS['blue'])
    ax.set_ylabel('p(x)')
    ax.text(0.5, 0.9, f'p(x) = 1/(b-a) = {1/(b-a):.2f}', transform=ax.transAxes, ha='center', fontsize=10,
            bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))

    # Poisson
    ax = axes[2]; ax.set_title('Poisson (λ=4)')
    from scipy.stats import poisson
    k = np.arange(0, 15)
    ax.bar(k, poisson.pmf(k, 4), color=COLORS['purple'], alpha=0.7)
    ax.set_xlabel('k (events)'); ax.set_ylabel('P(X=k)')
    ax.text(0.5, 0.9, 'P(X=k) = λ^k · e^(-λ) / k!', transform=ax.transAxes, ha='center', fontsize=10,
            bbox=dict(boxstyle='round', facecolor=COLORS['light_purple']))
    fig.tight_layout()
    save(fig, 'ch03-distributions.png')

def ch03_bayes():
    fig, ax = plt.subplots(figsize=(8, 5))
    ax.set_title("Bayes' Theorem — Updating Beliefs with Evidence")
    # Overlapping distributions
    x = np.linspace(0, 10, 300)
    prior = np.exp(-(x-3)**2 / 2) / np.sqrt(2*np.pi)
    likelihood = np.exp(-(x-6)**2 / (2*1.5**2)) / (np.sqrt(2*np.pi)*1.5)
    posterior = prior * likelihood
    posterior = posterior / np.sum(posterior * np.diff(np.append(x, x[-1]+x[1]-x[0])))  # normalize
    ax.fill_between(x, prior, alpha=0.2, color=COLORS['blue'], label='Prior P(H)')
    ax.plot(x, prior, color=COLORS['blue'], lw=2)
    ax.fill_between(x, likelihood, alpha=0.2, color=COLORS['red'], label='Likelihood P(E|H)')
    ax.plot(x, likelihood, color=COLORS['red'], lw=2)
    ax.fill_between(x, posterior, alpha=0.3, color=COLORS['purple'], label='Posterior P(H|E)')
    ax.plot(x, posterior, color=COLORS['purple'], lw=2.5)
    ax.set_xlabel('Hypothesis value'); ax.set_ylabel('Density')
    ax.text(0.98, 0.95, 'P(H|E) = P(E|H) · P(H) / P(E)\n\nPosterior ∝ Likelihood × Prior',
            transform=ax.transAxes, fontsize=10, ha='right', va='top',
            bbox=dict(boxstyle='round', facecolor=COLORS['light_purple']))
    ax.legend(fontsize=10)
    save(fig, 'ch03-bayes.png')


# ===================================================================
# CHAPTER 04 — Data Preprocessing
# ===================================================================

def ch04_normalization():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.5))
    np.random.seed(42)
    age = np.random.normal(35, 10, 100)
    salary = np.random.normal(75000, 20000, 100)

    ax = axes[0]; ax.set_title('Raw Data (different scales)')
    ax.scatter(age, salary, alpha=0.5, color=COLORS['blue'], s=30)
    ax.set_xlabel('Age (20-60)'); ax.set_ylabel('Salary (30k-120k)')

    ax = axes[1]; ax.set_title('Min-Max Normalization [0, 1]')
    age_mm = (age - age.min()) / (age.max() - age.min())
    sal_mm = (salary - salary.min()) / (salary.max() - salary.min())
    ax.scatter(age_mm, sal_mm, alpha=0.5, color=COLORS['green'], s=30)
    ax.set_xlabel('Age (normalized)'); ax.set_ylabel('Salary (normalized)')
    ax.text(0.05, 0.95, "x' = (x - min) / (max - min)", transform=ax.transAxes, fontsize=9, va='top',
            bbox=dict(boxstyle='round', facecolor=COLORS['light_green']))

    ax = axes[2]; ax.set_title('Z-Score Standardization')
    age_z = (age - age.mean()) / age.std()
    sal_z = (salary - salary.mean()) / salary.std()
    ax.scatter(age_z, sal_z, alpha=0.5, color=COLORS['purple'], s=30)
    ax.set_xlabel('Age (z-score)'); ax.set_ylabel('Salary (z-score)')
    ax.text(0.05, 0.95, "z = (x - μ) / σ", transform=ax.transAxes, fontsize=9, va='top',
            bbox=dict(boxstyle='round', facecolor=COLORS['light_purple']))
    fig.tight_layout()
    save(fig, 'ch04-normalization.png')

def ch04_outlier_iqr():
    fig, ax = plt.subplots(figsize=(8, 4))
    ax.set_title('Outlier Detection with IQR Method')
    np.random.seed(42)
    data = np.concatenate([np.random.normal(50, 10, 95), [5, 95, 100, 110, 120]])
    bp = ax.boxplot(data, vert=False, widths=0.6, patch_artist=True,
                     boxprops=dict(facecolor=COLORS['light_blue'], edgecolor=COLORS['blue']),
                     medianprops=dict(color=COLORS['red'], lw=2),
                     flierprops=dict(marker='o', markerfacecolor=COLORS['red'], ms=8))
    Q1, Q3 = np.percentile(data, [25, 75]); IQR = Q3 - Q1
    lower, upper = Q1 - 1.5*IQR, Q3 + 1.5*IQR
    ax.axvline(Q1, color=COLORS['blue'], ls='--', lw=1, alpha=0.7)
    ax.axvline(Q3, color=COLORS['blue'], ls='--', lw=1, alpha=0.7)
    ax.axvline(lower, color=COLORS['red'], ls=':', lw=2)
    ax.axvline(upper, color=COLORS['red'], ls=':', lw=2)
    ax.text(Q1, 1.3, f'Q1={Q1:.0f}', ha='center', fontsize=9, color=COLORS['blue'])
    ax.text(Q3, 1.3, f'Q3={Q3:.0f}', ha='center', fontsize=9, color=COLORS['blue'])
    ax.text(lower, 0.6, f'Lower={lower:.0f}', ha='center', fontsize=9, color=COLORS['red'])
    ax.text(upper, 0.6, f'Upper={upper:.0f}', ha='center', fontsize=9, color=COLORS['red'])
    ax.annotate(f'IQR = Q3 - Q1 = {IQR:.0f}', xy=((Q1+Q3)/2, 1), fontsize=10, ha='center',
                bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.set_xlabel('Value')
    save(fig, 'ch04-outlier-iqr.png')


# ===================================================================
# CHAPTER 05 — Feature Engineering
# ===================================================================

def ch05_pca_projection():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    np.random.seed(42)
    # Correlated 2D data
    cov = [[2, 1.5], [1.5, 1.5]]
    data = np.random.multivariate_normal([3, 3], cov, 80)

    ax = axes[0]; ax.set_title('Original 2D Data with Principal Components')
    ax.scatter(data[:, 0], data[:, 1], alpha=0.4, color=COLORS['blue'], s=30)
    mean = data.mean(axis=0)
    cov_mat = np.cov(data.T)
    eigenvalues, eigenvectors = np.linalg.eig(cov_mat)
    idx = np.argsort(eigenvalues)[::-1]
    eigenvalues, eigenvectors = eigenvalues[idx], eigenvectors[:, idx]
    for i, (ev, col, label) in enumerate(zip(eigenvalues, [COLORS['red'], COLORS['green']], ['PC1 (most variance)', 'PC2'])):
        vec = eigenvectors[:, i] * np.sqrt(ev) * 2
        ax.annotate('', xy=mean + vec, xytext=mean, arrowprops=dict(arrowstyle='->', color=col, lw=3))
        ax.text(mean[0] + vec[0] + 0.2, mean[1] + vec[1] + 0.2, label, color=col, fontweight='bold', fontsize=9)
    ax.set_xlabel('Feature 1'); ax.set_ylabel('Feature 2'); ax.set_aspect('equal')

    ax = axes[1]; ax.set_title('Projected onto PC1 (2D → 1D)')
    pc1 = eigenvectors[:, 0]
    projected = (data - mean) @ pc1
    ax.scatter(projected, np.zeros_like(projected), alpha=0.4, color=COLORS['red'], s=30)
    ax.axhline(0, color='black', lw=0.5)
    ax.set_xlabel('PC1 value'); ax.set_yticks([])
    variance_retained = eigenvalues[0] / eigenvalues.sum() * 100
    ax.text(0.5, 0.9, f'Variance retained: {variance_retained:.0f}%\n(Lost only {100-variance_retained:.0f}%)',
            transform=ax.transAxes, ha='center', fontsize=10,
            bbox=dict(boxstyle='round', facecolor=COLORS['light_red']))
    fig.tight_layout()
    save(fig, 'ch05-pca-projection.png')

def ch05_cyclical_encoding():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    hours = np.arange(0, 24)
    sin_h = np.sin(2 * np.pi * hours / 24)
    cos_h = np.cos(2 * np.pi * hours / 24)

    ax = axes[0]; ax.set_title('Problem: Hour 23 and Hour 0 Are Far Apart')
    ax.bar(hours, hours, color=[COLORS['red'] if h in [0, 23] else COLORS['blue'] for h in hours], alpha=0.6)
    ax.annotate('Distance = 23!', xy=(11.5, 22), fontsize=12, ha='center', color=COLORS['red'], fontweight='bold',
                bbox=dict(boxstyle='round', facecolor=COLORS['light_red']))
    ax.set_xlabel('Hour'); ax.set_ylabel('Raw value')

    ax = axes[1]; ax.set_title('Solution: Map to Unit Circle')
    theta = 2 * np.pi * hours / 24
    ax.plot(np.cos(theta), np.sin(theta), '--', color=COLORS['gray'], alpha=0.3)
    scatter = ax.scatter(cos_h, sin_h, c=hours, cmap='twilight', s=80, zorder=5)
    for h in [0, 6, 12, 18, 23]:
        ax.annotate(f'{h}h', (cos_h[h], sin_h[h]), textcoords='offset points', xytext=(10, 5), fontsize=9, fontweight='bold')
    ax.annotate('Now close!', xy=((cos_h[0]+cos_h[23])/2, (sin_h[0]+sin_h[23])/2+0.15),
                fontsize=10, ha='center', color=COLORS['green'], fontweight='bold')
    ax.set_xlabel('cos(2πh/24)'); ax.set_ylabel('sin(2πh/24)'); ax.set_aspect('equal')
    plt.colorbar(scatter, ax=ax, label='Hour')
    fig.tight_layout()
    save(fig, 'ch05-cyclical-encoding.png')


# ===================================================================
# CHAPTER 06 — Regression
# ===================================================================

def ch06_linear_regression():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    np.random.seed(42)
    x = np.linspace(0, 10, 30)
    y = 2.5 * x + 5 + np.random.normal(0, 3, 30)

    ax = axes[0]; ax.set_title('Linear Regression — Minimizing Residuals')
    ax.scatter(x, y, color=COLORS['blue'], s=40, alpha=0.7)
    m, b = np.polyfit(x, y, 1)
    y_pred = m * x + b
    ax.plot(x, y_pred, color=COLORS['red'], lw=2.5, label=f'ŷ = {m:.1f}x + {b:.1f}')
    for xi, yi, ypi in zip(x[::3], y[::3], y_pred[::3]):
        ax.plot([xi, xi], [yi, ypi], '--', color=COLORS['gray'], lw=1)
    ax.set_xlabel('x'); ax.set_ylabel('y')
    ax.text(0.05, 0.95, 'Minimize: L = (1/n) Σ(yᵢ - ŷᵢ)²\n\nClosed form:\nθ* = (XᵀX)⁻¹ Xᵀy',
            transform=ax.transAxes, fontsize=10, va='top', bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.legend(fontsize=10)

    # Decision boundary
    ax = axes[1]; ax.set_title('Logistic Regression — Decision Boundary')
    np.random.seed(42)
    from sklearn.datasets import make_classification
    X, y_cls = make_classification(n_samples=100, n_features=2, n_redundant=0, n_informative=2,
                                    n_clusters_per_class=1, random_state=42)
    ax.scatter(X[y_cls==0, 0], X[y_cls==0, 1], color=COLORS['blue'], s=30, alpha=0.6, label='Class 0')
    ax.scatter(X[y_cls==1, 0], X[y_cls==1, 1], color=COLORS['red'], s=30, alpha=0.6, label='Class 1')
    from sklearn.linear_model import LogisticRegression
    clf = LogisticRegression().fit(X, y_cls)
    xlim = ax.get_xlim(); ylim = ax.get_ylim()
    xx, yy = np.meshgrid(np.linspace(xlim[0], xlim[1], 100), np.linspace(ylim[0], ylim[1], 100))
    Z = clf.predict_proba(np.c_[xx.ravel(), yy.ravel()])[:, 1].reshape(xx.shape)
    ax.contour(xx, yy, Z, levels=[0.5], colors=[COLORS['green']], linewidths=2.5)
    ax.contourf(xx, yy, Z, levels=[0, 0.5, 1], colors=[COLORS['light_blue'], COLORS['light_red']], alpha=0.2)
    ax.set_xlabel('Feature 1'); ax.set_ylabel('Feature 2')
    ax.text(0.05, 0.95, 'σ(z) = 1 / (1 + e⁻ᶻ)\nz = wᵀx + b\nBoundary: z = 0',
            transform=ax.transAxes, fontsize=9, va='top', bbox=dict(boxstyle='round', facecolor=COLORS['light_green']))
    ax.legend(fontsize=9)
    fig.tight_layout()
    save(fig, 'ch06-regression.png')

def ch06_regularization():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    # L1 vs L2 constraint regions
    ax = axes[0]; ax.set_title('L1 (Lasso) vs L2 (Ridge) Constraint Regions')
    theta = np.linspace(0, 2*np.pi, 100)
    # L2 circle
    ax.plot(np.cos(theta), np.sin(theta), color=COLORS['blue'], lw=2.5, label='L2 (Ridge): ||w||² ≤ t')
    ax.fill(np.cos(theta), np.sin(theta), alpha=0.1, color=COLORS['blue'])
    # L1 diamond
    l1_x = [1, 0, -1, 0, 1]; l1_y = [0, 1, 0, -1, 0]
    ax.plot(l1_x, l1_y, color=COLORS['red'], lw=2.5, label='L1 (Lasso): ||w||₁ ≤ t')
    ax.fill(l1_x, l1_y, alpha=0.1, color=COLORS['red'])
    # Loss contours
    for r in [0.5, 1.0, 1.5, 2.0]:
        ellipse_x = 1.5 + r * 0.8 * np.cos(theta)
        ellipse_y = 1.0 + r * 0.6 * np.sin(theta)
        ax.plot(ellipse_x, ellipse_y, '--', color=COLORS['gray'], lw=0.8, alpha=0.5)
    ax.plot(1.5, 1.0, '+', color=COLORS['gray'], ms=12, mew=2)
    ax.text(1.6, 1.1, 'Unconstrained\noptimum', fontsize=8, color=COLORS['gray'])
    ax.plot(0, 1, 'o', color=COLORS['red'], ms=10, zorder=5)
    ax.text(0.15, 1.1, 'L1 hits\ncorner\n(sparse!)', fontsize=8, color=COLORS['red'])
    ax.set_xlabel('w₁'); ax.set_ylabel('w₂')
    ax.set_xlim(-2, 3); ax.set_ylim(-2, 3); ax.set_aspect('equal')
    ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
    ax.legend(fontsize=9, loc='lower right')

    # Coefficient paths
    ax = axes[1]; ax.set_title('Regularization Shrinks Coefficients')
    alphas = np.logspace(-2, 2, 50)
    np.random.seed(42)
    coefs = []
    for a in alphas:
        c = np.array([3, -2, 1.5, -0.5, 0.1]) / (1 + a)
        coefs.append(c)
    coefs = np.array(coefs)
    for i, col in enumerate([COLORS['blue'], COLORS['red'], COLORS['green'], COLORS['orange'], COLORS['purple']]):
        ax.plot(alphas, coefs[:, i], color=col, lw=2, label=f'w{i+1}')
    ax.set_xscale('log')
    ax.set_xlabel('Regularization strength α'); ax.set_ylabel('Coefficient value')
    ax.axhline(0, color='black', lw=0.5)
    ax.text(0.5, 0.95, 'L = Loss(y, ŷ) + α·Penalty(w)\n\nHigher α → smaller weights\n→ simpler model',
            transform=ax.transAxes, ha='center', va='top', fontsize=9,
            bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.legend(fontsize=8, ncol=2)
    fig.tight_layout()
    save(fig, 'ch06-regularization.png')


# ===================================================================
# CHAPTER 07 — Trees & Ensembles
# ===================================================================

def ch07_tree_splits():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    np.random.seed(42)
    n = 80
    X = np.random.randn(n, 2) * 2
    y = ((X[:, 0] > 0) & (X[:, 1] > -0.5)).astype(int) | ((X[:, 0] < -1) & (X[:, 1] < 0.5)).astype(int)

    ax = axes[0]; ax.set_title('Decision Tree Splits the Feature Space')
    ax.scatter(X[y==0, 0], X[y==0, 1], color=COLORS['blue'], s=30, alpha=0.6, label='Class 0')
    ax.scatter(X[y==1, 0], X[y==1, 1], color=COLORS['red'], s=30, alpha=0.6, label='Class 1')
    ax.axvline(0, color=COLORS['green'], lw=2.5, ls='--', label='Split 1: x₁ = 0')
    ax.axhline(-0.5, color=COLORS['orange'], lw=2.5, ls=':', xmin=0.5, label='Split 2: x₂ = -0.5')
    ax.set_xlabel('Feature 1'); ax.set_ylabel('Feature 2'); ax.legend(fontsize=8)

    ax = axes[1]; ax.set_title('Gini Impurity vs Entropy')
    p = np.linspace(0.001, 0.999, 100)
    gini = 2 * p * (1 - p)
    entropy = -(p * np.log2(p) + (1-p) * np.log2(1-p))
    ax.plot(p, gini, color=COLORS['blue'], lw=2.5, label='Gini = 2p(1-p)')
    ax.plot(p, entropy, color=COLORS['red'], lw=2.5, label='Entropy = -Σpₖlog₂pₖ')
    ax.axvline(0.5, color=COLORS['gray'], ls='--', lw=1)
    ax.set_xlabel('P(class 1)'); ax.set_ylabel('Impurity')
    ax.text(0.5, 0.05, 'Max impurity\n(worst split)', ha='center', fontsize=9, color=COLORS['gray'],
            transform=ax.transAxes)
    ax.legend(fontsize=10)
    fig.tight_layout()
    save(fig, 'ch07-tree-splits.png')

def ch07_ensemble():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4))
    np.random.seed(42)
    x = np.linspace(0, 10, 100)
    y_true = np.sin(x)
    for i in range(3):
        ax = axes[i]
        noise = np.random.normal(0, 0.3, 100)
        y_pred = y_true + noise + np.random.normal(0, 0.1) * x
        if i < 2:
            ax.set_title(f'Tree {i+1} (noisy)')
            ax.plot(x, y_pred, color=COLORS['blue'], lw=1.5, alpha=0.7)
        else:
            ax.set_title('Random Forest (averaged)')
            preds = [y_true + np.random.normal(0, 0.3, 100) + np.random.normal(0, 0.1)*x for _ in range(10)]
            avg = np.mean(preds, axis=0)
            for p in preds:
                ax.plot(x, p, color=COLORS['gray'], lw=0.5, alpha=0.3)
            ax.plot(x, avg, color=COLORS['green'], lw=2.5, label='Average of 10 trees')
            ax.legend(fontsize=9)
        ax.plot(x, y_true, '--', color=COLORS['red'], lw=1.5, label='Truth' if i == 2 else '_')
        ax.set_xlabel('x'); ax.set_ylabel('prediction')
        if i == 2: ax.legend(fontsize=9)
    fig.suptitle('Ensembles Reduce Variance: Var(avg) = σ²/n', fontsize=13, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch07-ensemble.png')


# ===================================================================
# CHAPTER 08 — Gradient Boosting
# ===================================================================

def ch08_boosting_iterations():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4))
    np.random.seed(42)
    x = np.linspace(0, 10, 80)
    y_true = np.sin(x) * 2 + x * 0.3
    y_data = y_true + np.random.normal(0, 0.5, 80)

    pred = np.zeros_like(x)
    for i, ax in enumerate(axes):
        residual = y_data - pred
        # Fit simple model to residual
        coeffs = np.polyfit(x, residual, 3)
        h = np.polyval(coeffs, x)
        pred = pred + 0.5 * h  # learning rate 0.5
        ax.set_title(f'Iteration {i+1}')
        ax.scatter(x, y_data, color=COLORS['gray'], s=10, alpha=0.3, label='Data')
        ax.plot(x, pred, color=COLORS['blue'], lw=2, label='Current model')
        ax.plot(x, y_true, '--', color=COLORS['red'], lw=1.5, label='True function')
        if i < 2:
            ax.scatter(x[::5], residual[::5], color=COLORS['orange'], s=25, alpha=0.6, marker='^', label='Residuals')
        ax.set_xlabel('x'); ax.legend(fontsize=7, loc='upper left')
    fig.suptitle('Gradient Boosting: Each Tree Fits the Residuals of Previous Trees',
                 fontsize=13, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch08-boosting-iterations.png')


# ===================================================================
# CHAPTER 09 — Clustering
# ===================================================================

def ch09_kmeans():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.5))
    np.random.seed(42)
    from sklearn.datasets import make_blobs
    X, y = make_blobs(n_samples=150, centers=3, cluster_std=1.0, random_state=42)
    colors = [COLORS['blue'], COLORS['red'], COLORS['green']]

    # Initial random centroids
    centroids = X[np.random.choice(len(X), 3, replace=False)]
    for step, ax in enumerate(axes):
        # Assign
        dists = np.array([np.linalg.norm(X - c, axis=1) for c in centroids])
        labels = dists.argmin(axis=0)
        if step > 0:
            # Update centroids
            for k in range(3):
                if np.sum(labels == k) > 0:
                    centroids[k] = X[labels == k].mean(axis=0)
            dists = np.array([np.linalg.norm(X - c, axis=1) for c in centroids])
            labels = dists.argmin(axis=0)
        for k in range(3):
            mask = labels == k
            ax.scatter(X[mask, 0], X[mask, 1], color=colors[k], s=20, alpha=0.5)
        for k in range(3):
            ax.plot(centroids[k, 0], centroids[k, 1], '*', color=colors[k], ms=15, markeredgecolor='black', markeredgewidth=1)
        ax.set_title(f'Step {step+1}: {"Assign" if step == 0 else "Update → Assign"}')
        ax.set_xlabel('Feature 1'); ax.set_ylabel('Feature 2')
        if step == 0:
            centroids_new = np.array([X[labels == k].mean(axis=0) for k in range(3)])
            centroids = centroids_new
    fig.suptitle('K-Means: Minimize Σ ||xᵢ - μc(i)||²', fontsize=13, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch09-kmeans.png')

def ch09_dbscan():
    fig, ax = plt.subplots(figsize=(7, 6))
    ax.set_title('DBSCAN: Core, Border, and Noise Points')
    np.random.seed(42)
    # Dense cluster
    c1 = np.random.normal([2, 2], 0.5, (30, 2))
    c2 = np.random.normal([6, 6], 0.5, (25, 2))
    noise = np.random.uniform(0, 9, (8, 2))
    X = np.vstack([c1, c2, noise])
    from sklearn.cluster import DBSCAN
    db = DBSCAN(eps=1.0, min_samples=4).fit(X)
    labels = db.labels_
    core_mask = np.zeros(len(X), dtype=bool)
    core_mask[db.core_sample_indices_] = True

    # Noise
    noise_mask = labels == -1
    ax.scatter(X[noise_mask, 0], X[noise_mask, 1], color=COLORS['gray'], s=50, marker='x', lw=2, label='Noise', zorder=5)
    # Core
    ax.scatter(X[core_mask, 0], X[core_mask, 1], color=COLORS['blue'], s=60, alpha=0.7, label='Core points', zorder=5)
    # Border
    border_mask = ~core_mask & ~noise_mask
    ax.scatter(X[border_mask, 0], X[border_mask, 1], color=COLORS['orange'], s=60, alpha=0.7, label='Border points', zorder=5)
    # Draw epsilon circles around a few core points
    for idx in db.core_sample_indices_[::5]:
        circle = plt.Circle(X[idx], 1.0, fill=False, color=COLORS['blue'], lw=0.8, ls='--', alpha=0.3)
        ax.add_patch(circle)
    ax.text(0.02, 0.98, 'ε = 1.0, min_samples = 4\n\nCore: ≥ min_samples within ε\nBorder: within ε of core\nNoise: neither',
            transform=ax.transAxes, fontsize=9, va='top', bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.legend(fontsize=9); ax.set_xlabel('Feature 1'); ax.set_ylabel('Feature 2')
    save(fig, 'ch09-dbscan.png')

def ch09_elbow():
    fig, ax = plt.subplots(figsize=(7, 5))
    ax.set_title('Elbow Method — Choosing K')
    np.random.seed(42)
    from sklearn.datasets import make_blobs
    from sklearn.cluster import KMeans
    X, _ = make_blobs(n_samples=200, centers=4, random_state=42)
    inertias = []
    K_range = range(1, 10)
    for k in K_range:
        km = KMeans(n_clusters=k, random_state=42, n_init=10).fit(X)
        inertias.append(km.inertia_)
    ax.plot(list(K_range), inertias, 'o-', color=COLORS['blue'], lw=2.5, ms=8)
    ax.axvline(4, color=COLORS['red'], ls='--', lw=2, label='Elbow at K=4')
    ax.annotate('Elbow', xy=(4, inertias[3]), xytext=(6, inertias[1]),
                arrowprops=dict(arrowstyle='->', color=COLORS['red'], lw=2),
                fontsize=12, color=COLORS['red'], fontweight='bold')
    ax.set_xlabel('Number of clusters (K)'); ax.set_ylabel('Inertia (Σ||xᵢ - μ||²)')
    ax.legend(fontsize=10)
    save(fig, 'ch09-elbow.png')


# ===================================================================
# CHAPTER 10 — Neural Networks
# ===================================================================

def ch10_activation_functions():
    fig, axes = plt.subplots(2, 2, figsize=(10, 8))
    x = np.linspace(-5, 5, 200)
    funcs = [
        ('ReLU: max(0, x)', lambda x: np.maximum(0, x), COLORS['blue'],
         'Gradient: 0 if x<0, 1 if x>0'),
        ('Sigmoid: 1/(1+e⁻ˣ)', lambda x: 1/(1+np.exp(-x)), COLORS['red'],
         'Output in (0,1) — for probabilities'),
        ('Tanh: (eˣ-e⁻ˣ)/(eˣ+e⁻ˣ)', lambda x: np.tanh(x), COLORS['green'],
         'Output in (-1,1) — zero-centered'),
        ('Leaky ReLU: max(0.1x, x)', lambda x: np.where(x > 0, x, 0.1*x), COLORS['purple'],
         'Fixes "dying ReLU" problem'),
    ]
    for ax, (name, f, col, note) in zip(axes.flat, funcs):
        ax.plot(x, f(x), color=col, lw=2.5)
        ax.set_title(name, fontsize=11)
        ax.axhline(0, color='black', lw=0.5); ax.axvline(0, color='black', lw=0.5)
        ax.text(0.05, 0.95, note, transform=ax.transAxes, fontsize=9, va='top',
                bbox=dict(boxstyle='round', facecolor='white', alpha=0.8))
    fig.suptitle('Activation Functions — Adding Non-Linearity', fontsize=14, fontweight='bold')
    fig.tight_layout()
    save(fig, 'ch10-activation-functions.png')

def ch10_decision_boundary():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.5))
    np.random.seed(42)
    from sklearn.datasets import make_moons
    X, y = make_moons(n_samples=200, noise=0.2, random_state=42)

    from sklearn.neural_network import MLPClassifier
    configs = [
        ('1 hidden layer (2 neurons)', (2,)),
        ('1 hidden layer (10 neurons)', (10,)),
        ('2 hidden layers (10, 10)', (10, 10)),
    ]
    for ax, (title, hidden) in zip(axes, configs):
        clf = MLPClassifier(hidden_layer_sizes=hidden, max_iter=1000, random_state=42).fit(X, y)
        xx, yy = np.meshgrid(np.linspace(-2, 3, 200), np.linspace(-1.5, 2, 200))
        Z = clf.predict(np.c_[xx.ravel(), yy.ravel()]).reshape(xx.shape)
        ax.contourf(xx, yy, Z, alpha=0.2, colors=[COLORS['light_blue'], COLORS['light_red']])
        ax.contour(xx, yy, Z, colors=[COLORS['green']], linewidths=2)
        ax.scatter(X[y==0, 0], X[y==0, 1], color=COLORS['blue'], s=15, alpha=0.6)
        ax.scatter(X[y==1, 0], X[y==1, 1], color=COLORS['red'], s=15, alpha=0.6)
        ax.set_title(title, fontsize=10)
    fig.suptitle('More Neurons/Layers → More Complex Decision Boundaries', fontsize=13, fontweight='bold', y=1.02)
    fig.tight_layout()
    save(fig, 'ch10-decision-boundary.png')


# ===================================================================
# CHAPTER 11 — Embeddings
# ===================================================================

def ch11_embedding_space():
    fig, ax = plt.subplots(figsize=(8, 7))
    ax.set_title('Word Embeddings in 2D Space (t-SNE projection)')
    words = {
        'Animals': {'cat': (1, 3), 'dog': (1.5, 2.5), 'fish': (0.5, 3.5), 'bird': (2, 3.2)},
        'Food': {'pizza': (5, 1), 'burger': (5.5, 0.5), 'pasta': (4.5, 1.5), 'sushi': (6, 1.2)},
        'Tech': {'python': (3, 6), 'java': (3.5, 5.5), 'server': (2.5, 6.5), 'code': (4, 6)},
    }
    colors_map = {'Animals': COLORS['blue'], 'Food': COLORS['red'], 'Tech': COLORS['green']}
    for category, word_dict in words.items():
        xs, ys = zip(*word_dict.values())
        ax.scatter(xs, ys, color=colors_map[category], s=80, alpha=0.7, label=category)
        for word, (x, y) in word_dict.items():
            ax.annotate(word, (x, y), textcoords='offset points', xytext=(5, 5), fontsize=10, fontweight='bold')
    ax.text(0.02, 0.02, 'Similar words cluster together\nRelated concepts form neighborhoods',
            transform=ax.transAxes, fontsize=10, va='bottom', bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.legend(fontsize=10, loc='upper right')
    ax.set_xlabel('Embedding dimension 1'); ax.set_ylabel('Embedding dimension 2')
    save(fig, 'ch11-embedding-space.png')

def ch11_word_analogy():
    fig, ax = plt.subplots(figsize=(8, 6))
    ax.set_title('Word Analogy: king - man + woman ≈ queen')
    points = {'king': (4, 4), 'queen': (6, 4), 'man': (4, 1.5), 'woman': (6, 1.5)}
    for word, (x, y) in points.items():
        ax.plot(x, y, 'o', color=COLORS['blue'] if 'man' in word or word == 'king' else COLORS['red'], ms=12)
        ax.annotate(word, (x, y), textcoords='offset points', xytext=(10, 5), fontsize=13, fontweight='bold')
    # Arrows showing relationships
    ax.annotate('', xy=(6, 4), xytext=(4, 4), arrowprops=dict(arrowstyle='->', color=COLORS['green'], lw=2.5))
    ax.annotate('', xy=(6, 1.5), xytext=(4, 1.5), arrowprops=dict(arrowstyle='->', color=COLORS['green'], lw=2.5))
    ax.text(5, 4.3, 'gender vector', fontsize=10, ha='center', color=COLORS['green'])
    ax.text(5, 1.2, 'gender vector', fontsize=10, ha='center', color=COLORS['green'])
    ax.annotate('', xy=(4, 4), xytext=(4, 1.5), arrowprops=dict(arrowstyle='->', color=COLORS['purple'], lw=2, ls='--'))
    ax.annotate('', xy=(6, 4), xytext=(6, 1.5), arrowprops=dict(arrowstyle='->', color=COLORS['purple'], lw=2, ls='--'))
    ax.text(3.5, 2.75, 'royalty', fontsize=10, ha='center', color=COLORS['purple'], rotation=90)
    ax.set_xlim(2.5, 7.5); ax.set_ylim(0.5, 5.5)
    ax.text(0.5, 0.02, 'E[king] - E[man] + E[woman] ≈ E[queen]\nVector arithmetic captures semantic relationships!',
            transform=ax.transAxes, fontsize=10, ha='center', bbox=dict(boxstyle='round', facecolor=COLORS['light_purple']))
    ax.set_xlabel('Dimension 1'); ax.set_ylabel('Dimension 2')
    save(fig, 'ch11-word-analogy.png')


# ===================================================================
# CHAPTER 12 — Transformers
# ===================================================================

def ch12_attention_heatmap():
    fig, ax = plt.subplots(figsize=(7, 6))
    ax.set_title('Self-Attention Weights (softmax(QKᵀ/√dₖ))')
    tokens = ['The', 'cat', 'sat', 'on', 'the', 'mat']
    n = len(tokens)
    np.random.seed(42)
    # Simulate meaningful attention
    attn = np.random.dirichlet(np.ones(n) * 0.5, n)
    # Make "cat" attend to "sat" and "mat"
    attn[1] = [0.05, 0.1, 0.4, 0.05, 0.05, 0.35]
    attn[2] = [0.1, 0.45, 0.15, 0.1, 0.1, 0.1]
    im = ax.imshow(attn, cmap='Blues', vmin=0, vmax=0.5)
    ax.set_xticks(range(n)); ax.set_xticklabels(tokens, fontsize=11)
    ax.set_yticks(range(n)); ax.set_yticklabels(tokens, fontsize=11)
    ax.set_xlabel('Key (attending to)'); ax.set_ylabel('Query (from)')
    for i in range(n):
        for j in range(n):
            ax.text(j, i, f'{attn[i,j]:.2f}', ha='center', va='center', fontsize=9,
                    color='white' if attn[i,j] > 0.3 else 'black')
    plt.colorbar(im, ax=ax, label='Attention weight')
    save(fig, 'ch12-attention-heatmap.png')

def ch12_positional_encoding():
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.set_title('Positional Encoding — Sinusoidal Waves')
    d_model = 64; max_len = 50
    pe = np.zeros((max_len, d_model))
    position = np.arange(max_len)[:, np.newaxis]
    div_term = np.exp(np.arange(0, d_model, 2) * -(np.log(10000.0) / d_model))
    pe[:, 0::2] = np.sin(position * div_term)
    pe[:, 1::2] = np.cos(position * div_term)
    im = ax.imshow(pe.T[:32, :], aspect='auto', cmap='RdBu', vmin=-1, vmax=1)
    ax.set_xlabel('Position in sequence'); ax.set_ylabel('Encoding dimension')
    ax.text(0.02, 0.98, 'PE(pos,2i) = sin(pos / 10000^(2i/d))\nPE(pos,2i+1) = cos(pos / 10000^(2i/d))',
            transform=ax.transAxes, fontsize=9, va='top', color='white', fontweight='bold',
            bbox=dict(boxstyle='round', facecolor='black', alpha=0.7))
    plt.colorbar(im, ax=ax, label='Value')
    save(fig, 'ch12-positional-encoding.png')


# ===================================================================
# CHAPTER 13 — Evaluation Metrics
# ===================================================================

def ch13_roc_curve():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    np.random.seed(42)
    from sklearn.datasets import make_classification
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import roc_curve, auc, precision_recall_curve, average_precision_score
    X, y = make_classification(n_samples=500, random_state=42)
    clf = LogisticRegression().fit(X, y)
    y_score = clf.predict_proba(X)[:, 1]

    # ROC
    ax = axes[0]; ax.set_title('ROC Curve')
    fpr, tpr, _ = roc_curve(y, y_score)
    roc_auc = auc(fpr, tpr)
    ax.fill_between(fpr, tpr, alpha=0.2, color=COLORS['blue'])
    ax.plot(fpr, tpr, color=COLORS['blue'], lw=2.5, label=f'AUC = {roc_auc:.3f}')
    ax.plot([0, 1], [0, 1], '--', color=COLORS['gray'], lw=1.5, label='Random (AUC=0.5)')
    ax.set_xlabel('FPR = FP/(FP+TN)'); ax.set_ylabel('TPR = TP/(TP+FN)')
    ax.text(0.5, 0.3, 'AUC = P(score_pos > score_neg)', fontsize=9, ha='center',
            bbox=dict(boxstyle='round', facecolor=COLORS['light_blue']))
    ax.legend(fontsize=10)

    # PR
    ax = axes[1]; ax.set_title('Precision-Recall Curve')
    prec, rec, _ = precision_recall_curve(y, y_score)
    ap = average_precision_score(y, y_score)
    ax.fill_between(rec, prec, alpha=0.2, color=COLORS['red'])
    ax.plot(rec, prec, color=COLORS['red'], lw=2.5, label=f'AP = {ap:.3f}')
    ax.set_xlabel('Recall = TP/(TP+FN)'); ax.set_ylabel('Precision = TP/(TP+FP)')
    ax.text(0.3, 0.3, 'AP = Σ(Rₙ - Rₙ₋₁) · Pₙ', fontsize=9, ha='center',
            bbox=dict(boxstyle='round', facecolor=COLORS['light_red']))
    ax.legend(fontsize=10)
    fig.tight_layout()
    save(fig, 'ch13-roc-pr-curves.png')

def ch13_bias_variance():
    fig, axes = plt.subplots(2, 2, figsize=(8, 8))
    titles = [('Low Bias\nLow Variance', 0.3, 0.3),
              ('Low Bias\nHigh Variance', 0.3, 1.5),
              ('High Bias\nLow Variance', 1.5, 0.3),
              ('High Bias\nHigh Variance', 1.5, 1.5)]
    for ax, (title, bias, var) in zip(axes.flat, titles):
        ax.set_title(title, fontsize=11)
        theta = np.linspace(0, 2*np.pi, 50)
        for r in [1, 2, 3]:
            ax.plot(r*np.cos(theta), r*np.sin(theta), color=COLORS['gray'], lw=0.5, alpha=0.3)
        ax.plot(0, 0, '+', color=COLORS['red'], ms=15, mew=2)
        np.random.seed(42)
        pts = np.random.normal([bias, bias], var, (15, 2))
        ax.scatter(pts[:, 0], pts[:, 1], color=COLORS['blue'], s=40, alpha=0.7)
        ax.set_xlim(-4, 4); ax.set_ylim(-4, 4); ax.set_aspect('equal')
        ax.set_xticks([]); ax.set_yticks([])
    fig.suptitle('Bias-Variance Tradeoff\nE[(ŷ-y)²] = Bias² + Variance + Noise', fontsize=14, fontweight='bold')
    fig.tight_layout()
    save(fig, 'ch13-bias-variance.png')


# ===================================================================
# RUN ALL
# ===================================================================

if __name__ == '__main__':
    setup_style()
    print('Generating diagrams for ML Engineering Guide...\n')

    print('Chapter 01 — Vectors & Linear Algebra:')
    ch01_vectors_2d()
    ch01_dot_product_projection()
    ch01_cosine_similarity()
    ch01_matrix_transform()
    ch01_eigenvectors()

    print('\nChapter 02 — Calculus & Optimization:')
    ch02_gradient_descent_2d()
    ch02_loss_surface_3d()
    ch02_saddle_point()

    print('\nChapter 03 — Probability & Statistics:')
    ch03_normal_distribution()
    ch03_distributions()
    ch03_bayes()

    print('\nChapter 04 — Data Preprocessing:')
    ch04_normalization()
    ch04_outlier_iqr()

    print('\nChapter 05 — Feature Engineering:')
    ch05_pca_projection()
    ch05_cyclical_encoding()

    print('\nChapter 06 — Regression:')
    ch06_linear_regression()
    ch06_regularization()

    print('\nChapter 07 — Trees & Ensembles:')
    ch07_tree_splits()
    ch07_ensemble()

    print('\nChapter 08 — Gradient Boosting:')
    ch08_boosting_iterations()

    print('\nChapter 09 — Clustering:')
    ch09_kmeans()
    ch09_dbscan()
    ch09_elbow()

    print('\nChapter 10 — Neural Networks:')
    ch10_activation_functions()
    ch10_decision_boundary()

    print('\nChapter 11 — Embeddings:')
    ch11_embedding_space()
    ch11_word_analogy()

    print('\nChapter 12 — Transformers:')
    ch12_attention_heatmap()
    ch12_positional_encoding()

    print('\nChapter 13 — Evaluation Metrics:')
    ch13_roc_curve()
    ch13_bias_variance()

    print(f'\nDone! Generated {len(os.listdir(OUT_DIR))} images in {OUT_DIR}')
