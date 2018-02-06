/**
 *  An object that stores parameters for the BM25
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

    protected double k1;
    protected double k3;
    protected double b;
    
    public RetrievalModelBM25() {
        
    }
    
    public RetrievalModelBM25(double k1, double k3, double b) {
        this.k1 = k1;
        this.k3 = k3;
        this.b = b;
    }
    
    @Override
    public String defaultQrySopName() {
        // TODO Auto-generated method stub
        return new String("#sum");
    }

}
