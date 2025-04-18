<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.*" %>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Web Search Engine</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
    <div class="search-container">
        <h1>Web Search Engine</h1>
        <form action="search" method="GET">
            <div class="search-box-container">
                <input type="text" name="query" value="<%= request.getParameter("query") != null ? request.getParameter("query") : "" %>" 
                       class="search-box" placeholder="Enter search query (use quotes for phrases)">
                <button type="submit" class="search-button">Search</button>
            </div>
        </form>
    </div>
    
    <div class="container">
        <% if (request.getAttribute("error") != null) { %>
            <div class="error"><%= request.getAttribute("error") %></div>
        <% } %>
        
        <% 
        List<Map<String, Object>> results = (List<Map<String, Object>>) request.getAttribute("results");
        if (results != null && !results.isEmpty()) { 
            Integer resultCount = (Integer) request.getAttribute("resultCount");
            Long searchTime = (Long) request.getAttribute("searchTime");
        %>
            <div class="result-stats">
                Found <%= resultCount %> results (<%= searchTime %> ms)
            </div>
            
            <div class="results-container">
                <% for (Map<String, Object> result : results) { %>
                    <div class="result-item">
                        <div class="score">
                            <%= result.get("score") %>
                        </div>
                        
                        <div class="result-title">
                            <a href="<%= result.get("url") %>" target="_blank">
                                <%= result.get("title") %>
                            </a>
                        </div>
                        
                        <div class="result-url">
                            <a href="<%= result.get("url") %>" target="_blank"><%= result.get("url") %></a>
                        </div>
                        
                        <div class="result-meta">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                            <%= result.get("formattedDate") %>, <%= result.get("size") %> bytes
                        </div>
                        
                        <div class="result-keywords">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"></path><line x1="7" y1="7" x2="7.01" y2="7"></line></svg>
                            <%= result.get("formattedKeywords") %>
                        </div>
                        
                        <% 
                        List<String> parentLinks = (List<String>) result.get("parentLinks");
                        List<String> childLinks = (List<String>) result.get("childLinks");
                        boolean hasLinks = (parentLinks != null && !parentLinks.isEmpty()) || 
                                          (childLinks != null && !childLinks.isEmpty());
                        if (hasLinks) {
                        %>
                            <div class="links-section">
                                <!-- Parent links -->
                                <% if (parentLinks != null && !parentLinks.isEmpty()) { %>
                                    <div class="links-title">Parent Links:</div>
                                    <div class="result-links">
                                        <% for (String parentUrl : parentLinks) { %>
                                            <div>
                                                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;"><line x1="7" y1="17" x2="17" y2="7"></line><polyline points="7 7 17 7 17 17"></polyline></svg>
                                                <a href="<%= parentUrl %>" target="_blank"><%= parentUrl %></a>
                                            </div>
                                        <% } %>
                                    </div>
                                <% } %>
                                
                                <!-- Child links -->
                                <% if (childLinks != null && !childLinks.isEmpty()) { %>
                                    <div class="links-title">Child Links:</div>
                                    <div class="result-links">
                                        <% for (String childUrl : childLinks) { %>
                                            <div>
                                                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;"><line x1="7" y1="17" x2="17" y2="7"></line><polyline points="7 7 17 7 17 17"></polyline></svg>
                                                <a href="<%= childUrl %>" target="_blank"><%= childUrl %></a>
                                            </div>
                                        <% } %>
                                    </div>
                                <% } %>
                            </div>
                        <% } %>
                    </div>
                <% } %>
            </div>
        <% } else if (request.getParameter("query") != null) { %>
            <div class="no-results">
                <h2>No results found</h2>
                <p>Try a different search query or check if the index has been built.</p>
            </div>
        <% } else { %>
            <div class="help-section">
                <h2>How to Use This Search Engine</h2>
                <ol>
                    <li>Make sure you have run the crawler (HtmlParser) first to build the index</li>
                    <li>Enter your search terms in the box above</li>
                    <li>Use quotes for phrases, e.g. "hong kong" university</li>
                    <li>Click the Search button to see results</li>
                </ol>
            </div>
        <% } %>
    </div>
</body>
</html> 