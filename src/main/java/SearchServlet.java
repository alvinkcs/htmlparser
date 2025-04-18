import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@WebServlet("/search")
public class SearchServlet extends HttpServlet {
    private static final String DB_NAME = "spider_index";
    private static final int MAX_KEYWORDS = 5;
    private static final int MAX_LINKS = 5;
    
    private SearchEngine searchEngine;
    
    @Override
    public void init() throws ServletException {
        try {
            searchEngine = new SearchEngine(DB_NAME);
        } catch (IOException e) {
            throw new ServletException("Failed to initialize search engine", e);
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String query = request.getParameter("query");
        
        if (query != null && !query.isBlank()) {
            try {
                long startTime = System.currentTimeMillis();
                List<SearchEngine.SearchResult> searchResults = searchEngine.search(query);
                long endTime = System.currentTimeMillis();
                
                // Create a list of prepared result objects for the JSP
                List<Map<String, Object>> processedResults = new ArrayList<>();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                
                // Get additional info for each page
                try (InvertedIndexManager indexManager = new InvertedIndexManager(DB_NAME)) {
                    for (SearchEngine.SearchResult result : searchResults) {
                        Map<String, Object> processedResult = new HashMap<>();
                        int pageId = result.getPageId();
                        
                        // Basic info
                        processedResult.put("pageId", pageId);
                        processedResult.put("url", result.getUrl());
                        processedResult.put("title", result.getTitle().isEmpty() ? result.getUrl() : result.getTitle());
                        processedResult.put("score", String.format("%.4f", result.getScore()));
                        
                        // Format date
                        String dateStr = result.getLastModifiedDate() > 0 ? 
                            dateFormat.format(new Date(result.getLastModifiedDate())) : "Unknown";
                        processedResult.put("formattedDate", dateStr);
                        processedResult.put("size", result.getSize());
                        
                        // Process keywords
                        Map<String, Integer> keywords = indexManager.getTopKeywords(pageId, MAX_KEYWORDS);
                        StringBuilder keywordStr = new StringBuilder();
                        if (keywords != null && !keywords.isEmpty()) {
                            int count = 0;
                            for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
                                if (count > 0) {
                                    keywordStr.append("; ");
                                }
                                keywordStr.append(entry.getKey()).append(" ").append(entry.getValue());
                                count++;
                                if (count >= MAX_KEYWORDS) break;
                            }
                        } else {
                            keywordStr.append("No keywords available");
                        }
                        processedResult.put("formattedKeywords", keywordStr.toString());
                        
                        // Get page info with child links
                        InvertedIndexManager.PageInfo pageInfo = indexManager.getPageInfo(pageId);
                        if (pageInfo != null) {
                            // Process child links
                            List<String> childLinks = new ArrayList<>();
                            for (int i = 0; i < Math.min(pageInfo.childPageIds.size(), MAX_LINKS); i++) {
                                Integer childId = pageInfo.childPageIds.get(i);
                                InvertedIndexManager.PageInfo childInfo = indexManager.getPageInfo(childId);
                                if (childInfo != null) {
                                    childLinks.add(childInfo.url);
                                }
                            }
                            processedResult.put("childLinks", childLinks);
                            
                            // Process parent links
                            List<String> parentLinks = new ArrayList<>();
                            for (Integer possibleParentId : indexManager.getAllPageIds()) {
                                InvertedIndexManager.PageInfo possibleParent = indexManager.getPageInfo(possibleParentId);
                                if (possibleParent != null && possibleParent.childPageIds.contains(pageId)) {
                                    parentLinks.add(possibleParent.url);
                                    if (parentLinks.size() >= MAX_LINKS) break;
                                }
                            }
                            processedResult.put("parentLinks", parentLinks);
                        } else {
                            processedResult.put("childLinks", Collections.emptyList());
                            processedResult.put("parentLinks", Collections.emptyList());
                        }
                        
                        processedResults.add(processedResult);
                    }
                }
                
                request.setAttribute("results", processedResults);
                request.setAttribute("query", query);
                request.setAttribute("searchTime", (endTime - startTime));
                request.setAttribute("resultCount", searchResults.size());
            } catch (Exception e) {
                request.setAttribute("error", "Error executing search: " + e.getMessage());
            }
        }
        
        request.getRequestDispatcher("/index.jsp").forward(request, response);
    }
    
    @Override
    public void destroy() {
        if (searchEngine != null) {
            try {
                searchEngine.close();
            } catch (Exception e) {
                getServletContext().log("Error closing search engine", e);
            }
        }
    }
} 