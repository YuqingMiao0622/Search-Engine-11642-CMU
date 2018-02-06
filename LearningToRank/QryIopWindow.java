import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QryIopWindow extends QryIop {

    private int distance;
    
    QryIopWindow(int distance) {
        this.distance = distance;
    }
    
    @Override
    protected void evaluate() throws IOException {
        // TODO Auto-generated method stub
        this.invertedList = new InvList();
        if (this.args.size() == 0) {
            return;
        }
        
        while (true) {
            // find the min docId
            int minDocId = Integer.MAX_VALUE;
            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatch(null)) {
                    int docId = q_i.docIteratorGetMatch();
                    minDocId = Math.min(minDocId, docId);
                }
            }
            if (minDocId == Integer.MAX_VALUE) {
                break;
            }
//            System.out.println("minDocId: " + minDocId);
            boolean isSameDoc = true;
            int minQueryIndex = Integer.MIN_VALUE;
            for (int i = 0; i < this.args.size(); i++) {
                Qry q_i = this.args.get(i);
                if (q_i.docIteratorHasMatch(null)) {
                    if (q_i.docIteratorGetMatch() != minDocId) {
                        isSameDoc = false;
                    } else {
                        minQueryIndex = i;
                    }
                } else {
                    isSameDoc = false;
                    break;
                }
            }
            if (minQueryIndex == Integer.MIN_VALUE) {
                break;
            }
            
            if (!isSameDoc) {
                this.args.get(minQueryIndex).docIteratorAdvancePast(minDocId);
            } else {
                // same document, deal with position requirements.
                List<Integer> positions = new ArrayList<Integer>();
                boolean isEnd = false;
                while (!isEnd) {
                    boolean isMatched = false;
                    int maxLoc = 0;
                    int minLoc = Integer.MAX_VALUE;
                    int minLocIndex = Integer.MIN_VALUE;
                    
                    for (int i = 0; i < this.args.size(); i++) {
                        QryIop query = (QryIop)this.args.get(i);
                        if (query.locIteratorHasMatch()) {
                            int position = query.locIteratorGetMatch();
                            maxLoc = Math.max(maxLoc, position);
                            if (position < minLoc) {
                                minLoc = position;
                                minLocIndex = i;
                            }

                            if (maxLoc - minLoc >= distance) {
                                // do not match. Advance the iterator of the minimum location.
                                ((QryIop)this.args.get(minLocIndex)).locIteratorAdvance();
                                if (!((QryIop)this.args.get(minLocIndex)).locIteratorHasMatch()) {
                                    isEnd = true;
                                }
                                isMatched = false;
                                break;
                            } else {
                                // match.
                                isMatched = true;
                            }

                        } else {
                            isEnd = true;
                            isMatched = false;
                        }
                    }
                    
                    if (isMatched) {
//                        System.out.println("min loc: " + minLoc + " max loc: " + maxLoc);
                        positions.add(maxLoc);
//                        System.out.println();
                        for (Qry q_i : this.args) {
                            ((QryIop)q_i).locIteratorAdvance();
                            if (!((QryIop)q_i).locIteratorHasMatch()) {
                                isEnd = true;
                            }
                        }
                    }
//                    System.out.println("positions are: " + Arrays.toString(positions.toArray()));
                     
                }
                if (!positions.isEmpty()) {
                    this.invertedList.appendPosting(minDocId, positions);
                }
                
                for (Qry q_i : this.args) {
                    ((QryIop)q_i).docIteratorAdvancePast(minDocId);
                }
            }
        }
        
    }

}
