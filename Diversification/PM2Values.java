
public class PM2Values {

    public String intentId;
    public double v;
    public double s;
    public double qt;
    
    PM2Values(String intentId, double v, double s, double qt) {
        this.intentId = intentId;
        this.v = v;
        this.s = s;
        this.qt = qt;
    }
    
    PM2Values(double v, double s) {
        this.v = v;
        this.s = s;
    }
}
