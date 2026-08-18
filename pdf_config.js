module.exports = {
    // Basic styling (GitHub-like)
    stylesheet: ['https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.2.0/github-markdown.min.css'],
    
    // Custom CSS adjustments
    css: `
        .markdown-body {
            box-sizing: border-box;
            min-width: 200px;
            max-width: 980px;
            margin: 0 auto;
            padding: 45px;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            font-size: 14px;
            line-height: 1.6;
        }
        
        /* Better heading styles */
        h1 {
            border-bottom: 2px solid #0366d6;
            padding-bottom: 0.3em;
            margin-top: 1.5em;
        }
        h2 {
            border-bottom: 1px solid #eaecef;
            padding-bottom: 0.2em;
            margin-top: 1.2em;
        }
        h3 {
            margin-top: 1em;
        }
        
        /* Key idea blockquotes styling */
        blockquote {
            background-color: #f6f8fa;
            border-left: 4px solid #0366d6;
            padding: 0.5em 1em;
            margin: 0.5em 0;
        }
        blockquote strong {
            color: #0366d6;
        }
        
        /* Code blocks */
        pre {
            background-color: #f6f8fa;
            border-radius: 6px;
            padding: 16px;
            overflow-x: auto;
            font-size: 13px;
        }
        code {
            font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
        }
        
        /* Tables */
        table {
            border-collapse: collapse;
            width: 100%;
            margin: 1em 0;
        }
        th, td {
            border: 1px solid #dfe2e5;
            padding: 8px 12px;
            text-align: left;
        }
        th {
            background-color: #f6f8fa;
            font-weight: 600;
        }
        tr:nth-child(even) {
            background-color: #f9f9f9;
        }
        
        /* Horizontal rule as section separator */
        hr {
            border: none;
            border-top: 1px solid #eaecef;
            margin: 2em 0;
        }
        
        @media print {
            .markdown-body {
                padding: 0;
                max-width: 100%;
            }
            
            /* Page break before each major section (H1 with numbers) */
            h1 {
                page-break-before: always;
                break-before: always;
                margin-top: 0;
            }
            
            /* But not for the first H1 (title) or Table of Contents */
            h1:first-of-type,
            h1:nth-of-type(2) {
                page-break-before: avoid;
                break-before: avoid;
            }
            
            /* Prevent page breaks after headings */
            h1, h2, h3, h4, h5, h6 {
                page-break-after: avoid;
                break-after: avoid;
            }
            
            /* Keep code blocks together - but allow break if too long */
            pre {
                page-break-inside: avoid;
                break-inside: avoid;
                max-height: 90vh;
                overflow: hidden;
            }
            
            /* Keep blockquotes together */
            blockquote {
                page-break-inside: avoid;
                break-inside: avoid;
            }
            
            /* Keep tables together if small enough */
            table {
                page-break-inside: avoid;
                break-inside: avoid;
            }
            
            /* Keep list items together */
            li {
                page-break-inside: avoid;
                break-inside: avoid;
            }
            
            /* Ensure headings stay with following content */
            h2 + *, h3 + * {
                page-break-before: avoid;
                break-before: avoid;
            }
            
            /* ASCII diagrams in pre blocks */
            pre code {
                white-space: pre;
                font-size: 11px;
            }
        }
        
        /* Mermaid diagrams */
        .mermaid {
            margin-bottom: 2em;
            text-align: center;
        }
    `,
    
    body_class: 'markdown-body',

    // Scripts to run in the browser before printing
    script: [
        // 1. Load Mermaid (Using v10)
        { url: 'https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js' },
        
        // 2. Transform DOM and Initialize
        {
            content: `
                window.onload = async function() {
                    // Initialize mermaid config first
                    mermaid.initialize({
                        startOnLoad: false,
                        theme: 'default',
                        securityLevel: 'loose', 
                        flowchart: { htmlLabels: true }
                    });

                    // Find all code blocks with language-mermaid or mermaid class
                    const mermaidCodes = document.querySelectorAll('code.language-mermaid, code.mermaid');
                    
                    const nodesToRender = [];

                    mermaidCodes.forEach(codeBlock => {
                        const pre = codeBlock.parentElement;
                        
                        if (pre.tagName === 'PRE') {
                            const div = document.createElement('div');
                            div.className = 'mermaid';
                            div.textContent = codeBlock.textContent;
                            
                            pre.replaceWith(div);
                            nodesToRender.push(div);
                        }
                    });

                    if (nodesToRender.length > 0) {
                        try {
                            await mermaid.run({
                                nodes: nodesToRender
                            });
                        } catch (e) {
                            console.error('Mermaid generation failed:', e);
                        }
                    }
                };
            `
        }
    ],

    // PDF options
    pdf_options: {
        format: 'A4',
        margin: {
            top: '20mm',
            bottom: '20mm',
            left: '15mm',
            right: '15mm'
        },
        printBackground: true,
        displayHeaderFooter: true,
        headerTemplate: '<div></div>',
        footerTemplate: '<div style="width: 100%; font-size: 10px; text-align: center; color: #666;"><span class="pageNumber"></span> / <span class="totalPages"></span></div>'
    }
};
