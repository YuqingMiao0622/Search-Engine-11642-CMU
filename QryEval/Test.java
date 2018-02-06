import java.text.DecimalFormat;

public class Test {

    public static void main(String[] args) {
        /*
        try {
            int id = Idx.getInternalDocid("GX000-35-14206301");
            System.out.println(id);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        */
        
        DecimalFormat formatter = new DecimalFormat("#0.0000");
        System.out.println(formatter.format(124.33333));
    }
}
