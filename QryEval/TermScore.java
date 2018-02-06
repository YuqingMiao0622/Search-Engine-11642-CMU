import java.util.Comparator;

public class TermScore implements Comparator<TermScore> {

    private String term;
    private double score;
    
    public TermScore() {
        
    }
    
    public TermScore(String term, double score) {
        this.term = term;
        this.score = score;
    }

    @Override
    public int compare(TermScore o1, TermScore o2) {
        // TODO Auto-generated method stub
        return Double.compare(o2.score, o1.score);
    }
    
    public String getTerm() {
        return term;
    }
    
    public double getScore() {
        return score;
    }
    
    public void setScore(double s) {
        score = s;
    }
}
