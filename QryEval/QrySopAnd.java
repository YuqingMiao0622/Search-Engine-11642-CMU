
/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
      if (r instanceof RetrievalModelIndri) {
          return this.docIteratorHasMatchMin(r);
      }
    return this.docIteratorHasMatchAll (r);
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
    }
    else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean(r);
    } else if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri(r);
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
          double minScore = Double.MAX_VALUE;
          // Because and operator requires all terms are matched, as long as the
          // docIterator has cached the matched document id, all the terms are matched.
          // There is no need to check whether the terms are matched or not(like the method
          // done in QrySopOr class).
          for (Qry q_i : this.args) {
              double tmpScore = ((QrySop)q_i).getScore(r);
              minScore = Math.min(tmpScore, minScore);
          }
          return minScore;
      }
  }
  
  private double getScoreIndri(RetrievalModel r) throws IOException {
      if (!this.docIteratorHasMatchCache()) {
          return 0.0;
      } else {
          int docId = this.docIteratorGetMatch();
          double score = 1.0;
          double numOfChild = this.args.size();
          
//          System.out.println("number of children: " + numOfChild + " docId: " + docId);
          for (Qry q_i : this.args) {
              if (!q_i.docIteratorHasMatchCache()) {
//                  System.out.println("get default score");
                  score = score * ((QrySop)q_i).getDefaultScore(r, docId);
              } else if (q_i.docIteratorGetMatch() == docId){
//                  System.out.println("get score");
                  score = score * ((QrySop)q_i).getScore(r);
              } else {
//                  System.out.println("get default score");
                  score = score * ((QrySop)q_i).getDefaultScore(r, docId);  
              }
          }
          score = Math.pow(score, 1 / numOfChild);
          return score;
      }
  }
  
  public double getDefaultScore(RetrievalModel r, long docId) throws IOException {
      if (r instanceof RetrievalModelIndri) {
          double score = 1.0;
          double numOfChild = this.args.size();
          for (Qry q_i : this.args) {
              score = score * ((QrySop)q_i).getDefaultScore(r, docId);
          }
          score = Math.pow(score, 1 / numOfChild);
          return score;
      } else {
          return 0.0;
      }
  }

}

