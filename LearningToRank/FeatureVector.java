import java.util.Arrays;

public class FeatureVector {

    public String docId;           // external document id
    public String qid;             // query id
    public String relevance;       // relevance judgment
    public double[] featureVector;
    
    FeatureVector() {
        featureVector = new double[18];
        Arrays.fill(featureVector, Double.NaN);
    }
    
    public void setFeature(int i, double score) {
        featureVector[i - 1] = score;
    }
    
    public double getFeature(int i) {
        return featureVector[i - 1];
    }
}
