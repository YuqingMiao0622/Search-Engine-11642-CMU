import java.io.IOException;

public class QrySopSum extends QrySop {

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelBM25) {
            return getScoreBM25(r);
        }
        return 0;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        // TODO Auto-generated method stub
        return this.docIteratorHasMatchMin(r);
    }
    
    private double getScoreBM25(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            // Similar to OR operator. The document is retrieved as long as the document contains
            // one of the query terms. So need to check whether the current term is cached AND the
            // document pointer points to the same document.
            double docId = this.docIteratorGetMatch();
            double score = 0;
            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatchCache()) {
                    if (q_i.docIteratorGetMatch() == docId) {
                        score += ((QrySop)q_i).getScore(r);                        
                    }
                }
            }
            return score;
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        // TODO Auto-generated method stub
        if (r instanceof RetrievalModelIndri) {
            double score = 0.0;
            for (Qry q_i : this.args) {
                score = score + ((QrySop)q_i).getDefaultScore(r, docid);
            }
            return score;
        }
        return 0.0;
    }

}
