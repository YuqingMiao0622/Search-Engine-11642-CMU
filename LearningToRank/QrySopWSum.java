import java.io.IOException;

public class QrySopWSum extends QrySop {

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        }
        return 0;
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            double sumWeights = 0.0;
            for (int i = 0; i< this.args.size(); i++) {
                sumWeights += this.weights.get(i);
            }
            double score = 0.0;
            for (int i = 0; i< this.args.size(); i++) {
                double weight = this.weights.get(i);
                score += weight / sumWeights * ((QrySop)this.args.get(i)).getDefaultScore(r, docid);
            }
            return score;
        }
        return 0;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        } else {
            return this.docIteratorHasMatchAll(r);
        }
    }
    
    private double getScoreIndri(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double sumWeights = 0.0;
            for (int i = 0; i < this.args.size(); i++) {
                sumWeights += this.weights.get(i);
            }
            double score = 0.0;
            int docId = this.docIteratorGetMatch();
            for (int i = 0; i < this.args.size(); i++) {
                double weight = this.weights.get(i);
                double currScore = 0.0;
                QrySop q_i = (QrySop)this.args.get(i);
                
                if (!q_i.docIteratorHasMatchCache()) {
                    currScore = q_i.getDefaultScore(r, docId);
                } else if (q_i.docIteratorGetMatch() == docId) {
                    currScore = q_i.getScore(r);
                } else {
                    currScore = q_i.getDefaultScore(r, docId);
                }
                currScore = weight / sumWeights * currScore;
                score += currScore;
            }
            return score;
        }
    }

}
