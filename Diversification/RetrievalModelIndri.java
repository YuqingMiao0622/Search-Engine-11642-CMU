
public class RetrievalModelIndri extends RetrievalModel {

    protected double mu;
    protected double lambda;
    
    RetrievalModelIndri(double mu, double lambda) {
        this.mu = mu;
        this.lambda = lambda;
    }

    @Override
    public String defaultQrySopName() {
        // TODO Auto-generated method stub
        return new String("#and");
    }

}
