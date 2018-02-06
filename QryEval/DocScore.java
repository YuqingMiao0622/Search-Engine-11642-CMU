
public class DocScore {


    private int docId;
    private double score;
    
    public DocScore(int docId, double score) {
        this.docId = docId;
        this.score = score;
    }
    
    public int getDocID() {
        return docId;
    }
    
    public double getDocScore() {
        return score;
    }
}
