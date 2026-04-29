/**
 * Navigation Helper Script
 * Provides consistent back button functionality across all pages
 */

// ======================================
// BACK BUTTON NAVIGATION
// ======================================

/**
 * Go back to previous page
 */
function goBack() {
    if (window.history.length > 1) {
        window.history.back();
    } else {
        // If no history, go to dashboard
        window.location.href = 'dashboard.html';
    }
}

/**
 * Navigate to a specific page
 */
function navigateTo(page) {
    window.location.href = page;
}

/**
 * Add back button to a container
 * @param {string} containerId - ID of container to add back button to
 */
function addBackButton(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const backBtn = document.createElement('button');
    backBtn.className = 'back-btn';
    backBtn.innerHTML = '← Back';
    backBtn.onclick = goBack;

    container.insertBefore(backBtn, container.firstChild);
}

/**
 * Create a breadcrumb navigation
 * @param {Array} items - Array of {label, href} objects
 * @param {string} containerId - Container to add breadcrumb to
 */
function createBreadcrumb(items, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const breadcrumb = document.createElement('nav');
    breadcrumb.className = 'breadcrumb';

    items.forEach((item, index) => {
        if (index > 0) {
            const separator = document.createElement('span');
            separator.textContent = '›';
            breadcrumb.appendChild(separator);
        }

        const link = document.createElement('a');
        link.href = item.href || '#';
        link.textContent = item.label;
        link.onclick = (e) => {
            if (!item.href) e.preventDefault();
        };
        breadcrumb.appendChild(link);
    });

    container.insertBefore(breadcrumb, container.firstChild);
}

/**
 * Setup sticky back button in header
 */
function setupStickyBackButton() {
    // Check if back button exists
    const existingBtn = document.querySelector('.back-btn-sticky');
    if (existingBtn) return;

    const header = document.querySelector('header, .page-header, h1, .topbar');
    if (!header) return;

    const backBtn = document.createElement('button');
    backBtn.className = 'back-btn-sticky';
    backBtn.innerHTML = '← Back';
    backBtn.onclick = goBack;

    // Add sticky style
    const style = document.createElement('style');
    style.textContent = `
        .back-btn-sticky {
            position: sticky;
            top: 0;
            background: #0ea5e9;
            color: white;
            border: none;
            padding: 10px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-weight: bold;
            z-index: 100;
            margin-bottom: 10px;
            transition: background 0.3s;
        }
        .back-btn-sticky:hover {
            background: #0284c7;
        }
    `;
    document.head.appendChild(style);

    header.parentNode.insertBefore(backBtn, header);
}

/**
 * Setup navigation sidebar
 */
function setupNavigationSidebar() {
    const navItems = [
        { label: '📊 Dashboard', href: 'dashboard.html' },
        { label: '📈 Performance', href: 'performance.html' },
        { label: '📺 Screen', href: 'screen.html' },
        { label: '📜 History', href: 'history.html' },
        { label: '🎛️ Controls', href: 'controls.html' },
        { label: '📁 Files', href: 'files.html' }
    ];

    // Highlight current page
    const currentPage = window.location.pathname.split('/').pop() || 'dashboard.html';
    navItems.forEach(item => {
        const link = document.querySelector(`a[href="${item.href}"]`);
        if (link && item.href === currentPage) {
            link.classList.add('active');
        }
    });
}

/**
 * Track page navigation for analytics
 */
function trackNavigation(pageName) {
    try {
        const navigation = JSON.parse(sessionStorage.getItem('navigation') || '[]');
        navigation.push({
            page: pageName,
            timestamp: new Date().toISOString()
        });
        sessionStorage.setItem('navigation', JSON.stringify(navigation));
    } catch (e) {
        console.error('Error tracking navigation:', e);
    }
}

/**
 * Get navigation history
 */
function getNavigationHistory() {
    try {
        return JSON.parse(sessionStorage.getItem('navigation') || '[]');
    } catch (e) {
        return [];
    }
}

/**
 * Initialize navigation on page load
 */
document.addEventListener('DOMContentLoaded', () => {
    setupNavigationSidebar();
    
    // Track page navigation
    const pageName = document.title || 'Unknown';
    trackNavigation(pageName);
});

// ======================================
// PAGE TRANSITION ANIMATIONS
// ======================================

/**
 * Add smooth page transition effect
 */
function addPageTransition() {
    const style = document.createElement('style');
    style.textContent = `
        body {
            animation: fadeIn 0.3s ease-out;
        }
        @keyframes fadeIn {
            from {
                opacity: 0;
                transform: translateY(10px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
    `;
    document.head.appendChild(style);
}

addPageTransition();

// ======================================
// KEYBOARD NAVIGATION
// ======================================

/**
 * Setup keyboard shortcuts
 */
document.addEventListener('keydown', (event) => {
    // Alt + B = Go Back
    if (event.altKey && event.key === 'b') {
        event.preventDefault();
        goBack();
    }

    // Alt + H = Go to Home/Dashboard
    if (event.altKey && event.key === 'h') {
        event.preventDefault();
        navigateTo('dashboard.html');
    }
});
