// Documentation site JavaScript
(function() {
    'use strict';

    // Configure marked.js to generate heading IDs
    marked.use({
        renderer: {
            heading(token) {
                const text = token.text || token;
                const level = token.depth || 1;
                // Generate ID from heading text (same way GitHub does it)
                const id = String(text).toLowerCase()
                    .replace(/<[^>]*>/g, '')  // Remove HTML tags
                    .replace(/[^\w\s-]/g, '') // Remove special chars except spaces and hyphens
                    .replace(/\s+/g, '-')     // Replace spaces with hyphens
                    .replace(/-+/g, '-')      // Collapse multiple hyphens
                    .trim();
                return `<h${level} id="${id}">${text}</h${level}>\n`;
            }
        }
    });

    // DOM Elements
    const sidebar = document.getElementById('sidebar');
    const sidebarNav = document.getElementById('sidebarNav');
    const content = document.getElementById('markdownContent');
    const searchInput = document.getElementById('searchInput');
    const menuToggle = document.getElementById('menuToggle');
    const overlay = document.getElementById('overlay');
    const themeToggle = document.getElementById('themeToggle');

    // State
    let navigation = null;
    let allNavItems = [];

    // Initialize
    async function init() {
        await loadNavigation();
        loadTheme();
        makeSidebarTitleClickable();
        handleHashChange();
        setupEventListeners();
    }

    // Load navigation.json
    async function loadNavigation() {
        try {
            const response = await fetch('navigation.json');
            navigation = await response.json();
            renderNavigation();
        } catch (error) {
            console.error('Failed to load navigation:', error);
            content.innerHTML = '<p>Failed to load navigation. Please ensure navigation.json exists.</p>';
        }
    }

    // Render sidebar navigation
    function renderNavigation() {
        sidebarNav.innerHTML = '';
        allNavItems = [];

        navigation.sections.forEach(section => {
            const sectionEl = document.createElement('div');
            sectionEl.className = 'nav-section';

            const titleEl = document.createElement('div');
            titleEl.className = 'nav-section-title';
            titleEl.textContent = section.title;
            sectionEl.appendChild(titleEl);

            section.items.forEach(item => {
                const itemEl = createNavItem(item);
                sectionEl.appendChild(itemEl);
                allNavItems.push({ element: itemEl, title: item.title, path: item.path });

                // Handle children
                if (item.children && item.children.length > 0) {
                    const childrenContainer = document.createElement('div');
                    childrenContainer.className = 'nav-children';
                    
                    item.children.forEach(child => {
                        const childEl = createNavItem(child);
                        childrenContainer.appendChild(childEl);
                        allNavItems.push({ element: childEl, title: child.title, path: child.path });
                    });

                    sectionEl.appendChild(childrenContainer);
                }
            });

            sidebarNav.appendChild(sectionEl);
        });
    }

    // Create navigation item element
    function createNavItem(item) {
        const el = document.createElement('a');
        el.className = 'nav-item';
        el.textContent = item.title;
        el.href = '#' + item.path;
        el.dataset.path = item.path;

        if (item.children && item.children.length > 0) {
            el.classList.add('has-children');
            el.addEventListener('click', (e) => {
                e.preventDefault();
                el.classList.toggle('expanded');
                const children = el.nextElementSibling;
                if (children && children.classList.contains('nav-children')) {
                    children.classList.toggle('visible');
                }
                // Also navigate to the page
                window.location.hash = item.path;
            });
        }

        return el;
    }

    // Load and render markdown
    async function loadMarkdown(path) {
        content.innerHTML = '<div class="loading">Loading...</div>';

        try {
            const response = await fetch(path);
            if (!response.ok) throw new Error('File not found');
            
            const markdown = await response.text();
            const html = marked.parse(markdown);
            content.innerHTML = html;

            // Apply syntax highlighting
            content.querySelectorAll('pre code').forEach(block => {
                hljs.highlightElement(block);
            });

            // Handle internal links
            content.querySelectorAll('a').forEach(link => {
                const href = link.getAttribute('href');
                if (href && !href.startsWith('http')) {
                    // Handle pure anchor links (same page)
                    if (href.startsWith('#')) {
                        link.addEventListener('click', (e) => {
                            e.preventDefault();
                            const anchor = href.substring(1);
                            const target = document.getElementById(anchor);
                            if (target) {
                                target.scrollIntoView({ behavior: 'smooth' });
                            }
                        });
                        return;
                    }
                    
                    // Convert relative link to hash link
                    const currentDir = path.substring(0, path.lastIndexOf('/') + 1);
                    let newPath = href;
                    let anchor = '';
                    
                    // Separate file path from anchor
                    if (href.includes('#')) {
                        const parts = href.split('#');
                        newPath = parts[0];
                        anchor = parts[1];
                    }
                    
                    if (newPath.startsWith('./')) {
                        newPath = currentDir + newPath.substring(2);
                    } else if (newPath.startsWith('../')) {
                        // Handle parent directory
                        const parts = currentDir.split('/').filter(p => p);
                        parts.pop();
                        newPath = parts.join('/') + '/' + newPath.substring(3);
                    } else if (!newPath.startsWith('/') && newPath) {
                        newPath = currentDir + newPath;
                    }
                    
                    // Store anchor for scrolling after page load
                    if (anchor) {
                        link.addEventListener('click', (e) => {
                            e.preventDefault();
                            window.location.hash = newPath;
                            // Wait for content to load, then scroll to anchor
                            setTimeout(() => {
                                const target = document.getElementById(anchor);
                                if (target) {
                                    target.scrollIntoView({ behavior: 'smooth' });
                                }
                            }, 100);
                        });
                    } else {
                        link.href = '#' + newPath;
                    }
                }
            });

            // Update active state in navigation
            updateActiveNavItem(path);

            // Scroll to top
            window.scrollTo(0, 0);

            // Close mobile menu
            closeMobileMenu();

        } catch (error) {
            console.error('Failed to load markdown:', error);
            content.innerHTML = `
                <h1>Page Not Found</h1>
                <p>Could not load: <code>${path}</code></p>
                <p>Please check that the file exists.</p>
            `;
        }
    }

    // Update active navigation item
    function updateActiveNavItem(path) {
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
            if (item.dataset.path === path) {
                item.classList.add('active');
                
                // Expand parent if exists
                const parent = item.closest('.nav-children');
                if (parent) {
                    parent.classList.add('visible');
                    const parentItem = parent.previousElementSibling;
                    if (parentItem) parentItem.classList.add('expanded');
                }
            }
        });
    }

    // Handle hash changes
    function handleHashChange() {
        const hash = window.location.hash.substring(1);
        if (hash) {
            loadMarkdown(hash);
        } else {
            renderHomePage();
        }
    }

    // Render home page with card grid
    function renderHomePage() {
        if (!navigation) return;

        // Clear active state in nav
        document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));

        let html = '<div class="home-page">';
        html += '<h1>📚 Algorithm &amp; System Design Guide</h1>';
        html += '<p class="home-subtitle">Select a subject to start studying</p>';
        html += '<div class="card-grid">';

        navigation.sections.forEach(section => {
            const itemCount = section.items.length;
            const firstPath = section.items[0]?.path || '';
            const icon = section.icon || '📄';
            const description = section.description || '';

            html += `<a class="section-card" href="#${firstPath}">`;
            html += `<div class="card-icon">${icon}</div>`;
            html += `<div class="card-title">${section.title}</div>`;
            html += `<div class="card-description">${description}</div>`;
            html += `<div class="card-count">${itemCount} guide${itemCount !== 1 ? 's' : ''}</div>`;
            html += '</a>';
        });

        html += '</div></div>';
        content.innerHTML = html;
        window.scrollTo(0, 0);
    }

    // Make sidebar title clickable to go home
    function makeSidebarTitleClickable() {
        const h1 = document.querySelector('.sidebar-header h1');
        if (h1) {
            const link = document.createElement('a');
            link.href = '#';
            link.innerHTML = h1.innerHTML;
            h1.innerHTML = '';
            h1.appendChild(link);
            link.addEventListener('click', (e) => {
                e.preventDefault();
                window.location.hash = '';
                renderHomePage();
                closeMobileMenu();
            });
        }
    }

    // Search functionality
    function handleSearch(query) {
        const lowerQuery = query.toLowerCase();
        
        allNavItems.forEach(item => {
            const matches = item.title.toLowerCase().includes(lowerQuery);
            item.element.style.display = matches || !query ? '' : 'none';
        });

        // Show all sections
        document.querySelectorAll('.nav-section').forEach(section => {
            const hasVisibleItems = section.querySelectorAll('.nav-item[style=""]').length > 0 ||
                                   section.querySelectorAll('.nav-item:not([style])').length > 0;
            section.style.display = hasVisibleItems || !query ? '' : 'none';
        });
    }

    // Theme handling
    function loadTheme() {
        const savedTheme = localStorage.getItem('theme') || 'light';
        setTheme(savedTheme);
    }

    function setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
        
        // Update button text
        themeToggle.textContent = theme === 'dark' ? '☀️ Light Mode' : '🌙 Dark Mode';

        // Update highlight.js theme
        const hljsTheme = document.getElementById('hljs-theme');
        if (theme === 'dark') {
            hljsTheme.href = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css';
        } else {
            hljsTheme.href = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css';
        }
    }

    function toggleTheme() {
        const current = document.documentElement.getAttribute('data-theme');
        setTheme(current === 'dark' ? 'light' : 'dark');
    }

    // Mobile menu
    function toggleMobileMenu() {
        sidebar.classList.toggle('open');
        menuToggle.classList.toggle('active');
        overlay.classList.toggle('visible');
    }

    function closeMobileMenu() {
        sidebar.classList.remove('open');
        menuToggle.classList.remove('active');
        overlay.classList.remove('visible');
    }

    // Event listeners
    function setupEventListeners() {
        window.addEventListener('hashchange', handleHashChange);
        
        searchInput.addEventListener('input', (e) => handleSearch(e.target.value));
        
        themeToggle.addEventListener('click', toggleTheme);
        
        menuToggle.addEventListener('click', toggleMobileMenu);
        
        overlay.addEventListener('click', closeMobileMenu);

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            // Focus search with /
            if (e.key === '/' && document.activeElement !== searchInput) {
                e.preventDefault();
                searchInput.focus();
            }
            // Close mobile menu with Escape
            if (e.key === 'Escape') {
                closeMobileMenu();
                searchInput.blur();
            }
        });
    }

    // Start the app
    init();
})();
