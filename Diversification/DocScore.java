import java.util.Comparator;

public class DocScore implements Comparator<DocScore> {


    private int docId;
    private double score;
    
    DocScore() {}
    
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

    @Override
    public int compare(DocScore o1, DocScore o2) {
        // TODO Auto-generated method stub
        return Double.compare(o2.score, o1.score);
    }
}
