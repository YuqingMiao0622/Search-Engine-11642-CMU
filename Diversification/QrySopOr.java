/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
//    }
    }  else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean(r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
      
    if (! this.docIteratorHasMatchCache()) {
        return 0.0;
    } else {
        return 1.0;
    }
  }
  
  private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
      if (!this.docIteratorHasMatchCache()) {
          return 0.0;
      } else {
          // Because or operator just requires one of the terms is matched, when calculating
          // score, must check whether this term is matched or not. 
          
//          System.out.println("getScoreRankedBoolean method");
          double maxScore = 0.0;
          int docid = this.docIteratorGetMatch();
          for (Qry q_i : this.args) {
//              System.out.println(q_i.toString() + " docid: " + docid);
              double tmpScore = 0.0;
              if (q_i.docIteratorHasMatchCache()) {
//                  System.out.println("has match cache");
                  if (docid == q_i.docIteratorGetMatch()) {
//                      System.out.println("go to score operator");
                      tmpScore = ((QrySop)q_i).getScore(r);
                  }
                  maxScore = Math.max(maxScore, tmpScore);
              }
          }
          return maxScore;
      }
  }

@Override
public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    // TODO Auto-generated method stub
    if (r instanceof RetrievalModelIndri) {
        double score = 1.0;
        for (Qry q_i : this.args) {
            score = score * (1 - ((QrySop)q_i).getDefaultScore(r, docid));
        }
        score = 1 - score;
        return score;
    }
    return 0.0;
}
}
