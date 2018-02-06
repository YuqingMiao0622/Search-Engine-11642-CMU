import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class QryIopNear extends QryIop {
    
    protected int distance = 0;
    
    public QryIopNear(int distance) {
        this.distance = distance;
    }

    @Override
    protected void evaluate() throws IOException {
        // TODO Auto-generated method stub

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.
        
        this.invertedList = new InvList (this.getField());

        if (args.size () == 0) {
          return;
        }
        

        //  Each pass of the loop adds 1 document to result inverted list
        //  until all of the argument inverted lists are depleted.

        while (true) {

          //  Find the minimum next document id.  If there is none, we're done.

          int minDocid = Qry.INVALID_DOCID;

          for (Qry q_i: this.args) {
            if (q_i.docIteratorHasMatch (null)) {
              int q_iDocid = q_i.docIteratorGetMatch ();
              
              if ((minDocid > q_iDocid) ||
                  (minDocid == Qry.INVALID_DOCID)) {
                minDocid = q_iDocid;
              }
            }
          }
//          System.out.println("minDocid is: " + minDocid);

          if (minDocid == Qry.INVALID_DOCID)
            break;              // All docids have been processed.  Done.
          
          // check whether all terms are matched in the same document.
          boolean isSameDoc = true;
          int minQueryIndex = Integer.MIN_VALUE;
          for (int i = 0; i < this.args.size(); i++) {
              if (((QryIop)this.args.get(i)).docIteratorHasMatch(null)) {
//                  System.out.println("this args is: " + i +"  " + this.args.get(i) + " " + this.args.get(i).docIteratorGetMatch());
                  if (this.args.get(i).docIteratorGetMatch() != minDocid) {
                      isSameDoc = false;
                  } else {
                      minQueryIndex = i;
//                      System.out.println("in the for loop, minQueryIndex is: " + minQueryIndex);
                  }
              } else {
                  // there is no more document matched for current term.
                  isSameDoc = false;
                  break;
              }
          }
//          System.out.println("isSameDoc is: " + isSameDoc);
          if (minQueryIndex == Integer.MIN_VALUE) {
              break;
          }
          
          if (!isSameDoc) {
              this.args.get(minQueryIndex).docIteratorAdvancePast(minDocid);
//              if (((QryIop)this.args.get(minQueryIndex)).docIteratorHasMatch(null)) {
//                  System.out.println("different doc, the smallest one advanced to: " + this.args.get(minQueryIndex).docIteratorGetMatch());
//              } else {
//                  System.out.println("end of list");
//              }
              
          } else {
              //  Create a new posting that is the union of the posting lists
              //  that match the minDocid.  Save it.
              //  Note:  This implementation assumes that a location will not appear
              //  in two or more arguments.  #SYN (apple apple) would break it
              List<Integer> positions = new ArrayList<Integer>();
              boolean isEnd = false;
              while (!isEnd) {
//                  positions = new ArrayList<Integer>();
                  boolean isMatched = false;
                  int currentLoc;
                  if (((QryIop)this.args.get(0)).locIteratorHasMatch()) {
                      currentLoc = ((QryIop)this.args.get(0)).locIteratorGetMatch();
//                      System.out.println("args 0 is: " + ((QryIop)this.args.get(0)).toString() + "  position is: " + currentLoc);
                  } else {
                      break;
                  }
                  
                  for (int i = 1; i < this.args.size(); i++) {
                      QryIop query = (QryIop)this.args.get(i);
                      if (query.locIteratorHasMatch()) {
                          int position = query.locIteratorGetMatch();
//                          System.out.println(query.toString() + " current location is: " + currentLoc + "  position is: " + position);
                          // if position is smaller than current location, advance the position past current location.
                          // update the position. if there is no larger available position, there is no match, break the for loop.
                          if (currentLoc > position) {
                              query.locIteratorAdvancePast(currentLoc);
                              if (!query.locIteratorHasMatch()) {
                                  isEnd = true;
                                  isMatched = false;
                                  break;
                              } else {
                                  // position must be greater than currentLoc, there is no need to check in the following step.
                                  position = query.locIteratorGetMatch();
                              }
//                              System.out.println("position is smaller. is end? " + isEnd);
                          }
                          if (position - currentLoc > distance) {
                              // do not match. advance the first term location iterator.
                              ((QryIop)this.args.get(0)).locIteratorAdvance();
                              if (!((QryIop)this.args.get(0)).locIteratorHasMatch()) {
                                  isEnd = true;
                              }
                              isMatched = false;
                              break;
                          } else {
                              // match.
                              currentLoc = position;
                              isMatched = true;
                          }
                          
//                          if (!query.locIteratorHasMatch() || !((QryIop)this.args.get(0)).locIteratorHasMatch()) {
//                              isEnd = true;
//                          }
                      } else {
                          isEnd = true;
                          isMatched = false;
                      }
                  }
//                  System.out.println("out of the for loop. is matched? " + isMatched + "  is end? " + isEnd);
                  if (isMatched) {
                      positions.add(currentLoc);
                      for (Qry q_i : this.args) {
                          ((QryIop)q_i).locIteratorAdvance();
                          if (!((QryIop)q_i).locIteratorHasMatch()) {
                              isEnd = true;
                          }
                      }
                  }
//                  System.out.println("positions are: " + Arrays.toString(positions.toArray()));
                   
              }
              if (!positions.isEmpty()) {
                  this.invertedList.appendPosting(minDocid, positions);
              }
              
              for (Qry q_i : this.args) {
                  ((QryIop)q_i).docIteratorAdvancePast(minDocid);
              }
                  
              
          }
        }
    }
}
