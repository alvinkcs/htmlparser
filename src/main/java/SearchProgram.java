import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Command-line search program for testing the search engine
 */
public class SearchProgram {
    private static final String DB_NAME = "spider_index";
    
    public static void main(String[] args) {
        String dbName = args.length > 0 ? args[0] : DB_NAME;
        
        System.out.println("Search Engine (Vector Space Model with tf-idf weighting)");
        System.out.println("Database: " + dbName);
        System.out.println("Enter a query (or 'exit' to quit):");
        System.out.println("Tip: Use quotes for phrase search, e.g., \"hong kong\" university");
        System.out.println();
        
        try (
            SearchEngine searchEngine = new SearchEngine(dbName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        ) {
            while (true) {
                System.out.print("> ");
                String query = reader.readLine().trim();
                
                if (query.equalsIgnoreCase("exit") || query.equalsIgnoreCase("quit")) {
                    break;
                }
                
                if (query.isEmpty()) {
                    continue;
                }
                
                long startTime = System.currentTimeMillis();
                List<SearchEngine.SearchResult> results = searchEngine.search(query);
                long endTime = System.currentTimeMillis();
                
                System.out.println("Found " + results.size() + " results (" + (endTime - startTime) + "ms)");
                
                if (results.isEmpty()) {
                    System.out.println("No matching documents found.");
                } else {
                    // Show the range of scores to help understand score distribution
                    if (!results.isEmpty()) {
                        double maxScore = results.get(0).getScore();
                        double minScore = results.get(results.size() - 1).getScore();
                        System.out.println("Score range: " + String.format("%.4f", minScore) + " to " + 
                                           String.format("%.4f", maxScore));
                    }
                    
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    
                    for (int i = 0; i < Math.min(results.size(),50); i++) {
                        SearchEngine.SearchResult result = results.get(i);
                        System.out.println((i + 1) + ". " + (result.getTitle().isEmpty() ? result.getUrl() : result.getTitle()));
                        System.out.println("   URL: " + result.getUrl());
                        System.out.println("   Score: " + String.format("%.4f", result.getScore()));
                        
                        String dateStr = result.getLastModifiedDate() > 0 ? 
                                        dateFormat.format(new Date(result.getLastModifiedDate())) : 
                                        "Unknown";
                        System.out.println("   Last Modified: " + dateStr);
                        System.out.println("   Size: " + result.getSize() + " bytes");
                        System.out.println();
                    }
                }
                
                System.out.println("----------------------------------");
            }
            
            System.out.println("Goodbye!");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 