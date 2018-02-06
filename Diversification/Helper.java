import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Helper {

    
    public static void printMap(Map<String, Double> map) {
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            String qid = entry.getKey();
            Double score = entry.getValue();
            System.out.println(qid + ", " + score);
        }
    }
    
    public static void printHelper(Map<String, List<DocScore>> map) throws Exception {
        for (Map.Entry<String, List<DocScore>> entry : map.entrySet()) {
            String qid = entry.getKey();
            List<DocScore> docScore = entry.getValue();
            System.out.println("query id: " + qid + ", number of docs: " + docScore.size());
            for (int i = 0; i < docScore.size(); i++) {
                System.out.println(Idx.getExternalDocid(docScore.get(i).getDocID()) + " " + (i + 1) + " " + docScore.get(i).getDocScore());
            }
        }
    }
    
    public static void printIntentScore(Map<Integer, List<IntentScore>> docIntentScore) throws IOException {
        for (Map.Entry<Integer, List<IntentScore>> entry : docIntentScore.entrySet()) {
            int internalId = entry.getKey();
            List<IntentScore> intentScores = entry.getValue();
            System.out.print(Idx.getExternalDocid(internalId) + " ");
            for (int i = 0; i < intentScores.size(); i++) {
                System.out.print(intentScores.get(i).intentId + " " + intentScores.get(i).score + " ");
            }
            System.out.println();
        }
    }
    
    public static void printList(Map<String, List<String>> queries) {
        for (Map.Entry<String, List<String>> entry : queries.entrySet()) {
            String origQuery = entry.getKey();
            List<String> intents = entry.getValue();
            System.out.print(origQuery + " ");
            for (int i = 0; i < intents.size(); i++) {
                System.out.print(intents.get(i) + " ");
            }
            System.out.println();
        }
    }
    
    public static void printIntentScore(Map<Integer, List<IntentScore>> docIntentScore, String outputPath) throws Exception {
        FileWriter writer = new FileWriter(new File(outputPath));
        for (Map.Entry<Integer, List<IntentScore>> entry : docIntentScore.entrySet()) {
            StringBuilder sb = new StringBuilder();
            int internalId = entry.getKey();
            List<IntentScore> intentScores = entry.getValue();
//            System.out.print(Idx.getExternalDocid(internalId) + " ");
            sb.append(Idx.getExternalDocid(internalId) + " ");
            for (int i = 0; i < intentScores.size(); i++) {
//                System.out.print(intentScores.get(i).intentId + " " + intentScores.get(i).score + " ");
                sb.append(intentScores.get(i).intentId + " " + intentScores.get(i).score + " ");
            }
            sb.append("\n");
            System.out.println(sb.toString());
            writer.write(sb.toString());
            writer.flush();
//            System.out.println();
        }
        writer.close();
    }
    
    public static void printResults(Map<String, List<DocScore>> results, String trecEvalOutputPath) throws Exception {
        File file = new File(trecEvalOutputPath);
        FileWriter writer = new FileWriter(file, true);
        for (Map.Entry<String, List<DocScore>> entry : results.entrySet()) {
            String qid = entry.getKey();
            List<DocScore> docs = entry.getValue();
            if (docs.size() == 0) {
                String s = qid + " Q0 dummy 1 0 fubar\n";
                writer.write(s);
                writer.flush();
            } else {
                for (int i = 0; i < docs.size(); i++) {
                    DocScore doc = docs.get(i);
                    String score = String.format("%.18f", doc.getDocScore());
                    String externalId = Idx.getExternalDocid(doc.getDocID());
                    StringBuilder sb = new StringBuilder();
                    sb.append(qid);
                    sb.append(" Q0 ");
                    sb.append(externalId);
                    sb.append(" ");
                    sb.append(i + 1);
                    sb.append(" ");
                    sb.append(score);
                    sb.append(" fubar\n");
//                    System.out.println(sb.toString());
                    writer.write(sb.toString());
                    writer.flush();
                }
            }
        }
        writer.close();
    }
    
    public static void printPM2(Map<String, PM2Values> map) {
        for (Map.Entry<String, PM2Values> entry : map.entrySet()) {
            System.out.print(entry.getKey() + ": " + entry.getValue().s + ", " + entry.getValue().v + ", " + entry.getValue().qt + " ");
        }
        System.out.println();
    }
 
}
