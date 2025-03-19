import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Test program that reads data from the JDBM database and generates the spider_result.txt file.
 */
public class TestProgram {
    private static final String OUTPUT_FILE = "spider_result.txt";
    private static final int MAX_KEYWORDS_TO_DISPLAY = 10;
    private static final int MAX_LINKS_TO_DISPLAY = 10;
    
    public static void main(String[] args) {
        String dbName = args.length > 0 ? args[0] : "spider_index";
        
        System.out.println("Reading from database: " + dbName);
        
        try (InvertedIndexManager indexManager = new InvertedIndexManager(dbName)) {
            try {
                List<Integer> pageIds = indexManager.getAllPageIds();
                System.out.println("Found " + pageIds.size() + " pages in the database.");
                
                if (pageIds.isEmpty()) {
                    System.out.println("WARNING: No pages found in the database!");
                    System.out.println("Database keys may be stored differently. Let's try to print all available keys:");
                    
                } else {
                    for (Integer pageId : pageIds) {
                        InvertedIndexManager.PageInfo info = indexManager.getPageInfo(pageId);
                        System.out.println("Page ID: " + pageId + ", URL: " + (info != null ? info.url : "null"));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting debug info: " + e.getMessage());
                e.printStackTrace();
            }
            
            generateResultFile(indexManager);
            System.out.println("Spider result file generated: " + OUTPUT_FILE);
        } catch (Exception e) {
            System.err.println("Error processing database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generates the spider result text file from the database
     */
    private static void generateResultFile(InvertedIndexManager indexManager) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {
            List<Integer> pageIds = indexManager.getAllPageIds();
            
            System.out.println("Found " + pageIds.size() + " pages in the database.");
            
            for (Integer pageId : pageIds) {
                InvertedIndexManager.PageInfo pageInfo = indexManager.getPageInfo(pageId);
                
                if (pageInfo != null) {
                    String title = pageInfo.title.isEmpty() ? pageInfo.url : pageInfo.title;
                    writer.write(title);
                    writer.newLine();
                    
                    writer.write(pageInfo.url);
                    writer.newLine();
                    
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String dateStr = pageInfo.lastModifiedDate > 0 ? 
                                     dateFormat.format(new Date(pageInfo.lastModifiedDate)) : 
                                     "Unknown";
                    writer.write(dateStr + ", " + pageInfo.size + " bytes");
                    writer.newLine();
                    
                    Map<String, Integer> keywords = indexManager.getTopKeywords(pageId, MAX_KEYWORDS_TO_DISPLAY);
                    if (!keywords.isEmpty()) {
                        StringBuilder keywordStr = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
                            if (keywordStr.length() > 0) {
                                keywordStr.append("; ");
                            }
                            keywordStr.append(entry.getKey()).append(" ").append(entry.getValue());
                        }
                        writer.write(keywordStr.toString());
                        writer.newLine();
                    } else {
                        writer.write("No keywords");
                        writer.newLine();
                    }
                    
                    // Child links
                    int linkCount = 0;
                    for (Integer childId : pageInfo.childPageIds) {
                        if (linkCount >= MAX_LINKS_TO_DISPLAY) break;
                        
                        InvertedIndexManager.PageInfo childInfo = indexManager.getPageInfo(childId);
                        if (childInfo != null) {
                            writer.write(childInfo.url);
                            writer.newLine();
                            linkCount++;
                        }
                    }
                    
                    writer.write("----------------------------------------");
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error generating result file: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 