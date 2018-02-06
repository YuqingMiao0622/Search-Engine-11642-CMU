/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
    } else if (r instanceof RetrievalModelBM25) {
        return this.getScoreBM25(r);
    } else if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri(r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
        return 1.0;
    }
  }
  
  public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
      if (!this.docIteratorHasMatchCache()) {
          return 0.0;
      } else {
          
          InvList.DocPosting postings = ((QryIop)this.args.get(0)).docIteratorGetMatchPosting();
          double score = postings.tf;
//          System.out.println(this.args.get(0).toString() + "  " + score);
          return score;
      }
  }

  public double getScoreBM25(RetrievalModel r) throws IOException {
      if (!this.docIteratorHasMatchCache()) {
          return 0.0;
      } else {
          // In order to calculate score, number of documents(N), document frequency(df), term frequency(tf),
          // document length(doclen), average document length(avg_doclen) and query term frequency(qtf = 1) are needed.
          
          // term frequency
          InvList.DocPosting postings = ((QryIop)this.args.get(0)).docIteratorGetMatchPosting();
          double tf = postings.tf;
          // document frequency
          double df = (double)((QryIop)this.args.get(0)).getDf();
          
          int docId = this.docIteratorGetMatch();
          String field = ((QryIop)this.args.get(0)).field;
          // document length
          double doclen = (double)Idx.getFieldLength(field, docId);
          // average document length
          double avg_doclen = (double)Idx.getSumOfFieldLengths(field) / (double)Idx.getDocCount(field);
          
          // number of documents
          double N = Idx.getNumDocs();
          
          double k1 = ((RetrievalModelBM25)r).k1;
//          double k3 = ((RetrievalModelBM25)r).k3;
          double b = ((RetrievalModelBM25)r).b;
          
          double RSJweight = Math.max(0, Math.log((N - df + 0.5) / (df + 0.5)));
          double tfWeight = tf / (tf + k1 * (1 - b + b * doclen / avg_doclen));
//          System.out.println(this.args.get(0) + " score is: " + RSJweight * tfWeight);
          return RSJweight * tfWeight;
      }
  }

  public double getScoreIndri(RetrievalModel r) throws IOException {

//      System.out.println("get score indri");
      if (!this.docIteratorHasMatchCache()) {
          return 0.0;
      } else {
          double ctf = ((QryIop)this.args.get(0)).invertedList.ctf;
          String field = ((QryIop)this.args.get(0)).field;
          double colleLength = (double)Idx.getSumOfFieldLengths(field);
          double mu = ((RetrievalModelIndri)r).mu;
          double lambda = ((RetrievalModelIndri)r).lambda;
          double pMLEc = ctf / colleLength;

          int docId = this.docIteratorGetMatch();
          double tf = (double)((QryIop)this.args.get(0)).docIteratorGetMatchPosting().tf;
          double docLen = Idx.getFieldLength(field, docId);
          double bayesSmooth = (tf + mu * pMLEc) / (docLen + mu);
          double score = (1 - lambda) * bayesSmooth + lambda * pMLEc;
          return score;
      }
  }
  
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
//      System.out.println("QrySopScore get default score");
      if (r instanceof RetrievalModelIndri) {
          double ctf = ((QryIop)this.args.get(0)).invertedList.ctf;
//          System.out.println("ctf: " + ctf);
          String field = ((QryIop)this.args.get(0)).field;
          double colleLength = (double)Idx.getSumOfFieldLengths(field);
//          System.out.println("collection length: " + colleLength);
          double docLen = (double)Idx.getFieldLength(field, (int)docid);
//          System.out.println("document length: " + docLen);
          int tf = 0;
          double mu = ((RetrievalModelIndri)r).mu;
//          System.out.println("mu: " + mu);
          double lambda = ((RetrievalModelIndri)r).lambda;
//          System.out.println("lambda: " + lambda);
          double pMLEc = ctf / colleLength;
//          System.out.println("pMLE collection: " + pMLEc);
          double bayesSmooth = (tf + mu * pMLEc) / (docLen + mu);
//          System.out.println("bayes smoothing: " + bayesSmooth);
          double score = (1 - lambda) * bayesSmooth + lambda * pMLEc;
//          System.out.println("get default score: " + score);
          return score;
      } else {
          return 0.0;
      }
  }
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
