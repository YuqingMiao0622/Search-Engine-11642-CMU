import java.io.IOException;

public class QrySopWAnd extends QrySop {

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            return getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
            (r.getClass().getName() + " doesn't support the WAND operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            double sumWeights = 0.0;
            for (int i = 0; i < this.args.size(); i++) {
                sumWeights += this.weights.get(i);
            }
            double score = 1.0;
            for (int i = 0; i < this.args.size(); i++) {
                Qry q_i = this.args.get(i);
                double weight = this.weights.get(i);
                score *= Math.pow(((QrySop)q_i).getDefaultScore(r, docid), weight / sumWeights);
            }
            return score;
        }
        return 0.0;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        }
        return this.docIteratorHasMatchAll(r);
    }
    
    private double getScoreIndri(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double sumWeights = 0.0;
            for (int i = 0; i < this.args.size(); i++) {
                sumWeights += this.weights.get(i);
            }
//            System.out.println("sum weights: " + sumWeights);
            int docId = this.docIteratorGetMatch();
            double score = 1.0;
   
            for (int i = 0; i < this.args.size(); i++) {
                Qry q_i = this.args.get(i);
                double weight = this.weights.get(i);
                double currScore = 0.0;
                if (!q_i.docIteratorHasMatchCache()) {
//                  System.out.println("get default score");
                    currScore = Math.pow(((QrySop)q_i).getDefaultScore(r, docId), weight / sumWeights);
                } else if (q_i.docIteratorGetMatch() == docId){
    //                  System.out.println("get score");
                    currScore = Math.pow(((QrySop)q_i).getScore(r), weight / sumWeights);
                } else {
    //                  System.out.println("get default score");
                    currScore = Math.pow(((QrySop)q_i).getDefaultScore(r, docId), weight / sumWeights);
                }
                score *= currScore;
            }
            return score;
        }
    }

}
