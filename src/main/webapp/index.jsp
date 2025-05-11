<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.*" %>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Web Search Engine</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">
    <!-- Preconnect to improve performance -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
</head>
<body>
    <div class="hero-section">
        <div class="search-container">
            <h1><i class="fas fa-search-location"></i> Advanced Web Search Engine</h1>
            <form action="search" method="GET" id="searchForm">
                <div class="search-box-container">
                    <input type="text" name="query" id="queryInput" value="<%= request.getParameter("query") != null ? request.getParameter("query") : "" %>" 
                           class="search-box" placeholder="Enter search query (use quotes for phrases)" autofocus>
                    <button type="submit" class="search-button">
                        <i class="fas fa-search"></i> Search
                    </button>
                </div>
            </form>
            
            <% 
            List<String> recentSearches = (List<String>) request.getAttribute("recentSearches");
            if (recentSearches != null && !recentSearches.isEmpty()) { 
            %>
                <div class="recent-searches">
                    <% for (int i = recentSearches.size() - 1; i >= 0; i--) { 
                        String escapedQuery = recentSearches.get(i).replace("'", "\\'").replace('\"', '`');
                    %>
                        <div class="recent-search-item" onclick="loadSearch('<%= escapedQuery %>')">
                            <i class="fas fa-history"></i> <%= recentSearches.get(i) %>
                        </div>
                    <% } %>
                </div>
            <% } %>
        </div>
    </div>
    
    <div class="container">
        <% if (request.getAttribute("error") != null) { %>
            <div class="error">
                <i class="fas fa-exclamation-circle"></i> <%= request.getAttribute("error") %>
            </div>
        <% } %>
        
        <% 
        List<Map<String, Object>> results = (List<Map<String, Object>>) request.getAttribute("results");
        if (results != null && !results.isEmpty()) { 
            Integer resultCount = (Integer) request.getAttribute("resultCount");
            Long searchTime = (Long) request.getAttribute("searchTime");
        %>
            <div class="result-stats">
                <i class="fas fa-chart-bar"></i> Found <strong><%= resultCount %></strong> results (<%= searchTime %> ms)
            </div>
            
            <div class="results-container">
                <% for (Map<String, Object> result : results) { %>
                    <div class="result-item">
                        <div class="score" title="Relevance Score">
                            <i class="fas fa-star"></i> <%= result.get("score") %>
                        </div>
                        
                        <div class="result-title">
                            <a href="<%= result.get("url") %>" target="_blank">
                                <i class="fas fa-external-link-alt"></i> <%= result.get("title") %>
                            </a>
                        </div>
                        
                        <div class="result-url">
                            <i class="fas fa-link"></i> <a href="<%= result.get("url") %>" target="_blank"><%= result.get("url") %></a>
                        </div>
                        
                        <div class="result-meta">
                            <span><i class="far fa-clock"></i> <%= result.get("formattedDate") %></span>
                            <span class="meta-divider">|</span>
                            <span><i class="fas fa-file-alt"></i> <%= result.get("size") %> bytes</span>
                        </div>
                        
                        <div class="result-keywords">
                            <div class="keyword-title"><i class="fas fa-tags"></i> Top Keywords:</div>
                            <div class="keyword-pills">
                                <% 
                                String keywordsStr = (String) result.get("formattedKeywords");
                                if (!keywordsStr.equals("No keywords available")) {
                                    String[] keywordPairs = keywordsStr.split("; ");
                                    for (String pair : keywordPairs) {
                                        String[] parts = pair.split(" ");
                                        if (parts.length >= 2) {
                                            // Join all but the last part for the keyword, last part is the count
                                            String keyword = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
                                            String count = parts[parts.length - 1];
                                %>
                                    <span class="keyword-pill"><%= keyword %> <span class="keyword-count"><%= count %></span></span>
                                <%
                                        }
                                    }
                                } else {
                                %>
                                    <span class="no-keywords">No keywords available</span>
                                <%
                                }
                                %>
                            </div>
                        </div>
                        
                        <!-- Get Similar Pages Button -->
                        <div class="action-buttons">
                            <form action="search" method="GET">
                                <input type="hidden" name="query" value="<%= result.get("topKeywordsQuery") %>" />
                                <button type="submit" class="similar-pages-button">
                                    <i class="fas fa-layer-group"></i> Find Similar Pages
                                </button>
                            </form>
                        </div>
                        
                        <% 
                        List<String> parentLinks = (List<String>) result.get("parentLinks");
                        List<String> childLinks = (List<String>) result.get("childLinks");
                        boolean hasLinks = (parentLinks != null && !parentLinks.isEmpty()) || 
                                          (childLinks != null && !childLinks.isEmpty());
                        if (hasLinks) {
                        %>
                            <div class="links-section">
                                <div class="links-container">
                                    <!-- Parent links -->
                                    <% if (parentLinks != null && !parentLinks.isEmpty()) { %>
                                        <div class="links-column">
                                            <div class="links-title"><i class="fas fa-level-up-alt"></i> Parent Links:</div>
                                            <div class="result-links">
                                                <% for (String parentUrl : parentLinks) { %>
                                                    <div class="link-item">
                                                        <i class="fas fa-angle-right"></i>
                                                        <a href="<%= parentUrl %>" target="_blank" title="<%= parentUrl %>">
                                                            <%= parentUrl.length() > 50 ? parentUrl.substring(0, 47) + "..." : parentUrl %>
                                                        </a>
                                                    </div>
                                                <% } %>
                                            </div>
                                        </div>
                                    <% } %>
                                    
                                    <!-- Child links -->
                                    <% if (childLinks != null && !childLinks.isEmpty()) { %>
                                        <div class="links-column">
                                            <div class="links-title"><i class="fas fa-level-down-alt"></i> Child Links:</div>
                                            <div class="result-links">
                                                <% for (String childUrl : childLinks) { %>
                                                    <div class="link-item">
                                                        <i class="fas fa-angle-right"></i>
                                                        <a href="<%= childUrl %>" target="_blank" title="<%= childUrl %>">
                                                            <%= childUrl.length() > 50 ? childUrl.substring(0, 47) + "..." : childUrl %>
                                                        </a>
                                                    </div>
                                                <% } %>
                                            </div>
                                        </div>
                                    <% } %>
                                </div>
                            </div>
                        <% } %>
                    </div>
                <% } %>
            </div>
            
        <% } else if (request.getParameter("query") != null) { %>
            <div class="no-results">
                <i class="fas fa-search" style="font-size: 48px; margin-bottom: 20px; color: #ddd;"></i>
                <h2>No results found</h2>
                <p>Try a different search query or check if the index has been built.</p>
                <div class="search-suggestions">
                    <p>Suggestions:</p>
                    <ul>
                        <li>Check if your spelling is correct</li>
                        <li>Try more general keywords</li>
                        <li>Reduce the number of keywords</li>
                    </ul>
                </div>
            </div>
        <% } else { %>
            <div class="help-section">
                <h2><i class="fas fa-info-circle"></i> How to Use This Search Engine</h2>
                <div class="help-grid">
                    <div class="help-card">
                        <div class="help-icon"><i class="fas fa-database"></i></div>
                        <h3>Build the Index</h3>
                        <p>Make sure you have run the crawler (HtmlParser) first to build the index</p>
                    </div>
                    <div class="help-card">
                        <div class="help-icon"><i class="fas fa-keyboard"></i></div>
                        <h3>Enter Search Terms</h3>
                        <p>Type your search query in the search box above</p>
                    </div>
                    <div class="help-card">
                        <div class="help-icon"><i class="fas fa-quote-right"></i></div>
                        <h3>Use Quotes for Phrases</h3>
                        <p>For example: "hong kong" university</p>
                    </div>
                    <div class="help-card">
                        <div class="help-icon"><i class="fas fa-search"></i></div>
                        <h3>Search</h3>
                        <p>Click the Search button to see results</p>
                    </div>
                </div>
            </div>
        <% } %>
    </div>
    
    <footer class="footer">
        <div class="container">
            <p>&copy; <%= new java.util.Date().getYear() + 1900 %> Web Search Engine - A Java-based search engine with TF-IDF weighting</p>
        </div>
    </footer>
    
    <script>
        // Function to handle clicking on a recent search
        function loadSearch(query) {
            document.getElementById('queryInput').value = query.replaceAll('`', '\"');
            document.getElementById('searchForm').submit();
        }
    </script>
</body>
</html>